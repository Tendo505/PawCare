# User acceptance testing checklist

Use at least five representative pet owners. Record device model, Android version, tester ID, completion time, observed defects, and a 1–5 satisfaction score for every case.

| ID | Scenario | Expected result |
|---|---|---|
| UAT-01 | Register with valid details | Account is created and dashboard opens |
| UAT-02 | Enter an invalid email or short password | Clear validation prevents submission |
| UAT-03 | Add, edit, then delete a pet | Profile updates correctly; deletion asks for confirmation |
| UAT-04 | Add a future vaccination | Record appears under Health and dashboard due count increases |
| UAT-05 | Add a medical visit | Diagnosis and treatment appear newest-first |
| UAT-06 | Schedule and edit an appointment | Reminder shows correct pet, date, time, clinic, and reason |
| UAT-07 | Upload a supported pet photo with trained model | Top prediction, confidence, and alternatives appear |
| UAT-08 | Upload a non-image or oversized image | File is rejected with a useful message |
| UAT-09 | Attempt another owner's record through API | API returns 404 and no data is disclosed |
| UAT-10 | Sign out and sign in again | Session changes safely and saved records remain available |
| UAT-11 | Use the app without backend connectivity | Demo CRUD remains usable; AI explains service outage |
| UAT-12 | Read AI disclaimer | Tester understands result is not a medical diagnosis |

## Exit criteria

- 100% of critical cases UAT-01 through UAT-10 pass.
- No open crash, data-loss, authentication-bypass, or cross-owner data exposure defects.
- At least 80% of testers rate navigation and task completion 4/5 or higher.
- Breed model results include a documented held-out accuracy and confusion matrix; no unsupported medical claims appear in the UI.
