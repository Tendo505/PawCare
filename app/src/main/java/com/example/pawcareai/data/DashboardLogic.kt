package com.example.pawcareai.data

import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

//1.dashboard totals
object DashboardCalculator
{
    //1.calculate dashboard totals
    fun calculate(
        pets: List<Pet>,
        vaccinations: List<VaccinationRecord>,
        appointments: List<Appointment>,
        medicalRecords: List<MedicalRecord>
    ): DashboardStats
    {
        val petCount: Int = pets.size
        val medicalRecordCount: Int = medicalRecords.size

        //count every vaccination not marked completed
        var upcomingVaccinations: Int = 0
        for (vaccination in vaccinations)
        {
            if (vaccination.status != "Completed")
            {
                upcomingVaccinations = upcomingVaccinations + 1
            }
        }

        //count every appointment that is still scheduled
        var scheduledAppointments: Int = 0
        for (appointment in appointments)
        {
            if (appointment.status == "Scheduled")
            {
                scheduledAppointments = scheduledAppointments + 1
            }
        }

        return DashboardStats(
            petCount = petCount,
            upcomingVaccinations = upcomingVaccinations,
            scheduledAppointments = scheduledAppointments,
            medicalRecordCount = medicalRecordCount
        )
    }
}

//2.appointment reminders
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
        //keep only the appointments that need a reminder
        val reminders: MutableList<AppointmentReminder> = mutableListOf()
        for (appointment in appointments)
        {
            val reminder: AppointmentReminder? = createReminder(appointment, today)
            if (reminder != null)
            {
                reminders.add(reminder)
            }
        }

        //soonest first: by date, then by time on the same day
        val orderedReminders: List<AppointmentReminder> = reminders.sortedWith(
            compareBy(
                { reminder -> reminder.appointment.appointmentDate },
                { reminder -> reminder.appointment.appointmentTime }
            )
        )

        //a negative limit is treated as 0
        var reminderLimit: Int = limit
        if (reminderLimit < 0)
        {
            reminderLimit = 0
        }
        return orderedReminders.take(reminderLimit)
    }

    //2.filter scheduled visits
    private fun createReminder(
        appointment: Appointment,
        today: LocalDate
    ): AppointmentReminder?
    {
        //completed and cancelled visits do not need a reminder
        if (appointment.status != "Scheduled")
        {
            return null
        }

        //skip dates that cannot be read and visits that are already in the past
        val appointmentDate: LocalDate? = parseDate(appointment.appointmentDate)
        if (appointmentDate == null || appointmentDate.isBefore(today))
        {
            return null
        }

        //appointment date minus today
        val daysUntil: Long = ChronoUnit.DAYS.between(today, appointmentDate)
        return AppointmentReminder(
            appointment = appointment,
            daysUntil = daysUntil,
            label = createLabel(appointment, daysUntil)
        )
    }

    //3.read a reminder label
    fun labelFor(
        appointment: Appointment,
        today: LocalDate = LocalDate.now()
    ): String?
    {
        val reminders: List<AppointmentReminder> = upcoming(listOf(appointment), today, 1)
        if (reminders.isEmpty())
        {
            return null
        }
        return reminders[0].label
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
