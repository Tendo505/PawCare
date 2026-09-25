package com.example.pawcareai.data

import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

//store an appointment with the time remaining and its display message
data class AppointmentReminder(
    val appointment: Appointment,
    val daysUntil: Long,
    val label: String
)

object AppointmentReminderCalculator
{
    //1.calculate upcoming reminders
    fun upcoming(
        appointments: List<Appointment>,
        today: LocalDate = LocalDate.now(),
        limit: Int = 5
    ): List<AppointmentReminder>
    {
        val reminders = appointments.mapNotNull { appointment ->
            createReminder(appointment, today)
        }

        val orderedReminders = reminders.sortedWith(
            compareBy(
                { it.appointment.appointmentDate },
                { it.appointment.appointmentTime }
            )
        )
        val reminderLimit = limit.coerceAtLeast(0)

        return orderedReminders.take(reminderLimit)
    }

    //2.filter scheduled visits
    private fun createReminder(
        appointment: Appointment,
        today: LocalDate
    ): AppointmentReminder?
    {
        val scheduledAppointment = appointment.takeIf { it.status == "Scheduled" } ?: return null
        val appointmentDate = parseDate(scheduledAppointment.appointmentDate)
            ?.takeUnless { it.isBefore(today) } ?: return null

        //appointment date minus today
        val daysUntil = ChronoUnit.DAYS.between(today, appointmentDate)
        return AppointmentReminder(
            appointment = scheduledAppointment,
            daysUntil = daysUntil,
            label = createLabel(scheduledAppointment, daysUntil)
        )
    }

    //3.read a reminder label
    fun labelFor(
        appointment: Appointment,
        today: LocalDate = LocalDate.now()
    ): String?
    {
        val reminder = upcoming(listOf(appointment), today, 1).firstOrNull()
        return reminder?.label
    }

    //4.format the remaining time
    private fun createLabel(appointment: Appointment, daysUntil: Long): String
    {
        return when (daysUntil)
        {
            0L -> "Today at ${appointment.appointmentTime}"
            1L -> "Tomorrow at ${appointment.appointmentTime}"
            else -> "In $daysUntil days - ${appointment.appointmentDate} at ${appointment.appointmentTime}"
        }
    }

    //5.read a stored date
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
}
