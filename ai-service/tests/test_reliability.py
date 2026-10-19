import io
import sys
from types import SimpleNamespace

import pytest
from fastapi.testclient import TestClient
from PIL import Image

from app.classifier import BreedClassifier, ModelNotReadyError, RankedPrediction
from app.main import app, classifier


client = TestClient(app)


def test_health_reports_missing_model_without_crashing(tmp_path, monkeypatch):
    #start and answer health checks even when no model has been placed
    monkeypatch.setattr(classifier, "model_path", tmp_path / "missing.keras")
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json()["status"] == "model_missing"
    assert response.json()["model_ready"] is False


def test_damaged_model_reports_load_failure(tmp_path, monkeypatch):
    #a model file that cannot be opened must give a clear error, not a crash
    model_path = tmp_path / "broken.keras"
    model_path.write_bytes(b"not-a-model")
    labels_path = tmp_path / "labels.json"
    labels_path.write_text('{"labels": ["Siamese"]}', encoding="utf-8")
    broken = BreedClassifier(model_path=str(model_path), labels_path=str(labels_path))
    _stub_keras_loader(monkeypatch, error=ValueError("bad file"))

    with pytest.raises(ModelNotReadyError, match="could not be loaded"):
        broken._load_model()
    assert broken.ready is False
    assert broken.load_failed is True

    monkeypatch.setattr("app.main.classifier", broken)
    assert client.get("/health").json()["status"] == "model_load_failed"


def test_high_confidence_prediction_is_not_flagged(monkeypatch):
    #confident results keep the existing fields and add low_confidence false
    _stub_prediction(monkeypatch, 0.81)
    response = client.post("/predict", files={"image": ("pet.png", _png(), "image/png")})

    assert response.status_code == 200
    assert response.json()["low_confidence"] is False
    assert "message" not in response.json()
    assert len(response.json()["top_predictions"]) == 3


def test_low_confidence_prediction_is_flagged(monkeypatch):
    #results below the default 0.60 threshold keep all fields and add a message
    monkeypatch.delenv("CONFIDENCE_THRESHOLD", raising=False)
    _stub_prediction(monkeypatch, 0.42)
    response = client.post("/predict", files={"image": ("pet.png", _png(), "image/png")})

    assert response.status_code == 200
    assert response.json()["breed"] == "British_Shorthair"
    assert response.json()["confidence"] == 0.42
    assert response.json()["low_confidence"] is True
    assert response.json()["message"] == "Not sure, try a clearer photo"
    assert len(response.json()["top_predictions"]) == 3


@pytest.mark.parametrize("threshold, expected", [("0.30", False), ("0.90", True), ("invalid", True), ("5", True)])
def test_confidence_threshold_reads_the_environment(monkeypatch, threshold, expected):
    #invalid or out-of-range thresholds fall back to the 0.60 default
    monkeypatch.setenv("CONFIDENCE_THRESHOLD", threshold)
    _stub_prediction(monkeypatch, 0.42 if threshold in {"invalid", "5"} else 0.5)
    response = client.post("/predict", files={"image": ("pet.png", _png(), "image/png")})

    assert response.json()["low_confidence"] is expected


def test_rejects_an_empty_upload():
    #an empty file is a clear validation error
    response = client.post("/predict", files={"image": ("pet.png", b"", "image/png")})

    assert response.status_code == 422
    assert "empty" in response.json()["detail"]


def test_rejects_an_oversized_upload(monkeypatch):
    #uploads above the size limit are refused before decoding
    monkeypatch.setattr("app.main.MAX_IMAGE_BYTES", 100)
    response = client.post("/predict", files={"image": ("pet.png", _png(), "image/png")})

    assert response.status_code == 413
    assert "10 MB" in response.json()["detail"]


def test_rejects_a_truncated_image():
    #a damaged image body is handled as a validation error, not a server error
    response = client.post("/predict", files={"image": ("pet.png", _png()[:60], "image/png")})

    assert response.status_code == 422


def _stub_prediction(monkeypatch, confidence):
    #return fixed ranked scores without loading tensorflow
    remaining = round(1 - confidence, 4)
    monkeypatch.setattr(
        classifier,
        "predict",
        lambda _: (
            "cat",
            [
                RankedPrediction("British_Shorthair", confidence),
                RankedPrediction("Russian_Blue", round(remaining * 0.6, 4)),
                RankedPrediction("Bombay", round(remaining * 0.4, 4)),
            ],
        ),
    )


def _stub_keras_loader(monkeypatch, error):
    #simulate keras failing to open a model file
    def load_model(_):
        raise error

    keras = SimpleNamespace(models=SimpleNamespace(load_model=load_model))
    monkeypatch.setitem(sys.modules, "tensorflow", SimpleNamespace(keras=keras))


def _png(width: int = 128, height: int = 128) -> bytes:
    # Create image bytes in memory for upload tests.
    output = io.BytesIO()
    Image.new("RGB", (width, height), color=(120, 100, 80)).save(output, format="PNG")
    return output.getvalue()
