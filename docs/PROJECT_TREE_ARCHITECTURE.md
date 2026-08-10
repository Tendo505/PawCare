# PawCare AI Project Tree Architecture

This document shows the main source-code structure of the PawCare AI project. Generated dependencies and build artifacts such as `build/`, `vendor/`, `.gradle/`, and `.idea/` are omitted.

```text
PawCareAI/
|
+-- app/                                  # Android application
|   +-- build.gradle.kts                  # Android and Retrofit configuration
|   +-- src/
|       +-- main/
|       |   +-- AndroidManifest.xml
|       |   +-- java/com/example/pawcareai/
|       |   |   +-- MainActivity.kt       # Login and registration UI
|       |   |   +-- DashboardActivity.kt  # Dashboard, pets, health, and AI UI
|       |   |   +-- data/
|       |   |   |   +-- AppRepository.kt  # Local authentication and CRUD
|       |   |   |   +-- Models.kt         # Mobile data models
|       |   |   +-- network/
|       |   |       +-- PawCareApi.kt      # Retrofit API definitions
|       |   +-- res/
|       |       +-- layout/                # XML screens
|       |       +-- drawable/              # Icons and graphics
|       |       +-- menu/                  # Navigation menus
|       |       +-- values/                # Themes, colors, and strings
|       +-- test/                          # Unit tests
|       +-- androidTest/                   # Instrumented tests
|
+-- backend/                              # Laravel REST API
|   +-- app/
|   |   +-- Http/Controllers/
|   |   |   +-- AuthController.php
|   |   |   +-- DashboardController.php
|   |   |   +-- PetController.php
|   |   |   +-- VaccinationController.php
|   |   |   +-- MedicalRecordController.php
|   |   |   +-- AppointmentController.php
|   |   |   +-- AiPredictionController.php
|   |   +-- Models/
|   |       +-- User.php
|   |       +-- Pet.php
|   |       +-- PetBreed.php
|   |       +-- PetImage.php
|   |       +-- Vaccination.php
|   |       +-- VaccinationRecord.php
|   |       +-- MedicalRecord.php
|   |       +-- Appointment.php
|   |       +-- Veterinarian.php
|   |       +-- Notification.php
|   |       +-- AiPrediction.php
|   +-- routes/
|   |   +-- api.php                       # REST endpoints and Sanctum routes
|   +-- database/
|   |   +-- migrations/                   # PostgreSQL table definitions
|   |   +-- seeders/
|   |       +-- DatabaseSeeder.php         # Demo data
|   +-- config/                            # Database, authentication, CORS, and services
|   +-- storage/                           # Logs and uploaded AI images
|   +-- tests/Feature/
|   |   +-- AuthAndPetTest.php
|   +-- Dockerfile
|   +-- composer.json
|
+-- ai-service/                           # FastAPI and TensorFlow service
|   +-- app/
|   |   +-- main.py                       # /health and /predict endpoints
|   |   +-- classifier.py                 # Image preprocessing and inference
|   |   +-- __init__.py
|   +-- models/
|   |   +-- labels.json
|   |   +-- pawcare_breed_classifier.keras # Generated trained model
|   +-- tests/
|   |   +-- test_api.py
|   +-- train.py                          # MobileNetV2 training pipeline
|   +-- requirements.txt
|   +-- requirements-dev.txt
|   +-- Dockerfile
|
+-- docs/
|   +-- ARCHITECTURE.md
|   +-- PROJECT_TREE_ARCHITECTURE.md
|   +-- api.http
|   +-- UAT.md
|
+-- compose.yaml                          # PostgreSQL, Laravel, and FastAPI services
+-- settings.gradle.kts                   # Includes the Android app module
+-- build.gradle.kts                      # Root Gradle configuration
+-- gradle/
|   +-- libs.versions.toml                 # Dependency versions
+-- PROJECT_VARIANTS.md
+-- README.md
```

## Runtime Paths

```text
Android UI
+-- AppRepository --> SharedPreferences
+-- BreedRecognitionApi --> FastAPI --> TensorFlow model
+-- PawCareApi --> Laravel --> PostgreSQL
    (The client exists but is not currently connected to the Android activities.)
```

