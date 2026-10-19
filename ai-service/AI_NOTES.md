# AI service notes — FYP demo preparation (Phase 2)

The model, its architecture and the training results were not changed. `train.py` was not modified.

## Files changed or added

| File | Change |
|---|---|
| `app/classifier.py` | A model file that fails to load now raises `ModelNotReadyError` with a clear message instead of an unhandled 500; new `files_present` / `load_failed` properties |
| `app/main.py` | `/health` reports `ready`, `model_missing` or `model_load_failed`; `/predict` adds `low_confidence` and (when low) `message`, controlled by `CONFIDENCE_THRESHOLD` |
| `evaluate.py` | New: test-split evaluation |
| `tests/test_reliability.py` | New tests; `tests/test_api.py` unchanged |
| `.gitignore`, `models/README.md`, `README.md` | New |

## Startup reliability

TensorFlow and the model load lazily on the first prediction, so the service always starts.
- Model missing: `/health` → `{"status": "model_missing", "model_ready": false, ...}`; `/predict` → 503 "No trained model was found...". Laravel turns this into the labelled DEMO fallback.
- Model present but unreadable: `/health` → `model_load_failed`; `/predict` → 503 "The model file could not be loaded...". Laravel does **not** use the DEMO fallback for this (its fallback only matches "model missing"), so the app shows the error. Restart the service after replacing the file.

## Confidence threshold

`CONFIDENCE_THRESHOLD` (default `0.60`; invalid or out-of-range values use the default).
Every successful `/predict` response keeps all existing fields and now also includes `"low_confidence": true|false`.
When the top confidence is below the threshold it also includes `"message": "Not sure, try a clearer photo"`.
`top_predictions` already returned the top 3 breeds with probabilities; unchanged.

### How the Android app currently handles uncertain results

- Laravel validates the AI response and stores only the known fields, so `low_confidence` and `message` are **not** passed on to the app yet. Nothing changes on screen.
- `DashboardActivity.kt` (around line 820) shows the top breed with "NN% confidence" and lists the next two breeds with percentages, whatever the confidence. There is no special wording for a low score.
- A photo with no cat or dog is rejected earlier (HTTP 422 "No cat or dog was detected"), and the app shows that message.
- Showing the uncertainty in the app would need Laravel to pass the field through and a small UI change. Per the brief, the app was not changed.

## Input safety

Already in place before this phase, and kept so Laravel's status mapping and the app's messages do not change:
- Wrong content type or format → **415** "Upload a JPEG, PNG, or WebP image."
- Over 10 MB → **413**; too many pixels (decompression bomb) → **413**
- Empty, corrupt or truncated image, or smaller than 64×64 → **422**

The brief asked for 400. Switching to 400 would change status codes that Laravel forwards to the app
(Laravel maps 413/415/422 straight through and turns anything else into 502), which breaks hard rule 1.
**Needs a human decision** if 400 is really required.

## Train/test split

- `train.py` loads `train[:85%]` for training and `train[85%:]` for validation (early stopping and checkpoint choice).
- The official Oxford-IIIT Pet `test` split (3,669 images) is never loaded by `train.py`.
- `evaluate.py` uses only `split="test"`, with the same 224×224 resize and MobileNetV2 preprocessing, and stops if `labels.json` does not match the dataset's label order.
- So the split is clearly separated; no fix needed.

## Accuracy figures

Computed on 2026-10-19 with `python evaluate.py` (Python 3.12 `.venv`, CPU) on the official
Oxford-IIIT Pet **test** split only; no test image was used for training or for choosing settings.
The full output (`summary.json`, `per_breed_metrics.csv`, `confusion_matrix.png`) is written to
`evaluation/`, which is git-ignored.

| Metric | Value |
|---|---|
| Test images | 3,669 |
| Accuracy | 77.95% (0.7795) |
| Macro F1 | 0.7624 |
| Avg inference (ms/image) | 62.6–85.2 across two runs (single-image predictions, 50 timing samples) |

`tensorflow-datasets` 4.9.10 also needed `importlib_resources`, which pip did not pull in. It was
installed into `.venv` by hand (`pip install importlib_resources`). Only `evaluate.py` and `train.py`
use it; the running API does not.

## Weakest breeds

Bombay cats are never recognised (F1 0.00), followed by Bengal (0.28), American Pit Bull Terrier (0.47), Russian Blue (0.53) and British Shorthair (0.56). See `evaluation/per_breed_metrics.csv`.

## Anything unsure

- The root `.gitignore` ignores the whole `ai-service/` folder ("private component"), so none of these files can be committed until that line is removed. Model weights stay excluded by `ai-service/.gitignore` either way.
- The full pytest run (33 passed) and the real evaluation above were both run in the Python 3.12 `.venv`.
- `importlib_resources` is not listed in `requirements.txt` (adding it would add a dependency); a fresh `.venv` needs `pip install importlib_resources` before `evaluate.py` will run.
- `models/README.md` has a placeholder GitHub Releases link; the model has not been uploaded.
