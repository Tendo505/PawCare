# Model files

The trained model is not stored in git. Place these two files in this folder:

| File | Purpose |
|---|---|
| `pawcare_breed_classifier.keras` | MobileNetV2 transfer-learning model trained by `train.py` (37 Oxford-IIIT Pet breeds, 8 epochs) |
| `labels.json` | Breed names in the model's output order (small, kept in git) |

The service reads `models/pawcare_breed_classifier.keras` by default. Use the `MODEL_PATH` and
`LABELS_PATH` environment variables to point at a different location. Always replace the model and
`labels.json` together.

## Download

Download `pawcare_breed_classifier.keras` from the project's GitHub Releases page:

https://github.com/Tendo505/PawCare/releases/latest  (placeholder: upload the model as a release asset)

Then restart the AI service. `GET http://127.0.0.1:8001/health` should report `"status": "ready"`.

If the file is missing, `/health` reports `model_missing`; if it exists but cannot be opened,
`/health` reports `model_load_failed`. In both cases `/predict` returns HTTP 503 with a clear message.
