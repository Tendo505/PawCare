package com.example.pawcareai.data

object DashboardCalculator {
    fun calculate(
        pets: List<Pet>,
        vaccinations: List<VaccinationRecord>,
        appointments: List<Appointment>,
        medicalRecords: List<MedicalRecord>
    ): DashboardStats = DashboardStats(
        petCount = pets.size,
        upcomingVaccinations = vaccinations.count { it.status != "Completed" },
        scheduledAppointments = appointments.count { it.status == "Scheduled" },
        medicalRecordCount = medicalRecords.size
    )
}

