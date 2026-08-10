# PawCare System Architecture

This document records the design work planned for 27 July to 2 August 2026. It covers the current PawCare pet-management scope: authentication, pet profiles, vaccination and medical records, appointments, reminders, and dashboard summaries. AI image recognition is outside the current sprint and can be added later as a separate service.

The editable DFD Level 0, DFD Level 1, and ERD are stored in `docs/diagrams/PawCare_Database_Design.drawio`.

## 1. System Overview

```text
Android application (Kotlin + XML)
        |
        | HTTPS / JSON through Retrofit
        v
Laravel REST API (authentication and business rules)
        |
        | Eloquent ORM
        v
PostgreSQL database (persistent project data)
```

The Android application is the user-facing client. Laravel validates requests, checks ownership, and exposes REST endpoints. PostgreSQL stores accounts and pet-care records. During demonstrations, `AppRepository` also provides local `SharedPreferences` storage so the basic Android flow can be shown when the API is unavailable.

## 2. Main Components

| Component | Responsibility |
| --- | --- |
| `MainActivity` | Sign-in, registration, validation, and session entry |
| `DashboardActivity` | Dashboard, bottom navigation, pet and health screens |
| `AppRepository` | Local session, demo data, CRUD operations, and dashboard totals |
| `PawCareApi` | Retrofit definitions for communication with Laravel |
| Laravel controllers | Validate requests and enforce authenticated ownership |
| Eloquent models | Represent database entities and relationships |
| PostgreSQL | Store users, pets, health records, and appointments |

## 3. Authentication Data Flow

```text
User enters details
    -> Android validates the form
    -> Laravel register/login endpoint
    -> Laravel validates credentials
    -> Sanctum creates an access token
    -> Android stores the active session
    -> Dashboard is opened
```

All protected API requests must include `Authorization: Bearer <token>`. Records are filtered by the authenticated owner so one user cannot read or change another user's pet data.

## 4. Database Design

```text
users 1 -------- many pets
users 1 -------- many appointments
pets  1 -------- many vaccination_records
pets  1 -------- many medical_records
pets  1 -------- many appointments

pet_breeds 1 --- many pets                 (optional classification)
vaccinations 1 - many vaccination_records (optional reference)
veterinarians 1- many medical_records      (optional reference)
veterinarians 1- many appointments         (optional reference)
```

Key design rules:

- Deleting a user removes the user's pets and appointments.
- Deleting a pet removes its linked health and appointment records.
- Pet ownership is checked before viewing, updating, or deleting a record.
- Vaccination due dates and appointment dates are indexed for reminder queries.
- Optional reference records use nullable foreign keys so manually entered clinic information is still supported.

## 5. Android Navigation Flow

```text
Launch
  |
  +-- No active session -> Sign in / Register
  |                         |
  |                         +-- Valid details -> Dashboard
  |
  +-- Active session -------------------------> Dashboard
                                                  |
                                                  +-- Home overview
                                                  +-- Pet profiles
                                                  +-- Health records
                                                  +-- Sign out -> Sign in
```

The dashboard uses a persistent top app bar and bottom navigation. Each destination updates the central content area while keeping the main navigation visible.

## 6. Current Screen Requirements

### Authentication

- Allow an owner to register using a name, email address, and password.
- Allow an existing owner to sign in.
- Show clear validation messages for invalid input.
- Restore an existing local session when the application is reopened.
- Allow the owner to sign out safely.

### Dashboard

- Greet the signed-in owner.
- Show totals for pets, upcoming vaccinations, appointments, and medical records.
- Show the nearest vaccination and appointment reminders.
- Provide quick access to common actions.

### Navigation

- Home opens the dashboard summary.
- Pets opens pet-profile management.
- Health opens vaccination, appointment, and medical-record management.
- The top app bar provides the sign-out action.

## 7. Security and Validation

- Passwords are never stored as plain text by Laravel.
- Local presentation-mode passwords are salted and hashed before storage.
- Email format and password length are checked before submission.
- Laravel performs server-side validation even when Android validation succeeds.
- Production deployments must use HTTPS and must not commit the `.env` file.
