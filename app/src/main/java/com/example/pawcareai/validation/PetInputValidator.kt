package com.example.pawcareai.validation

import java.time.LocalDate
import java.time.format.DateTimeParseException

data class PetValidationResult(
    val nameError: String? = null,
    val birthDateError: String? = null,
    val weightError: String? = null
)
{
    val isValid: Boolean
        get() = nameError == null && birthDateError == null && weightError == null

    val firstError: String?
        get() = nameError ?: birthDateError ?: weightError
}

object PetInputValidator
{
    // Validate the pet details before creating or updating a profile.
    fun validate(
        name: String,
        birthDate: String,
        weightKg: String,
        today: LocalDate = LocalDate.now()
    ): PetValidationResult
    {
        val cleanName = name.trim()
        val cleanBirthDate = birthDate.trim()
        val cleanWeight = weightKg.trim()

        return PetValidationResult(
            nameError = "Enter the pet's name.".takeIf { cleanName.isBlank() },
            birthDateError = validateBirthDate(cleanBirthDate, today),
            weightError = validateWeight(cleanWeight)
        )
    }

    // Validate an optional birth date and reject future dates.
    private fun validateBirthDate(birthDate: String, today: LocalDate): String?
    {
        val parsedBirthDate = parseDate(birthDate)

        return when
        {
            birthDate.isBlank() -> null
            parsedBirthDate == null -> "Use YYYY-MM-DD for the birth date."
            parsedBirthDate.isAfter(today) -> "Birth date cannot be in the future."
            else -> null
        }
    }

    // Validate an optional weight in kilograms.
    private fun validateWeight(weight: String): String?
    {
        val parsedWeight = weight.toDoubleOrNull()

        return when
        {
            weight.isBlank() -> null
            parsedWeight == null -> "Enter a valid weight."
            parsedWeight <= 0.0 -> "Weight must be greater than 0 kg."
            else -> null
        }
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
