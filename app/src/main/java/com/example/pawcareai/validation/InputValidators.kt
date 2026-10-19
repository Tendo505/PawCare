package com.example.pawcareai.validation

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

//1.auth validation

data class AuthValidationResult(
    val nameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null
)
{
    //true when no field has an error
    val isValid: Boolean
        get()
        {
            return nameError == null && emailError == null && passwordError == null
        }
}

object AuthInputValidator
{
    private const val MIN_PASSWORD_LENGTH = 8
    private const val MAX_EMAIL_LENGTH = 254
    private val emailPattern = Regex(
        "^[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?(?:\\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+$",
        RegexOption.IGNORE_CASE
    )

    //validate account input
    fun validate(
        name: String,
        email: String,
        password: String,
        registerMode: Boolean
    ): AuthValidationResult
    {
        val cleanName = name.trim()
        val cleanEmail = email.trim()

        return AuthValidationResult(
            nameError = validateName(cleanName, registerMode),
            emailError = validateEmail(cleanEmail),
            passwordError = validatePassword(password, registerMode)
        )
    }

    //validate the name when the user creates an account
    private fun validateName(name: String, registerMode: Boolean): String?
    {
        return when
        {
            registerMode && name.length < 2 -> "Enter your full name."
            else -> null
        }
    }

    //validate the email format, including spaces and misplaced dots
    private fun validateEmail(email: String): String?
    {
        val localPart = email.substringBefore('@')
        val hasInvalidDots = localPart.startsWith('.') || localPart.endsWith('.') || localPart.contains("..")

        return when
        {
            email.isEmpty() -> "Enter your email address."
            email.length > MAX_EMAIL_LENGTH -> "Email address is too long."
            hasSpace(email) -> "Email address cannot contain spaces."
            hasInvalidDots -> "Enter a valid email address."
            !emailPattern.matches(email) -> "Enter a valid email address."
            else -> null
        }
    }

    //check password strength only when registering
    private fun validatePassword(password: String, registerMode: Boolean): String?
    {
        return when
        {
            password.isBlank() -> "Enter your password."
            registerMode && password.length < MIN_PASSWORD_LENGTH -> "Password must contain at least 8 characters."
            registerMode && !hasLetter(password) -> "Password must contain at least one letter."
            registerMode && !hasDigit(password) -> "Password must contain at least one number."
            else -> null
        }
    }

    //true when the text contains a space, tab or line break
    private fun hasSpace(text: String): Boolean
    {
        for (character in text)
        {
            if (character.isWhitespace())
            {
                return true
            }
        }
        return false
    }

    //true when the text contains at least one letter
    private fun hasLetter(text: String): Boolean
    {
        for (character in text)
        {
            if (character.isLetter())
            {
                return true
            }
        }
        return false
    }

    //true when the text contains at least one number
    private fun hasDigit(text: String): Boolean
    {
        for (character in text)
        {
            if (character.isDigit())
            {
                return true
            }
        }
        return false
    }
}

//2.pet validation

data class PetValidationResult(
    val nameError: String? = null,
    val birthDateError: String? = null,
    val weightError: String? = null
)
{
    //true when no field has an error
    val isValid: Boolean
        get()
        {
            return nameError == null && birthDateError == null && weightError == null
        }

    //the first error from top to bottom of the form, shown in a toast
    val firstError: String?
        get()
        {
            if (nameError != null)
            {
                return nameError
            }
            if (birthDateError != null)
            {
                return birthDateError
            }
            return weightError
        }
}

object PetInputValidator
{
    //validate the pet details before creating or updating a profile
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

        var nameError: String? = null
        if (cleanName.isBlank())
        {
            nameError = "Enter the pet's name."
        }

        return PetValidationResult(
            nameError = nameError,
            birthDateError = validateBirthDate(cleanBirthDate, today),
            weightError = validateWeight(cleanWeight)
        )
    }

    //validate an optional birth date and reject future dates
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

    //validate an optional weight in kilograms
    private fun validateWeight(weight: String): String?
    {
        val parsedWeight = weight.toDoubleOrNull()

        return when
        {
            weight.isBlank() -> null
            parsedWeight == null || !parsedWeight.isFinite() -> "Enter a valid weight."
            parsedWeight <= 0.0 -> "Weight must be greater than 0 kg."
            parsedWeight > 300.0 -> "Weight cannot exceed 300 kg."
            else -> null
        }
    }

}

//3.health validation

data class VaccinationValidationResult(
    val vaccineNameError: String? = null,
    val administeredDateError: String? = null,
    val dueDateError: String? = null
)
{
    //true when no field has an error
    val isValid: Boolean
        get()
        {
            return vaccineNameError == null && administeredDateError == null && dueDateError == null
        }

    //the first error from top to bottom of the form, shown in a toast
    val firstError: String?
        get()
        {
            if (vaccineNameError != null)
            {
                return vaccineNameError
            }
            if (administeredDateError != null)
            {
                return administeredDateError
            }
            return dueDateError
        }
}

data class MedicalRecordValidationResult(
    val visitDateError: String? = null,
    val diagnosisError: String? = null
)
{
    //true when no field has an error
    val isValid: Boolean
        get()
        {
            return visitDateError == null && diagnosisError == null
        }

    //the first error from top to bottom of the form, shown in a toast
    val firstError: String?
        get()
        {
            if (visitDateError != null)
            {
                return visitDateError
            }
            return diagnosisError
        }
}

object HealthInputValidator
{
    //validate vaccination details and the order of the two dates
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

        var vaccineNameError: String? = null
        if (cleanName.isBlank())
        {
            vaccineNameError = "Enter the vaccine name."
        }
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

    //validate the clinic visit date and diagnosis before saving a medical record
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
        var diagnosisError: String? = null
        if (cleanDiagnosis.isBlank())
        {
            diagnosisError = "Enter the diagnosis or visit outcome."
        }

        return MedicalRecordValidationResult(
            visitDateError = visitDateError,
            diagnosisError = diagnosisError
        )
    }

}

//4.appointment validation

data class AppointmentValidationResult(
    val dateError: String? = null,
    val timeError: String? = null,
    val reasonError: String? = null
)
{
    //true when no field has an error
    val isValid: Boolean
        get()
        {
            return dateError == null && timeError == null && reasonError == null
        }

    //get the first message to display beside the appointment form
    fun firstError(): String?
    {
        if (dateError != null)
        {
            return dateError
        }
        if (timeError != null)
        {
            return timeError
        }
        return reasonError
    }
}

object AppointmentInputValidator
{
    //validate the appointment date, time, and reason before saving
    fun validate(
        appointmentDate: String,
        appointmentTime: String,
        reason: String,
        status: String,
        today: LocalDate = LocalDate.now()
    ): AppointmentValidationResult
    {
        val cleanDate = appointmentDate.trim()
        val cleanTime = appointmentTime.trim()
        val cleanReason = reason.trim()

        val parsedDate = parseDate(cleanDate)
        val parsedTime = parseTime(cleanTime)

        val dateError = when
        {
            cleanDate.isBlank() -> "Appointment date is required."
            parsedDate == null -> "Use a valid date in YYYY-MM-DD format."
            status == "Scheduled" && parsedDate.isBefore(today) ->
                "A scheduled appointment cannot be in the past."
            else -> null
        }

        val timeError = when
        {
            cleanTime.isBlank() -> "Appointment time is required."
            parsedTime == null -> "Use a valid 24-hour time such as 09:30."
            else -> null
        }

        val reasonError = when
        {
            cleanReason.isBlank() -> "Reason for visit is required."
            cleanReason.length > 500 -> "Reason must be 500 characters or fewer."
            else -> null
        }

        return AppointmentValidationResult(
            dateError = dateError,
            timeError = timeError,
            reasonError = reasonError
        )
    }

    //return null for an invalid time
    private fun parseTime(value: String): LocalTime?
    {
        return try
        {
            LocalTime.parse(value)
        }
        catch (_: DateTimeParseException)
        {
            null
        }
    }
}

//5.shared date parsing
private fun parseDate(value: String): LocalDate?
{
    return try
    {
        LocalDate.parse(value)
    }
    catch (_: DateTimeParseException)
    {
        null
    }
}
