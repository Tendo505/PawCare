package com.example.pawcareai.dashboard

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.example.pawcareai.R
import com.example.pawcareai.data.Appointment
import com.example.pawcareai.data.AppointmentReminder
import com.example.pawcareai.data.AppointmentReminderCalculator
import com.example.pawcareai.data.DashboardStats
import com.example.pawcareai.data.SystemHealth
import com.example.pawcareai.data.UserAccount
import com.example.pawcareai.data.VaccinationRecord
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

//the Home tab (layout: screen_home.xml): greeting, connection card, four totals, reminders and quick actions
class HomeScreen(
    private val page: DashboardPage,
    private val petsScreen: PetsScreen,
    private val healthScreen: HealthScreen
)
{
    private val views: ViewMaker = page.views

    //1.screen setup

    //draw the home screen
    fun render()
    {
        page.setTitle(R.string.app_name)
        val user: UserAccount? = page.repository.currentUser
        if (user == null)
        {
            //the account has not been restored yet, so there is nothing to greet
            return
        }

        val root: LinearLayout = page.inflate(R.layout.screen_home)
        val greeting: TextView = root.findViewById(R.id.homeGreeting)
        val firstName: String = user.name.substringBefore(' ')
        greeting.text = page.dashboard.getString(R.string.home_greeting, firstName)

        showConnection(root)
        showTotals(root)
        showReminders(root.findViewById(R.id.homeReminders))
        setQuickActions(root)
        page.show(root)
    }

    //2.input

    //the "Check again" button and the three quick action buttons
    private fun setQuickActions(root: LinearLayout)
    {
        val retryButton: MaterialButton = root.findViewById(R.id.connectionRetry)
        retryButton.setOnClickListener {
            refreshConnection()
        }

        val addPetButton: MaterialButton = root.findViewById(R.id.homeAddPet)
        addPetButton.setOnClickListener {
            petsScreen.showPetForm(null)
        }

        val bookVisitButton: MaterialButton = root.findViewById(R.id.homeBookVisit)
        bookVisitButton.setOnClickListener {
            healthScreen.showAppointmentForm(null)
        }

        val recognizeButton: MaterialButton = root.findViewById(R.id.homeRecognize)
        recognizeButton.setOnClickListener {
            page.openTab(R.id.nav_ai)
        }
    }

    //3.process

    //ask laravel for the service status (GET health), then redraw home if it is still on screen
    fun refreshConnection()
    {
        page.repository.checkSystemHealth { result ->
            if (page.isTabSelected(R.id.nav_home))
            {
                render()
            }
        }
    }

    //collect up to 3 upcoming appointments, then up to 2 vaccinations that are not completed
    private fun collectReminders(): List<MaterialCardView>
    {
        val reminderCards: MutableList<MaterialCardView> = mutableListOf()

        val upcoming: List<AppointmentReminder> = AppointmentReminderCalculator.upcoming(page.repository.appointments(), limit = 3)
        for (reminder in upcoming)
        {
            reminderCards.add(appointmentReminderCard(reminder))
        }

        var vaccinationCount: Int = 0
        for (vaccination in page.repository.vaccinations())
        {
            if (vaccination.status != "Completed" && vaccinationCount < 2)
            {
                reminderCards.add(vaccinationReminderCard(vaccination))
                vaccinationCount = vaccinationCount + 1
            }
        }
        return reminderCards
    }

    //4.output

    //the connection card: is laravel, the database and the ai service working?
    private fun showConnection(root: LinearLayout)
    {
        val health: SystemHealth? = page.repository.systemHealth()

        val title: TextView = root.findViewById(R.id.connectionTitle)
        val laravel: TextView = root.findViewById(R.id.connectionLaravel)
        val database: TextView = root.findViewById(R.id.connectionDatabase)
        val ai: TextView = root.findViewById(R.id.connectionAi)
        val model: TextView = root.findViewById(R.id.connectionModel)

        if (health == null)
        {
            //no answer yet, so every service is still "Checking"
            title.text = text(R.string.health_checking)
            laravel.text = page.dashboard.getString(R.string.health_laravel, serviceLabel(null))
            database.text = page.dashboard.getString(R.string.health_database, serviceLabel(null))
            ai.text = page.dashboard.getString(R.string.health_ai, serviceLabel(null))
            model.visibility = View.GONE
            return
        }

        title.text = text(overallMessage(health.overall))
        laravel.text = page.dashboard.getString(R.string.health_laravel, serviceLabel(health.laravel))
        database.text = page.dashboard.getString(R.string.health_database, serviceLabel(health.database))
        ai.text = page.dashboard.getString(R.string.health_ai, serviceLabel(health.ai))
        if (health.aiModelVersion.isBlank())
        {
            model.visibility = View.GONE
        }
        else
        {
            model.visibility = View.VISIBLE
        }
        model.text = page.dashboard.getString(R.string.health_model, health.aiModelVersion)
    }

    //the card title for the overall status sent by laravel
    private fun overallMessage(overall: String): Int
    {
        if (overall == "ok")
        {
            return R.string.health_all_connected
        }
        if (overall == "degraded")
        {
            return R.string.health_degraded
        }
        if (overall == "unavailable")
        {
            return R.string.health_unavailable
        }
        return R.string.health_checking
    }

    //a readable word for one service status, for example "ready" becomes "Connected"
    private fun serviceLabel(status: String?): String
    {
        var label: Int = R.string.service_checking
        if (status == "ok" || status == "ready")
        {
            label = R.string.service_connected
        }
        else if (status == "model_missing")
        {
            label = R.string.service_model_missing
        }
        else if (status == "unavailable")
        {
            label = R.string.service_unavailable
        }
        else if (status == "unknown")
        {
            label = R.string.service_not_confirmed
        }
        return text(label)
    }

    //the four numbers: pets, vaccinations due, scheduled visits and medical records
    private fun showTotals(root: LinearLayout)
    {
        val stats: DashboardStats = page.repository.stats()
        root.findViewById<TextView>(R.id.petStatValue).text = stats.petCount.toString()
        root.findViewById<TextView>(R.id.vaccineStatValue).text = stats.upcomingVaccinations.toString()
        root.findViewById<TextView>(R.id.appointmentStatValue).text = stats.scheduledAppointments.toString()
        root.findViewById<TextView>(R.id.medicalStatValue).text = stats.medicalRecordCount.toString()
    }

    //show each reminder card, or a message when there are none
    private fun showReminders(container: LinearLayout)
    {
        val reminderCards: List<MaterialCardView> = collectReminders()
        if (reminderCards.isEmpty())
        {
            views.addWithTopMargin(container, views.messageCard(text(R.string.reminders_empty_title), text(R.string.reminders_empty_message)), 10)
            return
        }
        for (card in reminderCards)
        {
            views.addWithTopMargin(container, card, 10)
        }
    }

    //"Appointment · Milo / Tomorrow at 10:00 / Wellness check · PawCare Clinic"
    private fun appointmentReminderCard(reminder: AppointmentReminder): MaterialCardView
    {
        val appointment: Appointment = reminder.appointment
        val petName: String = page.repository.petName(appointment.petId)
        val title: String = page.dashboard.getString(R.string.reminder_appointment, petName)

        val details: MutableList<String> = mutableListOf()
        if (appointment.reason.isNotBlank())
        {
            details.add(appointment.reason)
        }
        if (appointment.clinic.isNotBlank())
        {
            details.add(appointment.clinic)
        }
        return views.reminderCard(title, reminder.label, details.joinToString(" · "))
    }

    //"Vaccination · Milo / Due 3 Nov 2026 / Rabies"
    private fun vaccinationReminderCard(vaccination: VaccinationRecord): MaterialCardView
    {
        val petName: String = page.repository.petName(vaccination.petId)
        val title: String = page.dashboard.getString(R.string.reminder_vaccination, petName)
        val dueDate: String = page.textFormat.prettyDate(vaccination.dueDate)
        val date: String = page.dashboard.getString(R.string.reminder_due, dueDate)
        return views.reminderCard(title, date, vaccination.vaccineName)
    }

    //read a label from strings.xml
    private fun text(stringResource: Int): String
    {
        return page.dashboard.getString(stringResource)
    }
}
