package com.example.pawcareai.data

data class UserAccount(
    val name: String,
    val email: String,
    val passwordSalt: String,
    val passwordHash: String
)

data class Pet(
    val id: Long = 0,
    val ownerEmail: String = "",
    val name: String = "",
    val species: String = "Dog",
    val breed: String = "",
    val sex: String = "Unknown",
    val birthDate: String = "",
    val weightKg: Double = 0.0,
    val microchipNumber: String = "",
    val notes: String = ""
)

data class VaccinationRecord(
    val id: Long = 0,
    val ownerEmail: String = "",
    val petId: Long = 0,
    val vaccineName: String = "",
    val administeredDate: String = "",
    val dueDate: String = "",
    val clinic: String = "",
    val status: String = "Upcoming"
)

data class MedicalRecord(
    val id: Long = 0,
    val ownerEmail: String = "",
    val petId: Long = 0,
    val visitDate: String = "",
    val veterinarian: String = "",
    val diagnosis: String = "",
    val treatment: String = "",
    val notes: String = ""
)

data class Appointment(
    val id: Long = 0,
    val ownerEmail: String = "",
    val petId: Long = 0,
    val appointmentDate: String = "",
    val appointmentTime: String = "",
    val clinic: String = "",
    val reason: String = "",
    val status: String = "Scheduled"
)

data class BreedPrediction(
    val id: Long = 0,
    val ownerEmail: String = "",
    val petId: Long? = null,
    val species: String = "",
    val breed: String = "",
    val confidence: Double = 0.0,
    val createdAt: String = "",
    val imageUri: String = ""
)

data class DashboardStats(
    val petCount: Int,
    val upcomingVaccinations: Int,
    val scheduledAppointments: Int,
    val medicalRecordCount: Int
)
