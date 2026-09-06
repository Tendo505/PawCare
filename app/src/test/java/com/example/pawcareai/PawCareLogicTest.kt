package com.example.pawcareai

import com.example.pawcareai.data.Appointment
import com.example.pawcareai.data.AppointmentReminderCalculator
import com.example.pawcareai.data.DashboardCalculator
import com.example.pawcareai.data.MedicalRecord
import com.example.pawcareai.data.Pet
import com.example.pawcareai.data.VaccinationRecord
import com.example.pawcareai.validation.AppointmentInputValidator
import com.example.pawcareai.validation.AuthInputValidator
import com.example.pawcareai.validation.HealthInputValidator
import com.example.pawcareai.validation.PetInputValidator
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PawCareLogicTest
{
    private val today = LocalDate.of(2026, 8, 31)

    // Check required account details
    @Test
    fun registrationRequiresValidDetails()
    {
        val invalid = AuthInputValidator.validate("A", "wrong", "short", true)
        val valid = AuthInputValidator.validate("Aina Lee", "aina@example.com", "pawcare123", true)

        assertFalse(invalid.isValid)
        assertTrue(valid.isValid)
    }

    // Reject invalid email addresses and weak registration passwords
    @Test
    fun registrationRejectsMalformedEmailAndWeakPassword()
    {
        val malformedEmail = AuthInputValidator.validate(
            "Aina Lee",
            "aina..lee@example.com",
            "pawcare123",
            true
        )
        val weakPassword = AuthInputValidator.validate(
            "Aina Lee",
            "aina@example.com",
            "12345678",
            true
        )

        assertEquals("Enter a valid email address.", malformedEmail.emailError)
        assertEquals("Password must contain at least one letter.", weakPassword.passwordError)
    }

    // Reject impossible pet details
    @Test
    fun petRejectsFutureBirthDateAndInvalidWeight()
    {
        val result = PetInputValidator.validate("Milo", "2026-09-01", "zero", today)

        assertEquals("Birth date cannot be in the future.", result.birthDateError)
        assertEquals("Enter a valid weight.", result.weightError)
    }

    // Allow optional pet fields to remain empty
    @Test
    fun petAcceptsValidOptionalDetails()
    {
        val result = PetInputValidator.validate("Milo", "", "", today)

        assertTrue(result.isValid)
        assertNull(result.firstError)
    }

    // Reject a scheduled visit in the past
    @Test
    fun scheduledAppointmentCannotBeInPast()
    {
        val result = AppointmentInputValidator.validate(
            appointmentDate = "2026-08-30",
            appointmentTime = "09:30",
            reason = "Annual check-up",
            status = "Scheduled",
            today = today
        )

        assertEquals("A scheduled appointment cannot be in the past.", result.dateError)
    }

    // Check the vaccination date order
    @Test
    fun vaccinationDueDateCannotPrecedeAdministeredDate()
    {
        val result = HealthInputValidator.validateVaccination(
            vaccineName = "Rabies",
            administeredDate = "2026-08-20",
            dueDate = "2026-08-19",
            today = today
        )

        assertEquals("Due date cannot be before the administered date.", result.dueDateError)
    }

    // Exclude past and cancelled visits from reminders
    @Test
    fun remindersKeepOnlyUpcomingScheduledVisits()
    {
        val appointments = listOf(
            Appointment(id = 1, appointmentDate = "2026-09-01", appointmentTime = "10:00", status = "Scheduled"),
            Appointment(id = 2, appointmentDate = "2026-08-30", appointmentTime = "10:00", status = "Scheduled"),
            Appointment(id = 3, appointmentDate = "2026-09-02", appointmentTime = "10:00", status = "Cancelled")
        )

        val reminders = AppointmentReminderCalculator.upcoming(appointments, today)

        assertEquals(listOf(1L), reminders.map { it.appointment.id })
        assertEquals("Tomorrow at 10:00", reminders.single().label)
    }

    // Display the nearest visits first within the reminder limit
    @Test
    fun remindersAreSortedAndLimited()
    {
        val appointments = listOf(
            Appointment(id = 1, appointmentDate = "2026-09-03", appointmentTime = "10:00", status = "Scheduled"),
            Appointment(id = 2, appointmentDate = "2026-09-01", appointmentTime = "11:00", status = "Scheduled"),
            Appointment(id = 3, appointmentDate = "2026-09-01", appointmentTime = "09:00", status = "Scheduled")
        )

        val reminders = AppointmentReminderCalculator.upcoming(appointments, today, limit = 2)

        assertEquals(listOf(3L, 2L), reminders.map { it.appointment.id })
    }

    // Skip a reminder whose stored date cannot be read
    @Test
    fun invalidAppointmentDateIsIgnored()
    {
        val appointment = Appointment(
            id = 1,
            appointmentDate = "not-a-date",
            appointmentTime = "09:00",
            status = "Scheduled"
        )

        val reminders = AppointmentReminderCalculator.upcoming(listOf(appointment), today)

        assertTrue(reminders.isEmpty())
    }

    // Calculate totals from the current records and statuses
    @Test
    fun dashboardCalculatesCurrentTotals()
    {
        val stats = DashboardCalculator.calculate(
            pets = listOf(Pet(id = 1), Pet(id = 2)),
            vaccinations = listOf(
                VaccinationRecord(id = 1, status = "Upcoming"),
                VaccinationRecord(id = 2, status = "Completed")
            ),
            appointments = listOf(
                Appointment(id = 1, status = "Scheduled"),
                Appointment(id = 2, status = "Cancelled")
            ),
            medicalRecords = listOf(MedicalRecord(id = 1))
        )

        assertEquals(2, stats.petCount)
        assertEquals(1, stats.upcomingVaccinations)
        assertEquals(1, stats.scheduledAppointments)
        assertEquals(1, stats.medicalRecordCount)
    }
}
