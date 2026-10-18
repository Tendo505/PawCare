package com.example.pawcareai.dashboard

import com.example.pawcareai.R
import com.google.android.material.button.MaterialButton

//the health tab: vaccinations, appointments and medical records
class HealthScreen(
    private val page: DashboardPage,
    private val vaccinations: VaccinationsSection,
    private val appointments: AppointmentsSection,
    private val medicalRecords: MedicalRecordsSection
)
{
    //1.screen setup

    //display vaccination, appointment and medical record sections
    fun render()
    {
        page.setTitle(page.activity.getString(R.string.title_health))
        val root = page.inflate(R.layout.screen_health)
        root.findViewById<MaterialButton>(R.id.healthAddVaccine).setOnClickListener { vaccinations.showDialog() }
        root.findViewById<MaterialButton>(R.id.healthBookVisit).setOnClickListener { appointments.showDialog() }
        root.findViewById<MaterialButton>(R.id.healthAddRecord).setOnClickListener { medicalRecords.showDialog() }

        //4.output
        vaccinations.showList(root.findViewById(R.id.vaccinationList))
        appointments.showList(root.findViewById(R.id.appointmentList))
        medicalRecords.showList(root.findViewById(R.id.medicalList))
        page.show(root)
    }
}
