# PawCare – FYP demo checklist and refactor notes

One file for the demo day and the Phase 3 Android refactor.

## 1. Start order (do it in this order)

1. **Laravel Herd** – open Herd and make sure `pawcare-api.test` is served from `backend\`.
2. **Switch the backend to SQLite first.** If `backend\.env` still says `DB_CONNECTION=pgsql`, back the file up outside Git and edit it as described in `backend\README.md` ("If you already have a PostgreSQL `.env`"). Then run `php artisan config:clear`.
3. **Backend demo data** – in `backend\`, run `.\start-demo.bat`. It refuses to run on PostgreSQL and resets the SQLite demo file every time.
4. **AI service** – in `ai-service\`:
   ```
   .venv\Scripts\activate
   uvicorn app.main:app --host 127.0.0.1 --port 8001
   ```
   **Do one warm-up scan before the demo.** The model loads on the first prediction, so the first scan is slow.
5. **Emulator** – start it from Android Studio Koala (the app reaches Herd through `10.0.2.2`).
6. **App** – run the `app` configuration.

Demo login: **demo@pawcare.test / password123** (fictional account from `DemoSeeder`).

## 2. Five-minute demo flow

| Time | Step | What to show |
|---|---|---|
| 0:00 | Log in (or register a new fake account) | Validation messages, then the dashboard loads |
| 0:45 | Home tab | Greeting, connection card (Laravel / database / AI), the four totals, reminders |
| 1:30 | Pets → Add pet | Fill in the form, save, the new pet card appears |
| 2:30 | Health → Add vaccination | Pick the new pet, pick a due date, save, the card appears; Home reminders update |
| 3:30 | AI Scan | Link the pet, choose a photo, Analyze: best match, confidence, two alternatives, saved in history |
| 4:30 | Back to Home | Totals and reminders now include the new records |

## 3. Reset the demo data

Run `backend\start-demo.bat` again. It runs `migrate:fresh --seed` **only** on the dedicated SQLite demo file, then reloads Milo, Buddy and their records. Saved tokens are invalidated, so log in again. Never run a bare `php artisan migrate:fresh` against PostgreSQL.

## 4. Limitations and future work

- Breed results are estimates only, not veterinary advice.
- The model knows only the 37 Oxford-IIIT Pet breeds. On the official test split (3,669 images) it scores 77.95% accuracy and 0.7624 macro F1, at about 85 ms per image on CPU.
- The AI service now returns `low_confidence` and `message` below 0.60 confidence, but the app does not show them yet.
- The labelled AI DEMO fallback (`AI_DEMO_FALLBACK`) is not a real prediction. It only keeps the flow working when the AI service is down.
- Some screen text still says "PostgreSQL" even when the demo runs on SQLite. That was left unchanged so the app looks the same.

## 5. Needs a human decision

- **ai-service in Git:** the root `.gitignore` ignores the whole `ai-service/` folder ("private component"), so the Phase 2 work cannot be committed. Should it be un-ignored? Model weights stay excluded by `ai-service/.gitignore` either way.
- **Model download link:** `ai-service/models/README.md` has a placeholder GitHub Releases link, and the model is not uploaded.
- **Bad-upload status codes:** the brief asked for 400; the service keeps 413/415/422 so the status codes Laravel passes to the app do not change.
- **Commits:** when to commit, and whether the 5 local Phase 1 commits on `main` are OK as they are. Nothing has been pushed.
- **`importlib_resources`:** `evaluate.py` needed it (`tensorflow-datasets` does not install it). It was installed by hand into `.venv`; should it be added to `requirements.txt`?

---

## 6. Phase 3 Android refactor notes

### Why plain screen classes, not Fragments or Activities

The dashboard was one Activity with a BottomNavigationView that inflates `screen_home`, `screen_pets`, `screen_health` or `screen_ai` into `contentContainer`. Separate Activities would add transitions and back-stack entries. Fragments would add lifecycle and state-restoration behaviour. Either would change how the app behaves. So `DashboardActivity` stays the only host, and each screen became a small plain Kotlin class. Each class draws into the same container through `DashboardPage`, so the layouts, navigation, toolbar titles and dialogs are unchanged.

### File map

| File | Purpose |
|---|---|
| `DashboardActivity.kt` | Host: toolbar, bottom navigation, photo picker, loading/error screens, session restore |
| `dashboard/DashboardScreens.kt` | Creates every screen once and draws the one for the selected tab |
| `dashboard/DashboardPage.kt` | What screens draw into: title, inflate, show, toast, delete confirm, tab checks |
| `dashboard/HomeScreen.kt` | Home tab: greeting, totals, reminders, quick actions |
| `dashboard/SystemHealthCard.kt` | Home connection card and its retry |
| `dashboard/PetsScreen.kt` | Pets tab: pet cards and delete |
| `dashboard/PetDialog.kt` | Add/edit pet form |
| `dashboard/HealthScreen.kt` | Health tab: hosts the three sections |
| `dashboard/VaccinationsSection.kt` | Vaccination list, card, form, delete |
| `dashboard/AppointmentsSection.kt` | Appointment list, card, form, delete |
| `dashboard/MedicalRecordsSection.kt` | Medical record list, card, form, delete |
| `dashboard/AiScanScreen.kt` | AI tab: scan state, pet link, analyze, result and history |
| `dashboard/PhotoReader.kt` | Reads the photo (10 MB limit), type check, preview decode |
| `dashboard/PredictionCards.kt` | Latest-result card and history card |
| `dashboard/DialogFields.kt` | Form fields, shared save dialog, "add a pet first" prompt |
| `dashboard/RecordCards.kt` | Record card, edit/delete buttons, list-or-empty helper |
| `dashboard/DashboardViews.kt` | Shared label/card/button builders, dp, colours, date and weight format |
| `data/AppRepository.kt` | Thin facade with the same public methods as before |
| `data/ApiSession.kt` | The one place for the saved token, active user and Retrofit API |
| `data/ApiCalls.kt` | `ApiRequestException`, enqueue helpers, error parsing, cache helpers |
| `data/RecordRefresh.kt` | Loads the five record lists in order, flat steps |
| `data/AuthRepository.kt` | Restore session, register, login, logout |
| `data/PetRepository.kt` | Pets cache, save, delete |
| `data/VaccinationRepository.kt` | Vaccinations cache, save, delete |
| `data/AppointmentRepository.kt` | Appointments cache, save, delete |
| `data/MedicalRecordRepository.kt` | Medical records cache, save, delete |
| `data/AiScanRepository.kt` | Photo upload and prediction history |
| `data/SystemHealthRepository.kt` | Connection check |
| `res/values/dashboard_strings.xml` | All dashboard text moved out of Kotlin (same wording) |

### Screen flow (screen → repository → API endpoint)

| Screen | Repository | Endpoint |
|---|---|---|
| Login / Register | AuthRepository | `POST login`, `POST register` |
| Dashboard start | AuthRepository → RecordRefresh | `GET me`, then `GET pets` → `vaccinations` → `medical-records` → `appointments` → `ai-predictions` |
| Home connection card | SystemHealthRepository | `GET health` |
| Pets / PetDialog | PetRepository | `POST pets`, `PUT pets/{id}`, `DELETE pets/{id}` |
| Health – vaccinations | VaccinationRepository | `POST/PUT/DELETE vaccinations` |
| Health – appointments | AppointmentRepository | `POST/PUT/DELETE appointments` |
| Health – medical records | MedicalRecordRepository | `POST/PUT/DELETE medical-records` |
| AI Scan | AiScanRepository | `POST ai-predictions` (multipart) |
| Sign out | AuthRepository | `POST logout` |

### Callback flattening

- `refreshData` is now `RecordRefresh`: `start → loadVaccinations → loadMedicalRecords → loadAppointments → loadPredictions → finish`. The load order is the same. The first failure stops the chain and is reported, and caches are replaced only when all five succeed (pets first, so predictions can read pet names).
- `restoreAccountRecords` is now `restoreAccountRecords → onSessionRestored → onRecordsLoaded`, plus `showSessionError`. A 401 goes back to login; any other failure shows the database error screen with Try again.

### Removed dead code

- `ownerEmail` on `Pet`, `VaccinationRecord`, `MedicalRecord`, `Appointment` and `BreedPrediction`. It was only ever written (from `activeUser.email`) and never read anywhere: no Kotlin, test or XML references.
- The legacy preferences wipe (`pawcare_store_v1` cleared on every `AppRepository` start). Nothing in the app writes that file any more. Caveat: on a phone that still had data from the old offline version and never opened a newer build, that old file would no longer be cleared. It is never read either way.

### Anything unsure or left unchanged

- I did the split in one pass and built it once at the end (`assembleDebug testDebugUnitTest`: BUILD SUCCESSFUL, PawCareLogicTest 14/14 passed), not one screen at a time.
- **Not checked visually:** no emulator was running, so the screens have not been compared side by side in the app. Please click through all four tabs and each dialog once before the demo.
- Spinner values (`Dog`/`Cat`, `Unknown`/`Female`/`Male`, the status lists), `09:00` and `pet-photo.<ext>` stay in Kotlin, because they are values sent to the API, not display text.
- Fallback error messages inside `data/ApiCalls.kt` and "Please sign in." stay in Kotlin (data layer, no Context). `AuthActivity.kt`, the layouts and `PawCareApi.kt` were not touched.
- In data classes, the numbered sections reuse the same labels. `//2.input` holds request building, `//3.process` the API calls, `//4.output` reads and mapping. `//1.screen setup` is used only where there is real setup.
- The pet dialog now uses the shared save dialog. Its validation, toast, disabled Save button and refresh after saving work exactly as before.

### Final line count (Kotlin, `app/src/main/java/com/example/pawcareai`)

| File | Lines | File | Lines |
|---|---|---|---|
| DashboardActivity.kt | 178 (was 1,243) | data/AppRepository.kt | 162 (was 852) |
| dashboard/AiScanScreen.kt | 185 | data/ApiCalls.kt | 140 |
| dashboard/DialogFields.kt | 157 | data/AuthRepository.kt | 121 |
| dashboard/DashboardViews.kt | 133 | data/AiScanRepository.kt | 115 |
| dashboard/AppointmentsSection.kt | 123 | data/AppointmentRepository.kt | 106 |
| dashboard/VaccinationsSection.kt | 115 | data/PetRepository.kt | 104 |
| dashboard/PetDialog.kt | 111 | data/Models.kt | 103 |
| dashboard/DashboardPage.kt | 110 | data/VaccinationRepository.kt | 102 |
| dashboard/PetsScreen.kt | 107 | data/MedicalRecordRepository.kt | 100 |
| dashboard/HomeScreen.kt | 106 | data/RecordRefresh.kt | 81 |
| dashboard/MedicalRecordsSection.kt | 105 | data/ApiSession.kt | 64 |
| dashboard/PhotoReader.kt | 88 | data/SystemHealthRepository.kt | 54 |
| dashboard/RecordCards.kt | 84 | data/DashboardLogic.kt | 117 (unchanged) |
| dashboard/PredictionCards.kt | 73 | AuthActivity.kt | 230 (unchanged) |
| dashboard/SystemHealthCard.kt | 63 | network/PawCareApi.kt | 362 (unchanged) |
| dashboard/DashboardScreens.kt | 54 | validation/InputValidators.kt | 342 (unchanged) |
| dashboard/HealthScreen.kt | 31 | Login/RegisterActivity.kt | 8 each (unchanged) |
