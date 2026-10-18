# PawCare Laravel API — local FYP demo

Laravel serves the Android application's existing API through **Laravel Herd on Windows**. A fresh demo uses a dedicated SQLite file. Existing PostgreSQL installations remain supported; setup never converts or resets their data.

## Requirements

- The project's existing PHP 8.2+ / Laravel 12 installation, Composer and Laravel Herd. Keep the installed versions; do not upgrade for this demo.
- PHP extensions required by Laravel, plus `pdo_sqlite` and `sqlite3` for the demo. Confirm with `php -m`.
- The existing Android Studio Koala project and emulator.
- The existing Python AI service and trained model for genuine breed predictions.

## One-time setup

1. In Herd, link this **backend** folder as `pawcare-api` (or run `herd link pawcare-api` from this folder). The document root must be `backend/public`.
2. Open a terminal in this folder and run `start-demo.bat` (`.\start-demo.bat` in PowerShell).
3. With no `.env`, setup copies `.env.example`. With no dependencies, it runs `composer install` using the existing lock file. It generates a missing application key only after the demo database guard passes.
4. Setup creates `database/database.sqlite`, runs the existing migrations and seeds fictional demo records. This file and its journal files are gitignored.

**Important: start-demo resets demo records every time it runs.** It refuses PostgreSQL, `DB_URL`, alternate SQLite files, symlinks to another file, and production environments. It checks resolved connection settings in the same process that resets the database; it never prints database credentials.

### If you already have a PostgreSQL `.env`

The script deliberately stops. Your existing `.env` and PostgreSQL records are preserved. To opt into the demo, first save a private backup of `.env` outside Git. Then edit your local `.env` yourself:

```dotenv
DB_CONNECTION=sqlite
# remove DB_URL and DB_DATABASE so database/database.sqlite is used
# remove or comment DB_HOST, DB_PORT, DB_USERNAME and DB_PASSWORD
```

Do not replace an existing application key. Run `start-demo.bat` after this deliberate change. If Laravel configuration was cached, run `php artisan config:clear` so Herd uses your new selection. The setup guard does not silently clear the running application's configuration cache.

This is a separate demo database, **not a PostgreSQL-to-SQLite data migration**. To return to PostgreSQL, restore your private `.env` backup and clear the configuration cache. Do not run `migrate:fresh` on PostgreSQL.

## Start the demo

1. Start Herd. Verify `http://pawcare-api.test/api/health` responds.
2. Start the AI service on `127.0.0.1:8001` using its README and existing Python environment.
3. Start the Android emulator and run PawCare from Android Studio.
4. Log in with the fictional account: **demo@pawcare.test / password123**.

The seed contains Milo (cat), Buddy (dog), three vaccination records, two appointments and two medical records. Upcoming dates are relative to the day of a fresh reset. The seeder can run repeatedly without duplicating its records.

The existing `/api/health` route includes connection details used by the app. Its response has not been replaced with a smaller JSON object. All existing authentication, pet, health and prediction routes remain unchanged.

## Reset demo data

Run `start-demo.bat` again. It executes `migrate:fresh --seed` **only after validating this project's dedicated SQLite connection**. This deletes records in that demo file and invalidates tokens stored there. Log in again afterward. Do not use an unguarded `php artisan migrate:fresh` against an existing database.

## Run tests

```powershell
php artisan test
```

`phpunit.xml` uses an isolated in-memory SQLite database. Never override the test connection with your PostgreSQL database. Tests use fake data and AI-service mocks; passing them is not a measured real-model accuracy result.

## AI service unavailable

Real model predictions are unchanged. With `AI_DEMO_FALLBACK=true`, eligible service outages produce an explicitly labelled **DEMO** response in the existing response shape, not a real breed classification. It must not be presented as model evidence. The demo result is not saved as a real prediction. Turn the flag off when demonstrating genuine error handling or evaluating the model.

## Troubleshooting

| Problem | Check |
|---|---|
| Herd shows “site not found” | Link the backend folder as `pawcare-api`; confirm Herd serves its `public` directory. |
| Emulator cannot reach server | Keep its existing `http://10.0.2.2/api/` base URL and `Host: pawcare-api.test` header. `127.0.0.1` inside the emulator is the emulator itself. Ensure Herd is running and Windows Firewall allows the local connection. Do not change the Android network-security files. |
| Missing application key | For a new guarded demo, rerun `start-demo.bat`. Do not rotate an existing app key. |
| Missing SQLite file | Use the guarded setup to create `database/database.sqlite`; check write permissions on `database`. |
| “Could not find driver” | Enable the SQLite extensions in the existing Herd PHP version, then open a new terminal. |
| Reset refused | Existing PostgreSQL, `DB_URL`, an alternate file or production settings are intentional safety stops. Follow the opt-in instructions above only if you want a separate SQLite demo. |
| Browser still uses PostgreSQL after opt-in | After deliberately changing your local `.env`, run `php artisan config:clear`. Setup leaves any existing configuration cache untouched. |
| AI service off / model missing | Start the Python service and place the trained model as documented in `ai-service/README.md`. Check port 8001. A labelled DEMO fallback is not real recognition. |
| 401 after reset | Resetting the demo removes old tokens; log in again. |

## Migration portability

All five migrations have been reviewed. Laravel translates `jsonb` to text on SQLite while retaining PostgreSQL JSONB; the field name and JSON serialization are unchanged. Foreign keys remain enabled. SQLite receives equivalent expression/partial indexes and insert/update validation triggers for the PostgreSQL checks, because SQLite cannot add named CHECK constraints with `ALTER TABLE` without rebuilding tables. No application table or column was renamed or removed.

Never commit `.env`, database files, uploaded private images, datasets or model weights.
