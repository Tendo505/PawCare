package com.example.pawcareai.dashboard

import android.net.Uri
import com.example.pawcareai.R

//creates every dashboard screen once and draws the one for the selected tab
class DashboardScreens(
    page: DashboardPage,
    choosePhoto: () -> Unit
)
{
    //1.screen setup

    //screens and dialogs, connected so each save redraws the same screen as before
    private val petDialog: PetDialog = PetDialog(page) { petsScreen.render() }
    private val vaccinations: VaccinationsSection = VaccinationsSection(page, petDialog) { healthScreen.render() }
    private val appointments: AppointmentsSection = AppointmentsSection(page, petDialog) { healthScreen.render() }
    private val medicalRecords: MedicalRecordsSection = MedicalRecordsSection(page, petDialog) { healthScreen.render() }
    private val systemHealthCard: SystemHealthCard = SystemHealthCard(page) { homeScreen.render() }
    private val homeScreen: HomeScreen = HomeScreen(page, petDialog, appointments, systemHealthCard)
    private val petsScreen: PetsScreen = PetsScreen(page, petDialog)
    private val healthScreen: HealthScreen = HealthScreen(page, vaccinations, appointments, medicalRecords)
    private val aiScanScreen: AiScanScreen = AiScanScreen(page, choosePhoto)

    //2.input

    //pass a chosen photo to the ai scan screen
    fun onPhotoSelected(uri: Uri)
    {
        aiScanScreen.onPhotoSelected(uri)
    }

    //3.process

    //update the connection status shown on the home screen
    fun refreshSystemHealth()
    {
        systemHealthCard.refresh()
    }

    //5.navigation

    //keep asynchronous refreshes on the tab the user selected
    fun render(itemId: Int)
    {
        when (itemId)
        {
            R.id.nav_home -> homeScreen.render()
            R.id.nav_pets -> petsScreen.render()
            R.id.nav_health -> healthScreen.render()
            R.id.nav_ai -> aiScanScreen.render()
        }
    }
}
