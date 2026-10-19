from __future__ import annotations

import io
import os
import warnings
from contextlib import asynccontextmanager

from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.concurrency import run_in_threadpool
from PIL import Image, ImageOps, UnidentifiedImageError
from pydantic import BaseModel, Field

from .classifier import BreedClassifier, ModelNotReadyError, NotPetImageError

#the AI service: Laravel sends it a photo, it sends back the breed
#GET /health = is the model ready?   POST /predict = photo in, breed out


#1.setup

MAX_IMAGE_BYTES = 10 * 1024 * 1024          #10 MB
MIN_IMAGE_SIDE = 64                         #at least 64 x 64 pixels
SUPPORTED_CONTENT_TYPES = {"image/jpeg", "image/png", "image/webp"}
SUPPORTED_IMAGE_FORMATS = {"JPEG", "PNG", "WEBP"}
DEFAULT_CONFIDENCE_THRESHOLD = 0.60         #below 60% = not sure
LOW_CONFIDENCE_MESSAGE = "Not sure, try a clearer photo"

#the breed recogniser (see classifier.py)
classifier = BreedClassifier()


#one breed guess, e.g. {"breed": "samoyed", "confidence": 0.99}
class PredictionItem(BaseModel):
    breed: str
    confidence: float = Field(ge=0, le=1)       #between 0 and 1


#the answer sent back by /predict
class PredictionResponse(BaseModel):
    species: str
    breed: str
    confidence: float = Field(ge=0, le=1)
    top_predictions: list[PredictionItem]
    model_ready: bool = True
    model_version: str
    disclaimer: str = "Breed recognition is an estimate and is not veterinary advice."
    low_confidence: bool = False
    message: str | None = None


#runs when the service starts; nothing to load yet
@asynccontextmanager
async def lifespan(_: FastAPI):
    yield


app = FastAPI(
    title="PawCare Breed Recognition API",
    version="1.0.0",
    description="Image classification for common cat and dog breeds.",
    lifespan=lifespan,
)


#2.input

#check the file type and size
async def _read_image_payload(image: UploadFile) -> bytes:
    if image.content_type not in SUPPORTED_CONTENT_TYPES:
        raise HTTPException(status_code=415, detail="Upload a JPEG, PNG, or WebP image.")
    try:
        payload = await image.read(MAX_IMAGE_BYTES + 1)
    finally:
        await image.close()
    if not payload:
        raise HTTPException(status_code=422, detail="The uploaded image is empty.")
    if len(payload) > MAX_IMAGE_BYTES:
        raise HTTPException(status_code=413, detail="Image is larger than 10 MB.")
    return payload


#open the file as a picture and check it is a real photo
def _decode_image(payload: bytes) -> Image.Image:
    try:
        #stop pictures that are far too big
        with warnings.catch_warnings():
            warnings.simplefilter("error", Image.DecompressionBombWarning)

            #read the real file type
            with Image.open(io.BytesIO(payload)) as candidate:
                detected_format = ""
                if candidate.format is not None:
                    detected_format = candidate.format.upper()
                candidate.verify()
        if detected_format not in SUPPORTED_IMAGE_FORMATS:
            raise HTTPException(status_code=415, detail="Upload a JPEG, PNG, or WebP image.")

        #load the photo and turn it the right way up
        with Image.open(io.BytesIO(payload)) as candidate:
            candidate.load()
            prepared_image = ImageOps.exif_transpose(candidate).convert("RGB")
    except (Image.DecompressionBombError, Image.DecompressionBombWarning) as error:
        raise HTTPException(status_code=413, detail="The image dimensions are too large.") from error
    except (UnidentifiedImageError, OSError) as error:
        raise HTTPException(status_code=422, detail="The uploaded file is not a valid image.") from error

    width, height = prepared_image.size
    if width < MIN_IMAGE_SIDE or height < MIN_IMAGE_SIDE:
        raise HTTPException(status_code=422, detail="The image must be at least 64 x 64 pixels.")
    return prepared_image


#3.process

#the "not sure" limit (0.60 unless set in the settings)
def _read_confidence_threshold() -> float:
    try:
        threshold = float(os.getenv("CONFIDENCE_THRESHOLD", DEFAULT_CONFIDENCE_THRESHOLD))
    except ValueError:
        return DEFAULT_CONFIDENCE_THRESHOLD
    if threshold < 0 or threshold > 1:
        return DEFAULT_CONFIDENCE_THRESHOLD
    return threshold


#ready, model_missing or model_load_failed
def _read_model_status() -> str:
    if classifier.ready:
        return "ready"
    if classifier.load_failed:
        return "model_load_failed"
    return "model_missing"


#4.output

#GET / : service name and model info
@app.get("/")
def root() -> dict:
    answer = {"name": "PawCare Breed Recognition API", "docs": "/docs"}
    answer.update(classifier.status())
    return answer


#GET /health : is the model ready?
@app.get("/health")
def health() -> dict:
    answer = {"status": _read_model_status()}
    answer.update(classifier.status())
    return answer


#POST /predict : photo in, top 3 breeds out
@app.post("/predict", response_model=PredictionResponse, response_model_exclude_none=True)
async def predict(image: UploadFile = File(...)) -> PredictionResponse:
    #2.input: read and open the photo
    payload = await _read_image_payload(image)
    prepared_image = await run_in_threadpool(_decode_image, payload)

    #3.process: ask the model
    try:
        species, ranked = await run_in_threadpool(classifier.predict, prepared_image)
    except NotPetImageError as error:
        raise HTTPException(status_code=422, detail=str(error)) from error      #not a cat or dog
    except ModelNotReadyError as error:
        raise HTTPException(status_code=503, detail=str(error)) from error      #model not ready
    except Exception as error:
        raise HTTPException(status_code=500, detail="Model inference failed.") from error

    #4.output: best guess first, marked "not sure" when below 60%
    best_prediction = ranked[0]
    low_confidence = best_prediction.confidence < _read_confidence_threshold()
    message = None
    if low_confidence:
        message = LOW_CONFIDENCE_MESSAGE

    top_predictions = []
    for item in ranked:
        top_predictions.append(PredictionItem(breed=item.breed, confidence=item.confidence))

    return PredictionResponse(
        species=species,
        breed=best_prediction.breed,
        confidence=best_prediction.confidence,
        top_predictions=top_predictions,
        model_version=classifier.status()["model_version"],
        low_confidence=low_confidence,
        message=message,
    )
