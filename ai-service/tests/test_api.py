from fastapi.testclient import TestClient

from app.main import app, classifier


client = TestClient(app)


def test_health_reports_model_state():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] in {"ready", "model_missing"}


def test_rejects_non_image_upload():
    response = client.post("/predict", files={"image": ("pet.txt", b"not-an-image", "text/plain")})
    assert response.status_code == 415


def test_missing_model_is_explicit(tmp_path):
    original_model, original_labels = classifier.model_path, classifier.labels_path
    classifier.model_path, classifier.labels_path = tmp_path / "missing.keras", tmp_path / "missing.json"
    try:
        response = client.post("/predict", files={"image": ("pet.png", _one_pixel_png(), "image/png")})
        assert response.status_code == 503
        assert "trained model" in response.json()["detail"]
    finally:
        classifier.model_path, classifier.labels_path = original_model, original_labels


def _one_pixel_png() -> bytes:
    import base64
    return base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=")
