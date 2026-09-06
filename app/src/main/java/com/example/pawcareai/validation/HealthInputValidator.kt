package com.example.pawcareai.validation

import java.time.LocalDate
import java.time.format.DateTimeParseException

data class VaccinationValidationResult(
    val vaccineNameError: String? = null,
    val administeredDateError: String? = null,
    val dueDateError: String? = null
)
{
    val isValid: Boolean
        get() = vaccineNameError == null &&
            administeredDateError == null &&
            dueDateError == null

    val firstError: String?
        get() = vaccineNameError ?: administeredDateError ?: dueDateError
}

data class MedicalRecordValidationResult(
    val visitDateError: String? = null,
    val diagnosisError: String? = null
)
{
    val isValid: Boolean
        get() = visitDateError == null && diagnosisError == null

    val firstError: String?
        get() = visitDateError ?: diagnosisError
}

object HealthInputValidator
{
    // Validate vaccination details and the order of the two dates.
    fun validateVaccination(
        vaccineName: String,
        administeredDate: String,
        dueDate: String,
        today: LocalDate = LocalDate.now()
    ): VaccinationValidationResult
    {
        val cleanName = vaccineName.trim()
        val cleanAdministeredDate = administeredDate.trim()
        val cleanDueDate = dueDate.trim()
        val administered = parseDate(cleanAdministeredDate)
        val due = parseDate(cleanDueDate)

        val vaccineNameError = "Enter the vaccine name.".takeIf { cleanName.isBlank() }
        val administeredDateError = when
        {
            cleanAdministeredDate.isBlank() -> null
            administered == null -> "Use YYYY-MM-DD for the administered date."
            administered.isAfter(today) -> "Administered date cannot be in the future."
            else -> null
        }
        val dueDateError = when
        {
            cleanDueDate.isBlank() -> "Select the next due date."
            due == null -> "Use YYYY-MM-DD for the next due date."
            administered != null && due.isBefore(administered) ->
                "Due date cannot be before the administered date."
            else -> null
        }

        return VaccinationValidationResult(
            vaccineNameError = vaccineNameError,
            administeredDateError = administeredDateError,
            dueDateError = dueDateError
        )
    }

    // Validate the clinic visit date and diagnosis before saving a medical record.
    fun validateMedicalRecord(
        visitDate: String,
        diagnosis: String,
        today: LocalDate = LocalDate.now()
    ): MedicalRecordValidationResult
    {
        val cleanVisitDate = visitDate.trim()
        val cleanDiagnosis = diagnosis.trim()
        val visit = parseDate(cleanVisitDate)

        val visitDateError = when
        {
            cleanVisitDate.isBlank() -> "Select the clinic visit date."
            visit == null -> "Use YYYY-MM-DD for the visit date."
            visit.isAfter(today) -> "Visit date cannot be in the future."
            else -> null
        }
        val diagnosisError = "Enter the diagnosis or visit outcome.".takeIf { cleanDiagnosis.isBlank() }

        return MedicalRecordValidationResult(
            visitDateError = visitDateError,
            diagnosisError = diagnosisError
        )
    }

    // Convert an optional date without throwing an error for invalid input.
    private fun parseDate(value: String): LocalDate?
    {
        value.takeUnless(String::isBlank) ?: return null

        return try
        {
            LocalDate.parse(value)
        }
        catch (_: DateTimeParseException)
        {
            null
        }
    }
}
