package com.example.pawcareai.data

import android.content.Context
import com.google.gson.Gson
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID

class AppRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val gson = Gson()

    init {
        ensureDemoAccount()
    }

    val currentUser: UserAccount?
        get() {
            val email = preferences.getString(KEY_SESSION, null) ?: return null
            return users().firstOrNull { it.email.equals(email, ignoreCase = true) }
        }

    fun register(name: String, email: String, password: String): Result<UserAccount> {
        val cleanName = name.trim()
        val cleanEmail = email.trim().lowercase()
        if (cleanName.length < 2) return Result.failure(IllegalArgumentException("Enter your full name."))
        if (!EMAIL_REGEX.matches(cleanEmail)) return Result.failure(IllegalArgumentException("Enter a valid email address."))
        if (password.length < 8) return Result.failure(IllegalArgumentException("Password must contain at least 8 characters."))

        val accounts = users().toMutableList()
        if (accounts.any { it.email.equals(cleanEmail, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("An account already exists for this email."))
        }
        val salt = UUID.randomUUID().toString()
        val account = UserAccount(cleanName, cleanEmail, salt, hash(salt, password))
        accounts += account
        write(KEY_USERS, accounts)
        preferences.edit().putString(KEY_SESSION, cleanEmail).apply()
        return Result.success(account)
    }

    fun login(email: String, password: String): Result<UserAccount> {
        val account = users().firstOrNull { it.email.equals(email.trim(), ignoreCase = true) }
            ?: return Result.failure(IllegalArgumentException("Email or password is incorrect."))
        if (account.passwordHash != hash(account.passwordSalt, password)) {
            return Result.failure(IllegalArgumentException("Email or password is incorrect."))
        }
        preferences.edit().putString(KEY_SESSION, account.email).apply()
        if (account.email == DEMO_EMAIL) seedDemoData()
        return Result.success(account)
    }

    fun loginDemo(): UserAccount {
        val account = users().first { it.email == DEMO_EMAIL }
        preferences.edit().putString(KEY_SESSION, account.email).apply()
        seedDemoData()
        return account
    }

    fun logout() = preferences.edit().remove(KEY_SESSION).apply()

    fun pets(): List<Pet> {
        val owner = currentUser?.email ?: return emptyList()
        return allPets().filter { it.ownerEmail == owner }.sortedBy { it.name.lowercase() }
    }

    fun pet(id: Long): Pet? = pets().firstOrNull { it.id == id }

    fun savePet(pet: Pet): Pet {
        val owner = requireOwner()
        val items = allPets().toMutableList()
        val saved = pet.copy(id = pet.id.takeIf { it > 0 } ?: nextId(), ownerEmail = owner)
        val index = items.indexOfFirst { it.id == saved.id && it.ownerEmail == owner }
        if (index >= 0) items[index] = saved else items += saved
        write(KEY_PETS, items)
        return saved
    }

    fun deletePet(id: Long) {
        val owner = requireOwner()
        write(KEY_PETS, allPets().filterNot { it.id == id && it.ownerEmail == owner })
        write(KEY_VACCINATIONS, allVaccinations().filterNot { it.petId == id && it.ownerEmail == owner })
        write(KEY_RECORDS, allMedicalRecords().filterNot { it.petId == id && it.ownerEmail == owner })
        write(KEY_APPOINTMENTS, allAppointments().filterNot { it.petId == id && it.ownerEmail == owner })
    }

    fun vaccinations(): List<VaccinationRecord> {
        val owner = currentUser?.email ?: return emptyList()
        return allVaccinations().filter { it.ownerEmail == owner }.sortedBy { it.dueDate }
    }

    fun saveVaccination(record: VaccinationRecord): VaccinationRecord {
        val owner = requireOwner()
        val items = allVaccinations().toMutableList()
        val saved = record.copy(id = record.id.takeIf { it > 0 } ?: nextId(), ownerEmail = owner)
        val index = items.indexOfFirst { it.id == saved.id && it.ownerEmail == owner }
        if (index >= 0) items[index] = saved else items += saved
        write(KEY_VACCINATIONS, items)
        return saved
    }

    fun deleteVaccination(id: Long) = deleteByOwner(KEY_VACCINATIONS, allVaccinations(), id) { it.id to it.ownerEmail }

    fun medicalRecords(): List<MedicalRecord> {
        val owner = currentUser?.email ?: return emptyList()
        return allMedicalRecords().filter { it.ownerEmail == owner }.sortedByDescending { it.visitDate }
    }

    fun saveMedicalRecord(record: MedicalRecord): MedicalRecord {
        val owner = requireOwner()
        val items = allMedicalRecords().toMutableList()
        val saved = record.copy(id = record.id.takeIf { it > 0 } ?: nextId(), ownerEmail = owner)
        val index = items.indexOfFirst { it.id == saved.id && it.ownerEmail == owner }
        if (index >= 0) items[index] = saved else items += saved
        write(KEY_RECORDS, items)
        return saved
    }

    fun deleteMedicalRecord(id: Long) = deleteByOwner(KEY_RECORDS, allMedicalRecords(), id) { it.id to it.ownerEmail }

    fun appointments(): List<Appointment> {
        val owner = currentUser?.email ?: return emptyList()
        return allAppointments().filter { it.ownerEmail == owner }.sortedWith(compareBy({ it.appointmentDate }, { it.appointmentTime }))
    }

    fun saveAppointment(appointment: Appointment): Appointment {
        val owner = requireOwner()
        val items = allAppointments().toMutableList()
        val saved = appointment.copy(id = appointment.id.takeIf { it > 0 } ?: nextId(), ownerEmail = owner)
        val index = items.indexOfFirst { it.id == saved.id && it.ownerEmail == owner }
        if (index >= 0) items[index] = saved else items += saved
        write(KEY_APPOINTMENTS, items)
        return saved
    }

    fun deleteAppointment(id: Long) = deleteByOwner(KEY_APPOINTMENTS, allAppointments(), id) { it.id to it.ownerEmail }

    fun predictions(): List<BreedPrediction> {
        val owner = currentUser?.email ?: return emptyList()
        return allPredictions().filter { it.ownerEmail == owner }.sortedByDescending { it.createdAt }
    }

    fun savePrediction(prediction: BreedPrediction): BreedPrediction {
        val owner = requireOwner()
        val items = allPredictions().toMutableList()
        val saved = prediction.copy(id = nextId(), ownerEmail = owner)
        items += saved
        write(KEY_PREDICTIONS, items)
        return saved
    }

    fun stats(): DashboardStats = DashboardCalculator.calculate(
        pets = pets(),
        vaccinations = vaccinations(),
        appointments = appointments(),
        medicalRecords = medicalRecords()
    )

    fun petName(petId: Long): String = pet(petId)?.name ?: "Unknown pet"

    private fun ensureDemoAccount() {
        val accounts = users().toMutableList()
        if (accounts.none { it.email == DEMO_EMAIL }) {
            val salt = UUID.randomUUID().toString()
            accounts += UserAccount("Aina Rahman", DEMO_EMAIL, salt, hash(salt, DEMO_PASSWORD))
            write(KEY_USERS, accounts)
        }
    }

    private fun seedDemoData() {
        if (pets().isNotEmpty()) return
        val buddy = savePet(Pet(name = "Buddy", species = "Dog", breed = "Golden Retriever", sex = "Male", birthDate = "2022-04-16", weightKg = 28.4, microchipNumber = "MY-DOG-20481", notes = "Friendly; sensitive to chicken-based food."))
        val luna = savePet(Pet(name = "Luna", species = "Cat", breed = "British Shorthair", sex = "Female", birthDate = "2023-09-08", weightKg = 4.7, notes = "Indoor cat."))
        saveVaccination(VaccinationRecord(petId = buddy.id, vaccineName = "DHPP Booster", administeredDate = "2025-08-12", dueDate = LocalDate.now().plusDays(12).toString(), clinic = "Happy Tails Veterinary", status = "Upcoming"))
        saveVaccination(VaccinationRecord(petId = luna.id, vaccineName = "FVRCP", administeredDate = LocalDate.now().minusMonths(11).toString(), dueDate = LocalDate.now().plusMonths(1).toString(), clinic = "Paws & Claws Clinic", status = "Upcoming"))
        saveMedicalRecord(MedicalRecord(petId = buddy.id, visitDate = LocalDate.now().minusMonths(2).toString(), veterinarian = "Dr. Lim Wei", diagnosis = "Mild dermatitis", treatment = "Medicated shampoo for 14 days", notes = "Symptoms resolved."))
        saveAppointment(Appointment(petId = buddy.id, appointmentDate = LocalDate.now().plusDays(5).toString(), appointmentTime = "10:30", clinic = "Happy Tails Veterinary", reason = "Annual wellness examination"))
    }

    private fun users(): List<UserAccount> = gson.fromJson(preferences.getString(KEY_USERS, "[]"), Array<UserAccount>::class.java)?.toList().orEmpty()
    private fun allPets(): List<Pet> = gson.fromJson(preferences.getString(KEY_PETS, "[]"), Array<Pet>::class.java)?.toList().orEmpty()
    private fun allVaccinations(): List<VaccinationRecord> = gson.fromJson(preferences.getString(KEY_VACCINATIONS, "[]"), Array<VaccinationRecord>::class.java)?.toList().orEmpty()
    private fun allMedicalRecords(): List<MedicalRecord> = gson.fromJson(preferences.getString(KEY_RECORDS, "[]"), Array<MedicalRecord>::class.java)?.toList().orEmpty()
    private fun allAppointments(): List<Appointment> = gson.fromJson(preferences.getString(KEY_APPOINTMENTS, "[]"), Array<Appointment>::class.java)?.toList().orEmpty()
    private fun allPredictions(): List<BreedPrediction> = gson.fromJson(preferences.getString(KEY_PREDICTIONS, "[]"), Array<BreedPrediction>::class.java)?.toList().orEmpty()

    private fun write(key: String, value: Any) = preferences.edit().putString(key, gson.toJson(value)).apply()

    private fun <T> deleteByOwner(key: String, items: List<T>, id: Long, identity: (T) -> Pair<Long, String>) {
        val owner = requireOwner()
        write(key, items.filterNot { identity(it).first == id && identity(it).second == owner })
    }

    private fun nextId(): Long {
        val value = preferences.getLong(KEY_NEXT_ID, 100L) + 1L
        preferences.edit().putLong(KEY_NEXT_ID, value).apply()
        return value
    }

    private fun requireOwner(): String = currentUser?.email ?: error("A signed-in user is required")

    private fun hash(salt: String, password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest("$salt:$password".toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val DEMO_EMAIL = "demo@pawcare.my"
        const val DEMO_PASSWORD = "PawCare123"
        private const val PREFS = "pawcare_store_v1"
        private const val KEY_SESSION = "session_email"
        private const val KEY_USERS = "users"
        private const val KEY_PETS = "pets"
        private const val KEY_VACCINATIONS = "vaccinations"
        private const val KEY_RECORDS = "medical_records"
        private const val KEY_APPOINTMENTS = "appointments"
        private const val KEY_PREDICTIONS = "predictions"
        private const val KEY_NEXT_ID = "next_id"
        private val EMAIL_REGEX = Regex("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", RegexOption.IGNORE_CASE)
    }
}
