package com.example.pawcareai.data

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
        val petCount = pets.size
        //count every vaccination not marked completed
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
