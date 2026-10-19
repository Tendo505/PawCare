# PawCare – FYP demo checklist and code guide

One file for the demo day and a guide to the Android code.

## 1. Start order (do it in this order)

1. **Laravel Herd** – open Herd and make sure `pawcare-api.test` is served from `backend\`.
2. **Database.** Your `backend\.env` uses PostgreSQL, and the demo account is already in it (added with `php artisan db:seed --class=DemoSeeder`). To use the separate SQLite demo database instead, back up `backend\.env` outside Git, edit it as described in `backend\README.md`, run `php artisan config:clear`, then run `.\start-demo.bat`. That script refuses PostgreSQL and resets the SQLite demo file every time.
3. **AI service** – in `ai-service\`:
   ```
   .venv\Scripts\activate
   uvicorn app.main:app --host 127.0.0.1 --port 8001
   ```
   Run exactly one copy. **Do one warm-up scan before the demo.** The model loads on the first prediction, so the first scan is slow.
4. **Emulator** – start it from Android Studio Koala (the app reaches Herd through `10.0.2.2`).
5. **App** – run the `app` configuration.

Demo login: **demo@pawcare.test / password123** (fictional account from `DemoSeeder`).
Demo photo: `Samoyed_Demo.jpg` in the emulator's Downloads (recognised as Samoyed at 99%). It comes from the Oxford-IIIT Pet test set (CC BY-SA 4.0).

## 2. Five-minute demo flow

| Time | Step | What to show |
|---|---|---|
| 0:00 | Log in (or register a new fake account) | Validation messages, then the dashboard loads |
| 0:45 | Home tab | Greeting, connection card (Laravel / database / AI), the four totals, reminders |
| 1:30 | Pets → Add pet | Fill in the form, save, the new pet card appears |
| 2:30 | Health → Add vaccine | Pick the new pet, pick a due date, save, the card appears; Home reminders update |
| 3:30 | AI Scan | Link the pet, choose `Samoyed_Demo.jpg`, Analyze: best match, confidence, two alternatives, saved in history |
| 4:30 | Back to Home | Totals and reminders now include the new records |

## 3. Reset the demo data

- **SQLite demo database:** run `backend\start-demo.bat` again. It runs `migrate:fresh --seed` **only** on the dedicated SQLite demo file, then reloads Milo, Buddy and their records. Log in again afterwards.
- **PostgreSQL:** never run `migrate:fresh` on it. Delete demo records in the app, or run `php artisan db:seed --class=DemoSeeder` again to add back any missing demo records (it never creates duplicates).

## 4. Limitations and future work

- Breed results are estimates only, not veterinary advice.
- The model knows only the 37 Oxford-IIIT Pet breeds. On the official test split (3,669 images) it scores 77.95% accuracy and 0.7624 macro F1, at about 60–85 ms per image on CPU (it varies between runs). Bombay cats are never recognised (F1 0.00), and Bengal (0.28) is also weak.
- A small pet in a wide landscape can score low. The cat-or-dog check now looks at the photo's centre a second time, but the breed guess can still be weak (for example, a British Shorthair scored 23%).
- The AI service returns `low_confidence` and `message` below 0.60 confidence, but the app does not show them yet.
- The labelled AI DEMO fallback (`AI_DEMO_FALLBACK`) is not a real prediction. It only keeps the flow working when the AI service is down.
- Some screen text says "PostgreSQL" even when the demo runs on SQLite. That was left unchanged so the app looks the same.

## 5. Needs a human decision

- **ai-service in Git:** the root `.gitignore` ignores the whole `ai-service/` folder ("private component"), so the Phase 2 work cannot be committed. Should it be un-ignored? Model weights stay excluded by `ai-service/.gitignore` either way.
- **Model download link:** `ai-service/models/README.md` has a placeholder GitHub Releases link, and the model is not uploaded.
- **Bad-upload status codes:** the brief asked for 400; the service keeps 413/415/422 so the status codes Laravel passes to the app do not change.
- **Commits:** when to commit, and whether the 5 local Phase 1 commits on `main` are OK as they are. Nothing has been pushed.
- **`importlib_resources`:** `evaluate.py` needed it (`tensorflow-datasets` does not install it). It was installed by hand into `.venv`; should it be added to `requirements.txt`?

---

## 6. Android code guide

### Why plain screen classes, not Fragments or Activities

The dashboard is one Activity with a BottomNavigationView that inflates `screen_home`, `screen_pets`, `screen_health` or `screen_ai` into `contentContainer`. Separate Activities would add transitions and back-stack entries. Fragments would add lifecycle and state-restoration behaviour. Either would change how the app behaves. So `DashboardActivity` stays the only host, and each tab is one plain Kotlin class that draws into the same container. **Each tab file also contains its own pop-up forms** (dialogs), so one file holds everything for one tab.

Words used in the code:
- **Screen**: one bottom tab (Home, Pets, Health, AI Scan).
- **Pop-up / dialog**: the form that opens over a screen with fields and Cancel/Save (for example "Add pet").
- **Repository**: the class that sends data to Laravel and keeps the phone's copy.
- **Validator**: the rules for a form ("Enter the pet's name.").

### Beginner-friendly style rules used in every file

- Every file uses the same numbered sections, in this order (only the ones that apply): `//1.screen setup`, `//2.input`, `//3.process`, `//4.output`, `//5.navigation`. In the data classes, `//2.input` builds the request sent to Laravel, `//3.process` sends it, and `//4.output` reads or converts the result.
- Every function has a `//` comment saying what it does and, where it matters, why.
- No Kotlin shortcuts: no extension functions, no `::function` references, no `let` / `takeIf` / `apply` / `also` / `runCatching`, no `?: return`, no `!!`, no one-line `fun x() = ...`, and no `it`. Instead: plain `if / else`, `for` loops, `try / catch`, and variables with their type written out.
- Lambdas (`{ result -> ... }`) appear only where Android or Retrofit need a callback: button clicks, dialog buttons and Laravel answers. Each has a named parameter.
- `ApiCallback<T>` is the only generic class. `T` means "the type of the answer", so one class handles every Laravel request.

### How one action flows through the code (example: saving a vaccination)

1. `HealthScreen` "Add vaccine" button → `HealthScreen.showVaccinationForm(null)` opens the pop-up, built with `FormBuilder`.
2. Save → `HealthScreen.saveVaccination()` checks the form with `HealthInputValidator`; any error goes to the field and a toast.
3. `AppRepository.saveVaccination()` → `VaccinationRepository.saveVaccination()` builds a `VaccinationRequest` and sends `POST vaccinations` (or `PUT vaccinations/{id}`).
4. `ApiCallback` receives Laravel's answer → `RecordConverter.toVaccination()` → the record is put in `RecordCache`.
5. Back in `HealthScreen.finishSave()`: on success the pop-up closes and the health lists redraw; on failure it shows "The database could not be updated…" and the pop-up stays open.

### File map

| File | Purpose |
|---|---|
| `DashboardActivity.kt` | Creates the four tab screens, restores the account in 3 steps, loading/error screens, tabs, sign out, photo picker |
| `AuthActivity.kt` (+ `LoginActivity`, `RegisterActivity`) | Login and register forms |
| `dashboard/HomeScreen.kt` | Home tab: greeting, connection card, totals, reminders, quick action buttons |
| `dashboard/PetsScreen.kt` | Pets tab: pet cards and delete, plus the Add/Edit pet pop-up and the "Add a pet first" pop-up |
| `dashboard/HealthScreen.kt` | Health tab: vaccination, appointment and medical record lists, plus the three Add/Edit pop-ups |
| `dashboard/AiScanScreen.kt` | AI tab: photo, analyze, result and history, plus the "Link to a pet" pop-up |
| `dashboard/DashboardPage.kt` | What every screen gets: repository, drawing helpers, toasts, delete confirmation, tab checks |
| `dashboard/ViewMaker.kt` | Builds labels, cards, message cards, reminder cards, buttons, spacing |
| `dashboard/FormBuilder.kt` | Builds pop-up form inputs, dropdowns, date pickers and the Save/Cancel pop-up |
| `dashboard/RecordCards.kt` | Health record card and the Edit/Delete buttons |
| `dashboard/TextFormat.kt` | Date, weight, percent and breed-name formatting |
| `dashboard/PhotoReader.kt` | Reads the photo (10 MB limit), type check, preview |
| `dashboard/PredictionCards.kt` | Result card and history card on the AI tab |
| `data/AppRepository.kt` | The one class screens talk to; passes each call to a feature repository |
| `data/ApiSession.kt` | Saved token, signed-in account, Retrofit API |
| `data/ApiCalls.kt` | `ApiRequestException`, `ApiCallback`, `EmptyApiCallback`, `ApiErrorReader`, `blankToNull` |
| `data/RecordCache.kt` | The phone's memory copy of all record lists |
| `data/RecordConverter.kt` | Laravel JSON classes → app record classes |
| `data/RecordRefresh.kt` | Loads pets → vaccinations → medical records → appointments → scans |
| `data/AuthRepository.kt` | Restore session, register, login, logout |
| `data/PetRepository.kt`, `VaccinationRepository.kt`, `MedicalRecordRepository.kt`, `AppointmentRepository.kt` | Save/delete each record type |
| `data/AiScanRepository.kt` | Photo upload and scan history |
| `data/SystemHealthRepository.kt` | Connection check |
| `data/DashboardLogic.kt` | Home totals and appointment reminders |
| `validation/InputValidators.kt` | All form rules |
| `network/PawCareApi.kt` | API endpoints, JSON classes, OkHttp/Retrofit setup |

### Screen → repository → API endpoint

| Screen | Repository | Endpoint |
|---|---|---|
| Login / Register | AuthRepository | `POST login`, `POST register` |
| Dashboard start | AuthRepository, then RecordRefresh | `GET me`, then `GET pets` → `vaccinations` → `medical-records` → `appointments` → `ai-predictions` |
| Home connection card | SystemHealthRepository | `GET health` |
| Pets / PetDialog | PetRepository | `POST pets`, `PUT pets/{id}`, `DELETE pets/{id}` |
| Health: vaccinations | VaccinationRepository | `POST/PUT/DELETE vaccinations` |
| Health: appointments | AppointmentRepository | `POST/PUT/DELETE appointments` |
| Health: medical records | MedicalRecordRepository | `POST/PUT/DELETE medical-records` |
| AI Scan | AiScanRepository | `POST ai-predictions` (multipart) |
| Sign out | AuthRepository | `POST logout` |

### Laravel (PHP) files: the same input → process → output

- **Controllers:** every endpoint method is written as three marked steps: `//2.input` (read and validate the request), `//3.process` (check the owner, then read or save), `//4.output` (return JSON). Shared helpers sit under file-level `//2.input` (validation rules such as `validatePet()`) and `//3.process` (owner checks such as `checkOwner()`).
- **Models:** `//1.setup` (fillable fields and casts) and `//4.output` (relationships such as `pets()`), with a one-line comment naming the table.
- **Services, seeders and routes:** the same numbered steps; `start-demo.php` steps are relabelled 2/3/4 but its safety guard is unchanged.
- **No shortcuts:** no `fn () =>` arrow functions, ternaries, `match`, `?->` or chained `->when()`. Closures stay only where Laravel requires one (`whereHas`, `Rule::exists()->where`, `DB::transaction`, routes), written as full `function () use (...) { ... }`.
- **Same behaviour:** routes, JSON fields, status codes and validation messages are unchanged; `php artisan test` passes 72 tests / 429 assertions, and the live API answered login, pets, dashboard and 404 checks the same way.
- **Migrations:** `up()` / `down()` sit under `//3.process`, with a comment on each table and on the key columns (what a foreign key is, what `cascadeOnDelete` does). In the hardening migration each SQL rule has a comment saying what it stops, and the SQLite rules are added with plain `addSqliteCheck()` calls instead of an array-unpacking loop. Proof that nothing changed: the old and new migrations were each run on a throwaway SQLite file, giving identical schemas (47 tables, indexes and triggers), and both rolled back completely.
- **Not changed:** the tests.

### Python AI service: the same input → process → output

- `app/main.py`: `#1.setup` (limits and JSON shapes), `#2.input` (check the upload, open the photo), `#3.process` (threshold, model status), `#4.output` (`/`, `/health`, `/predict`). Inside `/predict` the three steps are marked again.
- `app/classifier.py`: `#2.input` (load the models and labels), `#3.process` (`predict`, ranking, cat/dog check with the centre crop), `#4.output` (status for `/health`).
- `train.py` and `evaluate.py`: a short explanation at the top (transfer learning, which photos are used for what), then marked steps. List comprehensions, `a if b else c`, `lambda` and `np.add.at` are written out as plain loops and `if` statements.
- Same behaviour: 34 pytest tests pass. Re-running `evaluate.py` gave the same accuracy (0.7795) and macro F1 (0.7624); the trained model was not touched or retrained.

### XML layouts

Every layout now starts with a comment naming the Kotlin file that uses it, and each block has a comment (for example "connection card: is Laravel, the database and the AI service working? (filled by HomeScreen.showConnection)"). Only comments were added; nothing visible changed.

### Presentation

`Downloads\PawCareFinalSlide_IPO.pptx` (and `.pdf`), a copy of `PawCareFinalSlide.pptx` updated to match the code. The original file is untouched.
- Slide 4: caption now says Input (Android forms) → Process (Laravel and FastAPI) → Output (records shown in the app).
- Slide 12 "Saving a Pet": boxes name the real classes: PetDialog → PetRepository → PetController → PetsScreen.
- Slide 13 "Code Structure" (new): table of 2. Input / 3. Process / 4. Output for Android and Laravel.
- Slide 16 "AI Breed Estimation": boxes labelled Input / Process / Output, plus the measured 77.95% test accuracy.
- Slide 17 "Verification Evidence": 14 Android, 72 Laravel (429 assertions) and 34 FastAPI tests, dated 19 Oct 2026.
- Slide 19: "AI accuracy is measured on test images only."
- Slide numbers 13–22 renumbered; speaker notes on slides 12, 13, 16, 17 and 21 updated.

### Removed dead code

- `ownerEmail` on `Pet`, `VaccinationRecord`, `MedicalRecord`, `Appointment` and `BreedPrediction`. It was written but never read anywhere.
- The legacy preferences wipe (`pawcare_store_v1`). Nothing writes that file any more. Caveat: a phone that still had old offline data would no longer have it cleared, but it is never read either.

### Checked

- `assembleDebug testDebugUnitTest` (clean rebuild): BUILD SUCCESSFUL; `PawCareLogicTest` 14/14 passed.
- On the emulator with the new build: login, the Home tab (connection card, totals, reminders), Pets, Health, AI Scan (Samoyed photo → samoyed 99%, saved in history), and the Add vaccination dialog with an empty Save (field errors and toast; the dialog stays open).
- After merging the pop-ups into the tab files: the Add pet, Book appointment and Link to a pet pop-ups open correctly from their tabs.
- Not clicked through: editing and deleting each record type, and sign out. They use the same pattern as the screens that were checked.

### Left as it was

- Spinner values (`Dog`/`Cat`, `Unknown`/`Female`/`Male`, the status lists), `09:00` and `pet-photo.<ext>` stay in Kotlin, because they are values sent to the API, not display text.
- Error fallback messages in `data/ApiCalls.kt` and "Please sign in." stay in Kotlin (data layer, no Context).
- Layout XML files are unchanged.
- Each tab and its pop-ups share one file, so the tab files are longer: `HealthScreen.kt` (528, three lists and three pop-ups), `PetsScreen.kt` (289), `AiScanScreen.kt` (286) and `HomeScreen.kt` (252). `PawCareApi.kt` (376) and `InputValidators.kt` (453) are also long. Each file is still split into the numbered sections. The app has 32 Kotlin files in total.

### Line count per Kotlin file (`app/src/main/java/com/example/pawcareai`)

| File | Lines | File | Lines |
|---|---|---|---|
| AuthActivity.kt | 282 | data/AppRepository.kt | 189 |
| DashboardActivity.kt | 241 | data/DashboardLogic.kt | 160 |
| Login/RegisterActivity.kt | 8 each | data/ApiCalls.kt | 150 |
| dashboard/HealthScreen.kt | 528 | data/RecordRefresh.kt | 130 |
| dashboard/PetsScreen.kt | 289 | data/RecordConverter.kt | 111 |
| dashboard/AiScanScreen.kt | 286 | data/AuthRepository.kt | 107 |
| dashboard/HomeScreen.kt | 252 | data/Models.kt | 103 |
| dashboard/FormBuilder.kt | 175 | data/PetRepository.kt | 100 |
| dashboard/ViewMaker.kt | 140 | data/AppointmentRepository.kt | 93 |
| dashboard/PhotoReader.kt | 140 | data/VaccinationRepository.kt | 93 |
| dashboard/DashboardPage.kt | 106 | data/MedicalRecordRepository.kt | 92 |
| dashboard/RecordCards.kt | 90 | data/AiScanRepository.kt | 86 |
| dashboard/PredictionCards.kt | 87 | data/ApiSession.kt | 69 |
| dashboard/TextFormat.kt | 65 | data/RecordCache.kt | 62 |
| | | data/SystemHealthRepository.kt | 55 |
| | | network/PawCareApi.kt | 376 |
| | | validation/InputValidators.kt | 453 |
