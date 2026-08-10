# Sprint 1 - Android Application Foundation

**Period:** 3 August to 9 August 2026  
**Goal:** Deliver a usable authentication, navigation, and dashboard foundation.

## User Stories

1. As a pet owner, I want to register so that I can create my own PawCare account.
2. As a registered owner, I want to sign in so that I can access my pet information.
3. As a returning owner, I want my session restored so that I do not sign in repeatedly.
4. As a signed-in owner, I want a dashboard summary so that I can see important pet-care information quickly.
5. As a signed-in owner, I want consistent navigation so that I can move between the main modules.
6. As a signed-in owner, I want to sign out so that another person cannot use my session.

## Acceptance Criteria

- Registration requires a name of at least two characters, a valid email address, and a password of at least eight characters.
- Login rejects an invalid email format, a short password, and incorrect credentials.
- Successful registration or login opens the dashboard.
- An existing session opens the dashboard when the app restarts.
- The dashboard shows pet, vaccination, appointment, and medical-record totals.
- Bottom navigation opens the available main sections.
- Sign out clears the session and returns to the login screen.
- Authentication validation and dashboard calculations can be checked using the manual review checklist.

## Implementation Record

| Task | Result |
| --- | --- |
| Refine authentication, navigation, and dashboard requirements | Completed in this document |
| Design Android screens and navigation flow | Completed in `docs/ARCHITECTURE.md` and XML layouts |
| Develop authentication functions | Completed in `MainActivity`, `AuthInputValidator`, and `AppRepository` |
| Develop navigation and dashboard functions | Completed in `DashboardActivity` and `DashboardCalculator` |
| Test the application foundation | Covered by the manual review checklist and Android build verification |

## Sprint Review Checklist

- [ ] Register a new account on the emulator.
- [ ] Sign out and sign in with the new account.
- [ ] Confirm invalid fields display the correct messages.
- [ ] Confirm the demo account opens a populated dashboard.
- [ ] Open every bottom-navigation destination.
- [ ] Sign out and confirm the login screen appears.
- [ ] Build the application successfully from Android Studio.
