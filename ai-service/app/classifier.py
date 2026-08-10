from __future__ import annotations

import json
import os
from dataclasses import dataclass
from pathlib import Path
from threading import Lock

import numpy as np
from PIL import Image


CAT_BREEDS = {
    "abyssinian", "bengal", "birman", "bombay", "british_shorthair",
    "egyptian_mau", "maine_coon", "persian", "ragdoll", "russian_blue",
    "siamese", "sphynx",
}


@dataclass(frozen=True)
class RankedPrediction:
    breed: str
    confidence: float


class ModelNotReadyError(RuntimeError):
    pass


class BreedClassifier:
    """Lazy TensorFlow image classifier with thread-safe model loading."""

    def __init__(self, model_path: str | None = None, labels_path: str | None = None):
        service_root = Path(__file__).resolve().parents[1]
        self.model_path = Path(model_path or os.getenv("MODEL_PATH", service_root / "models" / "pawcare_breed_classifier.keras"))
        self.labels_path = Path(labels_path or os.getenv("LABELS_PATH", service_root / "models" / "labels.json"))
        self._model = None
        self._labels: list[str] = []
        self._lock = Lock()

    @property
    def ready(self) -> bool:
        return self.model_path.exists() and self.labels_path.exists()

    def status(self) -> dict:
        return {
            "model_ready": self.ready,
            "model_path": str(self.model_path),
            "label_count": len(self._load_labels()) if self.labels_path.exists() else 0,
            "model_version": os.getenv("MODEL_VERSION", "oxford-pets-mobilenetv2-v1"),
        }

    def predict(self, image: Image.Image, top_k: int = 3) -> tuple[str, list[RankedPrediction]]:
        model = self._load_model()
        labels = self._load_labels()
        if not labels:
            raise ModelNotReadyError("The label file is empty. Run train.py to regenerate model metadata.")

        rgb = image.convert("RGB").resize((224, 224))
        batch = np.expand_dims(np.asarray(rgb, dtype=np.float32), axis=0)
        # preprocess_input scales pixels to the range expected by MobileNetV2.
        from tensorflow.keras.applications.mobilenet_v2 import preprocess_input

        probabilities = model.predict(preprocess_input(batch), verbose=0)[0]
        indexes = np.argsort(probabilities)[::-1][: min(top_k, len(labels))]
        ranked = [RankedPrediction(labels[int(index)], float(probabilities[int(index)])) for index in indexes]
        species = "cat" if ranked[0].breed.lower().replace(" ", "_") in CAT_BREEDS else "dog"
        return species, ranked

    def _load_model(self):
        if self._model is not None:
            return self._model
        if not self.ready:
            raise ModelNotReadyError(
                "No trained model was found. Run `python train.py --epochs 8` in ai-service, "
                "or mount a compatible .keras model at MODEL_PATH."
            )
        with self._lock:
            if self._model is None:
                from tensorflow import keras

                self._model = keras.models.load_model(self.model_path)
        return self._model

    def _load_labels(self) -> list[str]:
        if self._labels:
            return self._labels
        if self.labels_path.exists():
            with self.labels_path.open("r", encoding="utf-8") as file:
                payload = json.load(file)
            self._labels = payload["labels"] if isinstance(payload, dict) else payload
        return self._labels
