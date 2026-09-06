package com.example.pawcareai.validation

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

data class AppointmentValidationResult(
    val dateError: String? = null,
    val timeError: String? = null,
    val reasonError: String? = null
)
{
    val isValid: Boolean
        get() = dateError == null && timeError == null && reasonError == null

    // Get the first message to display beside the appointment form.
    fun firstError(): String?
    {
        return dateError ?: timeError ?: reasonError
    }
}

object AppointmentInputValidator
{
    // Validate the appointment date, time, and reason before saving.
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

    // Convert the entered date without throwing an error for invalid input.
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

    // Convert the entered 24-hour time without throwing an error for invalid input.
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
