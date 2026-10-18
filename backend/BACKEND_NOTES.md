# Backend notes — FYP demo preparation (Phase 1)

## Files changed or added

| File | Change |
|---|---|
| `.env.example` | `DB_CONNECTION=sqlite`; PostgreSQL host/port/database/user/password lines removed; `APP_URL=http://pawcare-api.test`; `AI_CONNECT_TIMEOUT=2`, `AI_REQUEST_TIMEOUT=15`, `AI_DEMO_FALLBACK=true` |
| `.gitignore`, `database/.gitignore` | `.env`, `*.sqlite` (plus journal/WAL files), `vendor/`, logs and generated storage stay out of git |
| `database/migrations/2026_08_31_000400_harden_api_and_database.php` | SQLite branch added (see below); PostgreSQL branch unchanged |
| `database/seeders/DemoSeeder.php` | New fictional demo account and records |
| `database/seeders/DatabaseSeeder.php` | Calls `DemoSeeder` after the breed and vaccine catalogue |
| `config/services.php` | Reads `AI_CONNECT_TIMEOUT`, `AI_REQUEST_TIMEOUT`, `AI_DEMO_FALLBACK` |
| `app/Services/DemoPrediction.php` | New: builds the labelled DEMO response |
| `app/Http/Controllers/AiPredictionController.php` | Short connect timeout; DEMO fallback only on connection failure/timeout or explicit "model missing" 503 |
| `scripts/start-demo.php`, `start-demo.bat` | Guarded one-command setup and reset of the SQLite demo database |
| `README.md` | Rewritten for Laravel Herd on Windows (Docker steps removed) |
| `tests/Feature/DemoFallbackTest.php`, `DemoSeederTest.php`, `SqliteDatabaseRulesTest.php` | New tests |

Register/login and create-pet coverage already exists in `tests/Feature/AuthAndPetTest.php`
(`test_user_can_register_and_receive_token`, `test_owner_can_log_in_read_account_and_log_out`,
`test_user_can_create_and_list_own_pet`), so those tests were kept rather than duplicated.

## SQLite migration adjustments

Every migration was reviewed. Table names, column names and column types used by the app are unchanged.

| Migration | PostgreSQL feature | SQLite handling |
|---|---|---|
| `0001_..._users_and_tokens` | none | unchanged |
| `2026_07_20_000100_create_pets_tables` | foreign keys with `cascadeOnDelete` / `nullOnDelete` | unchanged; `config/database.php` sets `foreign_key_constraints => true` for SQLite, verified by `test_foreign_keys_preserve_null_and_cascade_actions` |
| `2026_07_20_000200_create_health_tables` | `unsignedSmallInteger`, `time`, `decimal` | unchanged; Laravel maps these to SQLite types |
| `2026_07_20_000300_create_ai_and_notification_tables` | `jsonb('top_predictions')` | unchanged; Laravel's SQLite grammar stores it as text and the model's `array` cast returns the same JSON (round-trip test) |
| `2026_08_31_000400_harden_api_and_database` | raw SQL: `LOWER(email)` unique index, partial unique index, named `CHECK` constraints via `ALTER TABLE` | SQLite gets the same expression and partial indexes; the seven `CHECK` constraints become `BEFORE INSERT/UPDATE` triggers that `RAISE(ABORT, '<constraint name>')`, because SQLite cannot add named constraints with `ALTER TABLE`. Rollback drops the triggers and indexes. |

## AI fallback behaviour

- Successful predictions are unchanged: same validation, storage, database row and `201` JSON (`test_real_success_is_unchanged_when_fallback_is_enabled`).
- With `AI_DEMO_FALLBACK=true`, the fallback is used only when the AI service cannot be reached, the request times out (connect timeout 2 s, request timeout 15 s), or it answers `503` with a "model missing" message.
- The fallback returns `201` with the same fields as a saved prediction plus the optional fields `demo: true` and `message`. `breed` is `DEMO ONLY - no prediction`, `confidence` is `0`, `model_version` is `DEMO-fallback-not-a-model`, `id` is `0`, and nothing is saved to the database or disk.
- Authentication, image validation and pet ownership checks still run before any fallback.
- Other upstream errors (422 not a pet, 413, 415, other 503s, 5xx) keep their existing status and message.
- With `AI_DEMO_FALLBACK=false` (or unset — the config default is `false`), the original `503` "The AI service is not running..." response is returned.

## Routes checked

`routes/api.php` was not changed. Checked: `GET /api/health`, `POST /api/register`, `POST /api/login`, `GET /api/me`,
`POST /api/logout`, `GET /api/dashboard`, `apiResource` for `pets`, `vaccinations`, `medical-records`, `appointments`,
`GET|POST /api/ai-predictions`, and the JSON 404 fallback.

`GET /api/health` already existed. It returns `"status": "ok"` when the database and AI model are ready and
`"degraded"` otherwise, with a `services` breakdown. It was left as-is instead of being replaced by a bare `{"status":"ok"}`.

## Anything unsure

- The backend folder was previously ignored by the root `.gitignore`, so there is no earlier git version to diff against;
  the "unchanged" claims above come from reading the code and the existing tests.
- The demo fallback's `id` is `0`. If the app ever opens a prediction by id, that would point at nothing; the current
  scan screen only displays the returned result.
- `composer.lock` resolves Symfony 8.1 packages, which need PHP 8.4+. Herd's PHP must be 8.4 or newer for `composer install`.
- `start-demo.bat` resets the SQLite demo database every time it runs, and refuses to run against PostgreSQL or production.
