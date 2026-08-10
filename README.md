# PawCare AI — FYP (Advanced Kotlin Stack)

PawCare AI is an Android pet-health manager backed by a Laravel REST API, PostgreSQL, and a trainable FastAPI image classifier. It covers the FYP specification in `FYP.md`: authentication, pet profiles, vaccination schedules, medical records, appointments, reminders, dashboard statistics, and cat/dog breed recognition.

## What is included

- Android (Kotlin + XML + Material 3): polished sign-in/register screens, a demo account, dashboard, pet CRUD, vaccination CRUD, appointment CRUD, medical record CRUD, validation, search-ready data model, and AI photo upload.
- Offline presentation mode: the Android app persists data with `SharedPreferences`, so the core demo does not fail when Wi-Fi or Docker is unavailable.
- Laravel 12 REST API: Sanctum tokens, per-owner authorization, validated resource endpoints, dashboard aggregation, AI proxy/storage, seed data, and feature tests.
- PostgreSQL schema: all 11 tables requested by the project brief, with foreign keys, indexes, JSONB prediction output, and cascade behavior.
- FastAPI AI service: validated image upload, lazy TensorFlow inference, top-three results, clear readiness checks, and MobileNetV2 transfer-learning script for the Oxford-IIIT Pet dataset.
- Docker Compose: one command starts PostgreSQL, Laravel, and FastAPI.

## Repository layout

```text
PawCareAI/
├── app/                 Android application
├── backend/             Laravel 12 REST API
├── ai-service/          FastAPI inference and TensorFlow training
├── docs/                Architecture, API examples, and UAT checklist
└── compose.yaml         Local full-stack environment
```

## Run the Android app

1. Open this folder in Android Studio Koala or newer.
2. Select the `app` run configuration and an Android 8.0+ emulator/device.
3. Run the app.
4. Tap **Explore with demo account**, or use `demo@pawcare.my` / `PawCare123`.

The Android emulator reaches host services through `10.0.2.2`; the development URLs are declared in `app/build.gradle.kts`.

Command-line verification on this machine should use Android Studio's JDK 17:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat testDebugUnitTest assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Run PostgreSQL, Laravel, and FastAPI

Install Docker Desktop, then run:

```bash
docker compose up --build
```

- Laravel API: `http://localhost:8000/api`
- Laravel health check: `http://localhost:8000/up`
- FastAPI docs: `http://localhost:8001/docs`
- FastAPI readiness: `http://localhost:8001/health`
- PostgreSQL: `localhost:5432` (`pawcare` / `pawcare_secret`)

The container seeds the same demo user and sample records used by the Android presentation mode.

## Train the breed classifier

The repository intentionally does not commit a large trained binary. Until the model is trained, `/health` reports `model_missing` and `/predict` returns HTTP 503 with a setup message—no fake breed is returned.

```bash
cd ai-service
python -m venv .venv
# Windows: .venv\Scripts\activate
# macOS/Linux: source .venv/bin/activate
pip install -r requirements-dev.txt
python train.py --epochs 8
pytest
```

Training downloads Oxford-IIIT Pet and ImageNet MobileNetV2 weights, then writes `models/pawcare_breed_classifier.keras`. Restart the AI container afterward. For final evaluation, record accuracy, macro F1, per-breed precision/recall, confusion matrix, and inference time on a held-out test split.

## Backend without Docker

Requirements: PHP 8.2+, Composer 2, and PostgreSQL 14+.

```bash
cd backend
composer install
cp .env.example .env
php artisan key:generate
php artisan migrate --seed
php artisan test
php artisan serve
```

Use [docs/api.http](docs/api.http) in an HTTP client, or import the calls into Postman. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the ERD and [docs/UAT.md](docs/UAT.md) for acceptance tests.

## Safety and scope

PawCare AI organizes owner-entered records and estimates breeds from supported images. It does not diagnose illness and is not a substitute for a veterinarian. Rare breeds, mixed breeds, poor lighting, and multiple animals can reduce classification reliability.
