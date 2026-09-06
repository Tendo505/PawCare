package com.example.pawcareai.data

// Store the signed-in user's profile.
data class UserAccount(
    val id: Long = 0,
    val name: String,
    val email: String
)

// Store the connection status returned by the backend.
data class SystemHealth(
    val overall: String = "checking",
    val laravel: String = "checking",
    val database: String = "checking",
    val ai: String = "checking",
    val aiModelVersion: String = "",
    val checkedAt: String = ""
)

// Store a pet profile for display and editing.
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

// Store a pet's vaccination history and next due date.
data class VaccinationRecord(
    val id: Long = 0,
    val ownerEmail: String = "",
    val petId: Long = 0,
    val vaccineName: String = "",
    val administeredDate: String = "",
    val dueDate: String = "",
    val clinic: String = "",
    val status: String = "Upcoming",
    val notes: String = ""
)

// Store a pet's clinic visit and treatment details.
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

// Store an appointment and its current status.
data class Appointment(
    val id: Long = 0,
    val ownerEmail: String = "",
    val petId: Long = 0,
    val appointmentDate: String = "",
    val appointmentTime: String = "",
    val clinic: String = "",
    val reason: String = "",
    val status: String = "Scheduled",
    val notes: String = ""
)

// Store one possible breed and its confidence score.
data class BreedPredictionOption(
    val breed: String,
    val confidence: Double
)

// Store a saved scan result and its alternative breed predictions.
data class BreedPrediction(
    val id: Long = 0,
    val ownerEmail: String = "",
    val petId: Long? = null,
    val petName: String = "",
    val species: String = "",
    val breed: String = "",
    val confidence: Double = 0.0,
    val createdAt: String = "",
    val imageUrl: String = "",
    val modelVersion: String = "",
    val topPredictions: List<BreedPredictionOption> = emptyList(),
    val disclaimer: String = ""
)

// Store the totals shown on the dashboard.
data class DashboardStats(
    val petCount: Int,
    val upcomingVaccinations: Int,
    val scheduledAppointments: Int,
    val medicalRecordCount: Int
)
