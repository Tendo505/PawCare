from __future__ import annotations

import io
from contextlib import asynccontextmanager

from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from PIL import Image, UnidentifiedImageError
from pydantic import BaseModel, Field

from .classifier import BreedClassifier, ModelNotReadyError


MAX_IMAGE_BYTES = 10 * 1024 * 1024
classifier = BreedClassifier()


class PredictionItem(BaseModel):
    breed: str
    confidence: float = Field(ge=0, le=1)


class PredictionResponse(BaseModel):
    species: str
    breed: str
    confidence: float = Field(ge=0, le=1)
    top_predictions: list[PredictionItem]
    model_ready: bool = True
    model_version: str
    disclaimer: str = "Breed recognition is an estimate and is not veterinary advice."


@asynccontextmanager
async def lifespan(_: FastAPI):
    # Report readiness at startup without loading TensorFlow until the first prediction.
    yield


app = FastAPI(
    title="PawCare Breed Recognition API",
    version="1.0.0",
    description="Image classification for common cat and dog breeds.",
    lifespan=lifespan,
)
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["GET", "POST"], allow_headers=["*"])


@app.get("/")
def root() -> dict:
    return {"name": "PawCare Breed Recognition API", "docs": "/docs", **classifier.status()}


@app.get("/health")
def health() -> dict:
    return {"status": "ready" if classifier.ready else "model_missing", **classifier.status()}


@app.post("/predict", response_model=PredictionResponse)
async def predict(image: UploadFile = File(...)) -> PredictionResponse:
    if image.content_type not in {"image/jpeg", "image/png", "image/webp"}:
        raise HTTPException(status_code=415, detail="Upload a JPEG, PNG, or WebP image.")
    payload = await image.read(MAX_IMAGE_BYTES + 1)
    if len(payload) > MAX_IMAGE_BYTES:
        raise HTTPException(status_code=413, detail="Image is larger than 10 MB.")
    try:
        pil_image = Image.open(io.BytesIO(payload))
        pil_image.verify()
        pil_image = Image.open(io.BytesIO(payload))
    except (UnidentifiedImageError, OSError) as error:
        raise HTTPException(status_code=422, detail="The uploaded file is not a valid image.") from error

    try:
        species, ranked = classifier.predict(pil_image)
    except ModelNotReadyError as error:
        raise HTTPException(status_code=503, detail=str(error)) from error
    except Exception as error:
        raise HTTPException(status_code=500, detail="Model inference failed.") from error

    best = ranked[0]
    return PredictionResponse(
        species=species,
        breed=best.breed,
        confidence=best.confidence,
        top_predictions=[PredictionItem(breed=item.breed, confidence=item.confidence) for item in ranked],
        model_version=classifier.status()["model_version"],
    )
