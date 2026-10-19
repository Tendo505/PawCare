# PawCare AI service

FastAPI service that recognises 37 cat and dog breeds (Oxford-IIIT Pet) from a photo. Laravel calls it at
`http://127.0.0.1:8001`; the Android app never calls it directly.

## Requirements

- **Python 3.12** (3.10–3.12 work). TensorFlow does not support Python 3.14, so a virtual environment
  built with Python 3.14 cannot run the model.
- About 2 GB of free disk space for TensorFlow.
- The trained model file (see `models/README.md`).

## Set up the environment (once)

```powershell
cd C:\Users\dozle\StudioProjects\PawCare\ai-service
py -3.12 -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements-dev.txt
```

If an old `.venv` was made with another Python version, delete the `.venv` folder first.

## Place the model

Put `pawcare_breed_classifier.keras` and `labels.json` in `models/`. See `models/README.md`.

## Start the service

```powershell
.\.venv\Scripts\Activate.ps1
uvicorn app.main:app --host 127.0.0.1 --port 8001
```

Check `http://127.0.0.1:8001/health` (`"status": "ready"`) and `http://127.0.0.1:8001/docs`.

### Settings

| Variable | Default | Meaning |
|---|---|---|
| `CONFIDENCE_THRESHOLD` | `0.60` | Below this, `/predict` adds `"low_confidence": true` and `"message"` |
| `MODEL_PATH` | `models/pawcare_breed_classifier.keras` | Model file location |
| `LABELS_PATH` | `models/labels.json` | Label file location |
| `MIN_PET_PROBABILITY` | `0.20` | Cat/dog detector threshold used before breed prediction |

Example: `$env:CONFIDENCE_THRESHOLD='0.7'; uvicorn app.main:app --port 8001`

## Evaluate the model

```powershell
python evaluate.py
```

Runs the model on the Oxford-IIIT Pet **test** split only (never used for training or choosing settings)
and writes `evaluation/summary.json`, `evaluation/per_breed_metrics.csv` and
`evaluation/confusion_matrix.png`. The first run downloads the dataset (about 800 MB).

## Run tests

```powershell
pytest
```

The tests stub the model, so they run without TensorFlow loading the real weights.

## Troubleshooting

| Problem | Fix |
|---|---|
| `/health` says `model_missing` | Place the model in `models/` (see `models/README.md`) and restart. |
| `/health` says `model_load_failed` | The model file is damaged or from an incompatible TensorFlow version. Replace it and restart. |
| `pip` cannot find `tensorflow-cpu` | Your Python is too new. Recreate `.venv` with Python 3.12. |
| Port 8001 already in use | Close the other service (`Get-NetTCPConnection -LocalPort 8001`), or stop the old uvicorn window. Laravel expects port 8001 (`AI_SERVICE_URL` in `backend/.env`). |
| First scan is slow | TensorFlow and the models load on the first request (10–30 s). Do one test scan before the demo. |
| App shows a DEMO result | Laravel could not reach this service; check it is running on port 8001. |
