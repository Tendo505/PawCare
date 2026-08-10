package com.example.pawcareai.validation

import java.time.LocalDate
import java.time.format.DateTimeParseException

data class PetValidationResult(
    val nameError: String? = null,
    val birthDateError: String? = null,
    val weightError: String? = null
) {
    val isValid: Boolean
        get() = nameError == null && birthDateError == null && weightError == null

    val firstError: String?
        get() = nameError ?: birthDateError ?: weightError
}

object PetInputValidator {
    fun validate(
        name: String,
        birthDate: String,
        weightKg: String,
        today: LocalDate = LocalDate.now()
    ): PetValidationResult {
        val cleanName = name.trim()
        val cleanBirthDate = birthDate.trim()
        val cleanWeight = weightKg.trim()

        val parsedBirthDate = if (cleanBirthDate.isBlank()) {
            null
        } else {
            try {
                LocalDate.parse(cleanBirthDate)
            } catch (_: DateTimeParseException) {
                null
            }
        }

        val parsedWeight = cleanWeight.toDoubleOrNull()

        return PetValidationResult(
            nameError = if (cleanName.isBlank()) "Enter the pet's name." else null,
            birthDateError = when {
                cleanBirthDate.isBlank() -> null
                parsedBirthDate == null -> "Use YYYY-MM-DD for the birth date."
                parsedBirthDate.isAfter(today) -> "Birth date cannot be in the future."
                else -> null
            },
            weightError = when {
                cleanWeight.isBlank() -> null
                parsedWeight == null -> "Enter a valid weight."
                parsedWeight <= 0.0 -> "Weight must be greater than 0 kg."
                else -> null
            }
        )
    }
}
