package com.example.pawcareai.dashboard

import android.widget.LinearLayout
import android.widget.TextView
import com.example.pawcareai.R
import com.example.pawcareai.data.AppointmentReminderCalculator
import com.google.android.material.button.MaterialButton

//the home tab: greeting, connection status, care totals, reminders and quick actions
class HomeScreen(
    private val page: DashboardPage,
    private val petDialog: PetDialog,
    private val appointments: AppointmentsSection,
    private val systemHealthCard: SystemHealthCard
)
{
    private val context = page.activity

    //one reminder row: title, date and detail
    private class Reminder(val title: String, val date: String, val detail: String)

    //1.screen setup

    //display account details, care totals, reminders and quick actions
    fun render()
    {
        page.setTitle(context.getString(R.string.app_name))
        val user = page.repository.currentUser ?: return
        val root = page.inflate(R.layout.screen_home)
        root.findViewById<TextView>(R.id.homeGreeting).text = context.getString(R.string.home_greeting, user.name.substringBefore(' '))
        systemHealthCard.bind(root)
        showTotals(root)
        showReminders(root.findViewById(R.id.homeReminders))
        setQuickActions(root)
        page.show(root)
    }

    //2.input

    //connect the quick action buttons
    private fun setQuickActions(root: LinearLayout)
    {
        root.findViewById<MaterialButton>(R.id.homeAddPet).setOnClickListener { petDialog.show() }
        root.findViewById<MaterialButton>(R.id.homeBookVisit).setOnClickListener { appointments.showDialog() }
        root.findViewById<MaterialButton>(R.id.homeRecognize).setOnClickListener {
            page.openTab(R.id.nav_ai)
        }
    }

    //3.process

    //collect the next appointments, then vaccinations that are still due
    private fun collectReminders(): List<Reminder>
    {
        val repository = page.repository
        val reminders = mutableListOf<Reminder>()
        val upcomingAppointments = AppointmentReminderCalculator.upcoming(repository.appointments(), limit = 3)
        upcomingAppointments.forEach { reminder ->
            val appointment = reminder.appointment
            val appointmentDetails = listOf(appointment.reason, appointment.clinic)
                .filter { detail -> detail.isNotBlank() }
                .joinToString(" · ")
            val title = context.getString(R.string.reminder_appointment, repository.petName(appointment.petId))
            reminders += Reminder(title, reminder.label, appointmentDetails)
        }
        val dueVaccinations = repository.vaccinations()
            .filter { vaccination -> vaccination.status != "Completed" }
            .take(2)
        dueVaccinations.forEach { vaccination ->
            val title = context.getString(R.string.reminder_vaccination, repository.petName(vaccination.petId))
            val date = context.getString(R.string.reminder_due, context.prettyDate(vaccination.dueDate))
            reminders += Reminder(title, date, vaccination.vaccineName)
        }
        return reminders
    }

    //4.output

    //show the four dashboard totals
    private fun showTotals(root: LinearLayout)
    {
        val stats = page.repository.stats()
        root.findViewById<TextView>(R.id.petStatValue).text = stats.petCount.toString()
        root.findViewById<TextView>(R.id.vaccineStatValue).text = stats.upcomingVaccinations.toString()
        root.findViewById<TextView>(R.id.appointmentStatValue).text = stats.scheduledAppointments.toString()
        root.findViewById<TextView>(R.id.medicalStatValue).text = stats.medicalRecordCount.toString()
    }

    //show each reminder, or the empty message
    private fun showReminders(remindersContainer: LinearLayout)
    {
        val reminders = collectReminders()
        if (reminders.isEmpty())
        {
            val emptyTitle = context.getString(R.string.reminders_empty_title)
            val emptyMessage = context.getString(R.string.reminders_empty_message)
            remindersContainer.addView(context.emptyCard(emptyTitle, emptyMessage).withTopMargin(10))
        }
        else
        {
            reminders.forEach { reminder ->
                remindersContainer.addView(context.infoCard(reminder.title, reminder.date, reminder.detail).withTopMargin(10))
            }
        }
    }
}
