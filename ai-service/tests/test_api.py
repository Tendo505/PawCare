import asyncio
import io
import sys
from types import SimpleNamespace
import numpy as np
import pytest

from fastapi.testclient import TestClient
from PIL import Image

from app.classifier import NotPetImageError, RankedPrediction
from app.main import app, classifier


client = TestClient(app)


def test_prediction_ranking_preserves_order_and_limit():
    #check score processing without loading tensorflow
    ranked = classifier._rank_predictions(
        np.array([0.2, 0.7, 0.1]), ["first", "second", "third"], 2
    )
    assert [item.breed for item in ranked] == ["second", "first"]
    assert [item.confidence for item in ranked] == [0.7, 0.2]


def test_prediction_ranking_rejects_invalid_confidence():
    #keep the existing failure for non-finite scores
    with pytest.raises(RuntimeError, match="invalid prediction"):
        classifier._rank_predictions(np.array([float("nan")]), ["first"], 1)


@pytest.mark.parametrize("scores", [[1.2, -0.2], [0.8, float("-inf")], [0.8, -0.1]])
def test_prediction_ranking_rejects_invalid_scores_outside_the_top_results(scores):
    with pytest.raises(RuntimeError, match="invalid prediction"):
        classifier._rank_predictions(np.array(scores), ["first", "second"], 1)


def test_health_reports_model_state():
    # Check model availability without exposing server file paths.
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json()["status"] in {"ready", "model_missing"}
    assert "model_path" not in response.json()
    assert response.json()["label_count"] >= 0


def test_rejects_non_image_upload():
    # Reject files whose upload type is not an accepted image type.
    response = client.post(
        "/predict",
        files={"image": ("pet.txt", b"not-an-image", "text/plain")},
    )

    assert response.status_code == 415


def test_rejects_invalid_bytes_disguised_as_an_image():
    # Reject invalid image bytes even when the upload claims to be a PNG.
    response = client.post(
        "/predict",
        files={"image": ("pet.png", b"not-an-image", "image/png")},
    )

    assert response.status_code == 422


def test_rejects_an_image_that_is_too_small():
    # Reject images below the minimum width or height.
    response = client.post("/predict", files={"image": ("pet.png", _png(32, 32), "image/png")})

    assert response.status_code == 422
    assert "64 x 64" in response.json()["detail"]


@pytest.mark.parametrize("pixel_limit", [10000, 100])
def test_rejects_excessive_decoded_image_size(monkeypatch, pixel_limit):
    # Simulate Pillow's warning and hard-error limits using a small safe image.
    payload = _png()
    monkeypatch.setattr(Image, "MAX_IMAGE_PIXELS", pixel_limit)
    def unexpected_inference(_):
        raise AssertionError("Oversized images must be rejected before inference.")
    monkeypatch.setattr(classifier, "predict", unexpected_inference)
    response = TestClient(app, raise_server_exceptions=False).post(
        "/predict", files={"image": ("large.png", payload, "image/png")}
    )
    assert response.status_code == 413
    assert "dimensions" in response.json()["detail"]


def test_missing_model_is_explicit(tmp_path):
    # Check the missing-model response and restore the original file locations.
    original_model, original_labels = classifier.model_path, classifier.labels_path
    classifier.model_path, classifier.labels_path = tmp_path / "missing.keras", tmp_path / "missing.json"

    try:
        response = client.post("/predict", files={"image": ("pet.png", _png(), "image/png")})
        assert response.status_code == 503
        assert "trained model" in response.json()["detail"]
    finally:
        classifier.model_path, classifier.labels_path = original_model, original_labels


def test_returns_a_stable_prediction_contract(monkeypatch):
    # Use fixed predictions to verify the response fields without TensorFlow.
    monkeypatch.setattr(
        classifier,
        "predict",
        lambda _: (
            "cat",
            [
                RankedPrediction("British_Shorthair", 0.81),
                RankedPrediction("Russian_Blue", 0.12),
                RankedPrediction("Bombay", 0.07),
            ],
        ),
    )
    response = client.post("/predict", files={"image": ("toby.png", _png(), "image/png")})

    assert response.status_code == 200
    assert response.json()["breed"] == "British_Shorthair"
    assert response.json()["species"] == "cat"
    assert len(response.json()["top_predictions"]) == 3
    assert response.json()["model_ready"] is True


def test_prediction_does_not_block_the_server_event_loop(monkeypatch):
    #keep model loading and inference off the request event loop
    prediction_threads = []

    def predict_without_blocking(_):
        try:
            asyncio.get_running_loop()
            prediction_threads.append("event_loop")
        except RuntimeError:
            prediction_threads.append("worker")
        return "cat", [RankedPrediction("British_Shorthair", 0.81)]

    monkeypatch.setattr(classifier, "predict", predict_without_blocking)
    response = client.post("/predict", files={"image": ("pet.png", _png(), "image/png")})

    assert response.status_code == 200
    assert prediction_threads == ["worker"]


def test_rejects_a_photo_without_a_cat_or_dog(monkeypatch):
    # Check that non-pet detection becomes a clear validation response.
    def reject_non_pet(_):
        # Simulate the classifier rejecting a photo with no cat or dog.
        raise NotPetImageError(
            "No cat or dog was detected. Choose a clear photo containing one cat or dog."
        )

    monkeypatch.setattr(classifier, "predict", reject_non_pet)
    response = client.post("/predict", files={"image": ("person.png", _png(), "image/png")})

    assert response.status_code == 422
    assert "No cat or dog was detected" in response.json()["detail"]


@pytest.mark.parametrize("invalid_value", [float("nan"), float("inf"), -0.1, 1.1])
def test_pet_gate_rejects_invalid_model_scores(monkeypatch, invalid_value):
    #invalid detector output must not silently pass the cat-or-dog gate
    scores = np.zeros(1000)
    scores[151] = invalid_value
    _stub_pet_gate(monkeypatch, scores)

    with pytest.raises(RuntimeError, match="invalid prediction"):
        classifier._require_pet_image(Image.new("RGB", (224, 224)))


@pytest.mark.parametrize("best_index, is_pet", [(0, False), (151, True), (282, True)])
def test_pet_gate_distinguishes_pet_and_non_pet_scores(monkeypatch, best_index, is_pet):
    #exercise the actual gate decision, not a mocked rejection exception
    scores = np.zeros(1000)
    scores[best_index] = 1.0
    _stub_pet_gate(monkeypatch, scores)

    if is_pet:
        classifier._require_pet_image(Image.new("RGB", (224, 224)))
    else:
        with pytest.raises(NotPetImageError, match="No cat or dog"):
            classifier._require_pet_image(Image.new("RGB", (224, 224)))


def test_pet_gate_checks_the_centre_when_scenery_outscores_the_pet(monkeypatch):
    #a small pet in a wide landscape passes when the centre crop shows a cat
    scenery_scores = np.zeros(1000)
    scenery_scores[0] = 1.0
    cat_scores = np.zeros(1000)
    cat_scores[285] = 1.0
    seen_views = []

    def score_view(batch, verbose):
        seen_views.append(batch.shape)
        return np.array([scenery_scores if len(seen_views) == 1 else cat_scores])

    _stub_pet_gate(monkeypatch, scenery_scores)
    monkeypatch.setattr(classifier, "_load_pet_gate_model", lambda: SimpleNamespace(predict=score_view))

    classifier._require_pet_image(Image.new("RGB", (800, 400)))

    assert seen_views == [(1, 224, 224, 3), (1, 224, 224, 3)]


def _stub_pet_gate(monkeypatch, scores):
    #test detector scores without loading tensorflow or downloading weights
    monkeypatch.setitem(
        sys.modules,
        "tensorflow.keras.applications.mobilenet_v2",
        SimpleNamespace(preprocess_input=lambda batch: batch),
    )
    model = SimpleNamespace(predict=lambda batch, verbose: np.array([scores]))
    monkeypatch.setattr(classifier, "_load_pet_gate_model", lambda: model)


def _png(width: int = 128, height: int = 128) -> bytes:
    # Create image bytes in memory for upload tests.
    output = io.BytesIO()
    image = Image.new("RGB", (width, height), color=(120, 100, 80))
    image.save(output, format="PNG")
    return output.getvalue()
