package com.example.pawcareai.data

object DashboardCalculator
{
    // Calculate the dashboard totals from the current account's records.
    fun calculate(
        pets: List<Pet>,
        vaccinations: List<VaccinationRecord>,
        appointments: List<Appointment>,
        medicalRecords: List<MedicalRecord>
    ): DashboardStats
    {
        val petCount = pets.size
        val upcomingVaccinations = vaccinations.count { vaccination -> vaccination.status != "Completed" }
        val scheduledAppointments = appointments.count { appointment -> appointment.status == "Scheduled" }
        val medicalRecordCount = medicalRecords.size

        return DashboardStats(
            petCount = petCount,
            upcomingVaccinations = upcomingVaccinations,
            scheduledAppointments = scheduledAppointments,
            medicalRecordCount = medicalRecordCount
        )
    }
}
