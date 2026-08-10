# Sprint 2 - Pet Profile Management

**Period:** 10 August to 16 August 2026  
**Goal:** Allow a signed-in pet owner to manage complete pet profiles on the Android application.

## User Stories and Acceptance Criteria

### US-01 - View pet profiles

As a pet owner, I want to view my pets so that I can quickly find their identity and care information.

- The Pets destination lists only profiles belonging to the signed-in owner.
- Each card shows the pet's name, species, breed, sex, weight, birth date, and microchip number when available.
- A helpful empty state is displayed when the owner has no pet profiles.

### US-02 - Add a pet profile

As a pet owner, I want to add a pet so that I can begin recording its care information.

- The owner can enter name, species, breed, sex, birth date, weight, microchip number, and care notes.
- Pet name is required.
- Birth date is optional, but it must use `YYYY-MM-DD` and cannot be in the future.
- Weight is optional, but it must be a positive number when entered.
- A valid profile appears in the pet list after it is saved.

### US-03 - Edit a pet profile

As a pet owner, I want to correct a pet's details so that the stored profile remains accurate.

- Selecting Edit opens the form with the existing values.
- Saving replaces the selected profile without creating a duplicate.

### US-04 - Delete a pet profile

As a pet owner, I want to remove a pet profile that is no longer needed.

- The application asks for confirmation before deletion.
- Confirming deletion removes the profile and its linked local health and appointment records.
- Cancelling leaves the profile unchanged.

## Screen and Navigation Design

```text
Dashboard
   -> Bottom navigation: Pets
      -> Pet profile list
         -> Add pet -> Validate -> Save -> Updated list
         -> Edit pet -> Validate -> Save -> Updated list
         -> Delete pet -> Confirm -> Updated list
```

The pet-profile list is displayed inside `DashboardActivity` so the bottom navigation and top app bar remain visible. The add and edit actions share the same form to keep the interface simple and consistent.

## Pet Data Structure

| Field | Type | Rule |
| --- | --- | --- |
| `id` | Long | Generated unique identifier |
| `ownerEmail` | String | Links the profile to the signed-in owner |
| `name` | String | Required |
| `species` | String | Dog or Cat |
| `breed` | String | Optional text |
| `sex` | String | Unknown, Female, or Male |
| `birthDate` | String | Optional ISO date (`YYYY-MM-DD`) |
| `weightKg` | Double | Optional positive value |
| `microchipNumber` | String | Optional text |
| `notes` | String | Optional care notes |

The current Android demonstration stores profiles locally through `AppRepository`. The existing `PawCareApi` interface already defines the create, read, update, and delete endpoints that will be connected during the later Laravel backend sprint.

## Implementation Record

| Project-plan task | Result |
| --- | --- |
| 3.1 Define pet-profile user stories and acceptance criteria | Completed in this document |
| 3.2 Design pet-profile screens and data structure | Completed in this document, `Models.kt`, and `DashboardActivity.kt` |
| 3.3 Develop add, edit, view, and delete functions | Completed in `DashboardActivity.kt` and `AppRepository.kt` |
| 3.4 Test pet-profile functions and conduct sprint review | Validation is covered by the manual checklist below |

## Sprint Review Checklist

- [ ] Sign in and open **Pets** from the bottom navigation.
- [ ] Confirm the empty state or existing pet list is displayed.
- [ ] Add a pet with all valid fields.
- [ ] Try saving without a name and confirm the validation message appears.
- [ ] Try an invalid or future birth date and confirm it is rejected.
- [ ] Try a zero, negative, or non-numeric weight and confirm it is rejected.
- [ ] Edit the saved pet and confirm the card is updated without duplication.
- [ ] Cancel a delete confirmation and confirm the pet remains.
- [ ] Confirm deletion and verify the pet is removed.
- [ ] Build the application successfully using Android Studio's bundled JDK.

## Sprint Boundary

This sprint covers only pet-profile management. Vaccination and medical records begin on 17 August, appointments and reminders begin on 24 August, and AI integration is scheduled for September.
