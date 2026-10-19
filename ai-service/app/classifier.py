from __future__ import annotations

import json
import os
from dataclasses import dataclass
from math import isfinite
from pathlib import Path
from threading import Lock

import numpy as np
from PIL import Image

#the breed recogniser, using two models:
#1. a general model checks the photo shows a cat or dog
#2. our trained model picks one of 37 breeds


#1.setup

#the 12 cat breeds; all other breeds are dogs
CAT_BREEDS = {
    "abyssinian", "bengal", "birman", "bombay", "british_shorthair",
    "egyptian_mau", "maine_coon", "persian", "ragdoll", "russian_blue",
    "siamese", "sphynx",
}

#in the general model, classes 151-268 are dogs and 281-285 are cats
IMAGENET_DOG_RANGE = range(151, 269)
IMAGENET_CAT_RANGE = range(281, 286)

#a photo passes if it is at least 20% cat/dog
DEFAULT_MIN_PET_PROBABILITY = 0.20

#the middle of the photo, checked when the whole photo fails
CENTRE_CROP = (0.25, 0.10, 0.75, 0.90)


#one breed guess, e.g. ("samoyed", 0.99)
@dataclass(frozen=True)
class RankedPrediction:
    breed: str
    confidence: float


#model missing or broken (error 503)
class ModelNotReadyError(RuntimeError):
    pass


#no cat or dog in the photo (error 422)
class NotPetImageError(ValueError):
    pass


class BreedClassifier:
    #where the model files are
    def __init__(self, model_path: str | None = None, labels_path: str | None = None):
        service_root = Path(__file__).resolve().parents[1]

        #default: models/pawcare_breed_classifier.keras and models/labels.json
        if model_path is None:
            model_path = os.getenv("MODEL_PATH", service_root / "models" / "pawcare_breed_classifier.keras")
        if labels_path is None:
            labels_path = os.getenv("LABELS_PATH", service_root / "models" / "labels.json")
        self.model_path = Path(model_path)
        self.labels_path = Path(labels_path)

        #empty until the first photo arrives
        self._model = None
        self._pet_gate_model = None
        self._labels: list[str] = []
        self._load_error: str | None = None

        #one photo at a time
        self._lock = Lock()
        self._pet_gate_lock = Lock()
        self._prediction_lock = Lock()

    #2.input

    #load the cat/dog model once
    def _load_pet_gate_model(self):
        if self._pet_gate_model is not None:
            return self._pet_gate_model

        with self._pet_gate_lock:
            if self._pet_gate_model is None:
                from tensorflow.keras.applications import MobileNetV2

                self._pet_gate_model = MobileNetV2(weights="imagenet")
        return self._pet_gate_model

    #load our breed model once
    def _load_model(self):
        if self._model is not None:
            return self._model
        if self._load_error is not None:
            raise ModelNotReadyError(self._load_error)
        if not self.files_present:
            raise ModelNotReadyError(
                "No trained model was found. Run `python train.py --epochs 8` in ai-service, "
                "or mount a compatible .keras model at MODEL_PATH."
            )

        with self._lock:
            if self._model is None:
                #a broken file is reported, the service keeps running
                try:
                    from tensorflow import keras

                    self._model = keras.models.load_model(self.model_path)
                except Exception as error:
                    self._load_error = (
                        "The model file could not be loaded. Replace models/pawcare_breed_classifier.keras "
                        "with a compatible trained model and restart the service."
                    )
                    raise ModelNotReadyError(self._load_error) from error
        return self._model

    #read the 37 breed names from labels.json once
    def _load_labels(self) -> list[str]:
        if self._labels:
            return self._labels
        if not self.labels_path.exists():
            return self._labels

        with self.labels_path.open("r", encoding="utf-8") as file:
            payload = json.load(file)

        labels = payload
        if isinstance(payload, dict):
            labels = payload["labels"]

        #names must not be empty or repeated
        if not isinstance(labels, list) or not labels:
            raise ValueError("labels.json must contain a non-empty list of breed names.")
        for label in labels:
            if not isinstance(label, str) or not label.strip():
                raise ValueError("labels.json must contain a non-empty list of breed names.")
        if len(set(labels)) != len(labels):
            raise ValueError("labels.json contains duplicate breed names.")

        self._labels = labels
        return self._labels

    #3.process

    #main job: check for a pet, then return the species and top 3 breeds
    def predict(self, image: Image.Image, top_k: int = 3) -> tuple[str, list[RankedPrediction]]:
        #load the model and names
        model = self._load_model()
        labels = self._load_labels()
        if not labels:
            raise ModelNotReadyError("The label file is empty. Run train.py to regenerate model metadata.")

        #stop if there is no cat or dog
        rgb_image = image.convert("RGB")
        self._require_pet_image(rgb_image)

        #resize to 224 x 224
        prepared_image = rgb_image.resize((224, 224))
        batch = np.expand_dims(np.asarray(prepared_image, dtype=np.float32), axis=0)

        #prepare the pixels like in training
        from tensorflow.keras.applications.mobilenet_v2 import preprocess_input

        #one score per breed
        with self._prediction_lock:
            probabilities = model.predict(preprocess_input(batch), verbose=0)[0]
        if len(probabilities) != len(labels):
            raise ModelNotReadyError("The model output does not match labels.json. Retrain or replace both files together.")

        ranked = self._rank_predictions(probabilities, labels, top_k)

        #cat breed = cat, otherwise dog
        best_breed = ranked[0].breed.lower().replace(" ", "_")
        species = "dog"
        if best_breed in CAT_BREEDS:
            species = "cat"
        return species, ranked

    #keep the 3 highest scores
    def _rank_predictions(self, probabilities, labels: list[str], top_k: int) -> list[RankedPrediction]:
        #reject broken scores
        probabilities = np.asarray(probabilities)
        if (
            probabilities.ndim != 1
            or len(probabilities) != len(labels)
            or not np.all(np.isfinite(probabilities))
            or np.any(probabilities < 0)
            or np.any(probabilities > 1)
        ):
            raise RuntimeError("The model returned an invalid prediction.")

        #sorted lowest to highest, so take from the end
        sorted_positions = np.argsort(probabilities)
        how_many = min(top_k, len(labels))
        ranked = []
        for step in range(how_many):
            position = int(sorted_positions[len(sorted_positions) - 1 - step])
            ranked.append(RankedPrediction(labels[position], float(probabilities[position])))

        if not ranked:
            raise RuntimeError("The model returned an invalid prediction.")
        for item in ranked:
            if not isfinite(item.confidence):
                raise RuntimeError("The model returned an invalid prediction.")
        return ranked

    #reject the photo if neither the whole photo nor its middle shows a cat or dog
    def _require_pet_image(self, image: Image.Image) -> None:
        if self._shows_pet(image):
            return

        #try the middle of the photo
        width, height = image.size
        left, top, right, bottom = CENTRE_CROP
        centre = image.crop((int(width * left), int(height * top), int(width * right), int(height * bottom)))
        if self._shows_pet(centre):
            return

        raise NotPetImageError(
            "No cat or dog was detected. Choose a clear photo containing one cat or dog."
        )

    #true if the general model sees a cat or dog
    def _shows_pet(self, image: Image.Image) -> bool:
        from tensorflow.keras.applications.mobilenet_v2 import preprocess_input

        prepared_image = image.convert("RGB").resize((224, 224))
        batch = np.expand_dims(np.asarray(prepared_image, dtype=np.float32), axis=0)
        with self._prediction_lock:
            probabilities = self._load_pet_gate_model().predict(preprocess_input(batch), verbose=0)[0]

        #reject broken scores
        probabilities = np.asarray(probabilities)
        if (
            probabilities.shape != (1000,)
            or not np.all(np.isfinite(probabilities))
            or np.any(probabilities < 0)
            or np.any(probabilities > 1)
        ):
            raise RuntimeError("The pet detector returned an invalid prediction.")

        #total cat/dog score
        pet_indexes = list(IMAGENET_DOG_RANGE) + list(IMAGENET_CAT_RANGE)
        pet_probability = float(np.sum(probabilities[pet_indexes]))

        #is the top class a cat or dog?
        best_index = int(np.argmax(probabilities))
        best_is_pet = best_index in IMAGENET_DOG_RANGE or best_index in IMAGENET_CAT_RANGE

        minimum_probability = float(os.getenv("MIN_PET_PROBABILITY", DEFAULT_MIN_PET_PROBABILITY))
        return best_is_pet or pet_probability >= minimum_probability

    #4.output

    #both model files exist
    @property
    def files_present(self) -> bool:
        return self.model_path.exists() and self.labels_path.exists()

    #the model file is broken
    @property
    def load_failed(self) -> bool:
        return self._load_error is not None

    #ready to predict
    @property
    def ready(self) -> bool:
        return self.files_present and not self.load_failed

    #model info for /health
    def status(self) -> dict:
        label_count = 0
        try:
            if self.labels_path.exists():
                label_count = len(self._load_labels())
        except (KeyError, TypeError, ValueError, json.JSONDecodeError):
            label_count = 0

        return {
            "model_ready": self.ready,
            "model_loaded": self._model is not None,
            "label_count": label_count,
            "model_version": os.getenv("MODEL_VERSION", "oxford-pets-mobilenetv2-v1"),
        }
