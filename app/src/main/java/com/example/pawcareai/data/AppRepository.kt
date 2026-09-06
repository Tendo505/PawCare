package com.example.pawcareai.data

import android.content.Context
import android.content.SharedPreferences
import com.example.pawcareai.network.ApiAiPrediction
import com.example.pawcareai.network.ApiAppointment
import com.example.pawcareai.network.ApiMedicalRecord
import com.example.pawcareai.network.ApiMessage
import com.example.pawcareai.network.ApiPet
import com.example.pawcareai.network.ApiSystemHealth
import com.example.pawcareai.network.ApiUser
import com.example.pawcareai.network.ApiVaccination
import com.example.pawcareai.network.AppointmentRequest
import com.example.pawcareai.network.AuthRequest
import com.example.pawcareai.network.MedicalRecordRequest
import com.example.pawcareai.network.NetworkModule
import com.example.pawcareai.network.PetRequest
import com.example.pawcareai.network.PawCareApi
import com.example.pawcareai.network.VaccinationRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ApiRequestException(
    message: String,
    val statusCode: Int,
    val fieldErrors: Map<String, String> = emptyMap()
) : IllegalStateException(message)

class AppRepository(context: Context)
{
    // Authentication settings and API connection
    private val preferences: SharedPreferences = context.applicationContext
        .getSharedPreferences(AUTH_PREFERENCES, Context.MODE_PRIVATE)
    private val api: PawCareApi = NetworkModule.backend

    // Current account and records loaded from PostgreSQL
    private var activeUser: UserAccount? = null
    private var petCache: List<Pet> = emptyList()
    private var vaccinationCache: List<VaccinationRecord> = emptyList()
    private var medicalRecordCache: List<MedicalRecord> = emptyList()
    private var appointmentCache: List<Appointment> = emptyList()
    private var predictionCache: List<BreedPrediction> = emptyList()
    private var systemHealthCache: SystemHealth? = null

    init
    {
        // Remove records from the old offline version
        val legacyPreferences: SharedPreferences = context.applicationContext
            .getSharedPreferences(LEGACY_DATA_PREFERENCES, Context.MODE_PRIVATE)
        val legacyEditor: SharedPreferences.Editor = legacyPreferences.edit()
        legacyEditor.clear()
        legacyEditor.apply()

        NetworkModule.setAuthToken(preferences.getString(KEY_TOKEN, null))
    }

    // Read the signed-in account
    val currentUser: UserAccount?
        get()
        {
            return activeUser
        }

    // Check whether an authentication token has been saved
    val hasSession: Boolean
        get()
        {
            val token: String? = preferences.getString(KEY_TOKEN, null)
            return !token.isNullOrBlank()
        }

    // Restore the account using its saved token
    fun restoreSession(onResult: (Result<UserAccount>) -> Unit)
    {
        if (!hasSession)
        {
            onResult(Result.failure(IllegalStateException("Please sign in.")))
            return
        }

        api.me().enqueueResult { result ->
            result.onSuccess { user ->
                activeUser = user.toUserAccount()
            }
            result.onFailure {
                clearSession()
            }

            val accountResult: Result<UserAccount> = result.map { user -> user.toUserAccount() }
            onResult(accountResult)
        }
    }

    // Create a new account in PostgreSQL
    fun register(name: String, email: String, password: String, onResult: (Result<UserAccount>) -> Unit)
    {
        val request: AuthRequest = AuthRequest(email.trim().lowercase(), password, name.trim())

        api.register(request).enqueueResult { result ->
            result.onSuccess { authentication ->
                acceptAuthentication(authentication.token, authentication.user)
            }

            val accountResult: Result<UserAccount> = result.map { authentication ->
                authentication.user.toUserAccount()
            }
            onResult(accountResult)
        }
    }

    // Sign in and save the returned token
    fun login(email: String, password: String, onResult: (Result<UserAccount>) -> Unit)
    {
        val request: AuthRequest = AuthRequest(email.trim().lowercase(), password)

        api.login(request).enqueueResult { result ->
            result.onSuccess { authentication ->
                acceptAuthentication(authentication.token, authentication.user)
            }

            val accountResult: Result<UserAccount> = result.map { authentication ->
                authentication.user.toUserAccount()
            }
            onResult(accountResult)
        }
    }

    // Sign out and clear the local session
    fun logout(onComplete: () -> Unit)
    {
        if (!hasSession)
        {
            clearSession()
            onComplete()
            return
        }
        api.logout().enqueue(object : Callback<ApiMessage>
        {
            override fun onResponse(
                call: Call<ApiMessage>,
                response: Response<ApiMessage>
            )
            {
                clearSession()
                onComplete()
            }

            override fun onFailure(
                call: Call<ApiMessage>,
                throwable: Throwable
            )
            {
                clearSession()
                onComplete()
            }
        })
    }

    // Read all records before replacing the current cache
    fun refreshData(onResult: (Result<Unit>) -> Unit)
    {
        api.pets().enqueueResult { result ->
            result.onSuccess { pets ->
                loadVaccinations(pets, onResult)
            }
            result.onFailure { error ->
                onResult(Result.failure(error))
            }
        }
    }

    // Read vaccination records
    private fun loadVaccinations(pets: List<ApiPet>, onResult: (Result<Unit>) -> Unit)
    {
        api.vaccinations().enqueueResult { result ->
            result.onSuccess { vaccinations ->
                loadMedicalRecords(pets, vaccinations, onResult)
            }
            result.onFailure { error ->
                onResult(Result.failure(error))
            }
        }
    }

    // Read medical records
    private fun loadMedicalRecords(
        pets: List<ApiPet>,
        vaccinations: List<ApiVaccination>,
        onResult: (Result<Unit>) -> Unit
    )
    {
        api.medicalRecords().enqueueResult { result ->
            result.onSuccess { medicalRecords ->
                loadAppointments(pets, vaccinations, medicalRecords, onResult)
            }
            result.onFailure { error ->
                onResult(Result.failure(error))
            }
        }
    }

    // Read appointments
    private fun loadAppointments(
        pets: List<ApiPet>,
        vaccinations: List<ApiVaccination>,
        medicalRecords: List<ApiMedicalRecord>,
        onResult: (Result<Unit>) -> Unit
    )
    {
        api.appointments().enqueueResult { result ->
            result.onSuccess { appointments ->
                loadPredictions(pets, vaccinations, medicalRecords, appointments, onResult)
            }
            result.onFailure { error ->
                onResult(Result.failure(error))
            }
        }
    }

    // Read prediction history and finish refreshing records
    private fun loadPredictions(
        pets: List<ApiPet>,
        vaccinations: List<ApiVaccination>,
        medicalRecords: List<ApiMedicalRecord>,
        appointments: List<ApiAppointment>,
        onResult: (Result<Unit>) -> Unit
    )
    {
        api.aiPredictions().enqueueResult { result ->
            result.onSuccess { predictions ->
                updateCache(pets, vaccinations, medicalRecords, appointments, predictions.data)
                onResult(Result.success(Unit))
            }
            result.onFailure { error ->
                onResult(Result.failure(error))
            }
        }
    }

    // Update the in-memory records after every request succeeds
    private fun updateCache(
        pets: List<ApiPet>,
        vaccinations: List<ApiVaccination>,
        medicalRecords: List<ApiMedicalRecord>,
        appointments: List<ApiAppointment>,
        predictions: List<ApiAiPrediction>
    )
    {
        petCache = pets.map { pet -> pet.toPet() }
        vaccinationCache = vaccinations.map { record -> record.toVaccinationRecord() }
        medicalRecordCache = medicalRecords.map { record -> record.toMedicalRecord() }
        appointmentCache = appointments.map { appointment -> appointment.toAppointment() }
        predictionCache = predictions.map { prediction -> prediction.toBreedPrediction() }
    }

    // Read pets in alphabetical order
    fun pets(): List<Pet>
    {
        return petCache.sortedBy { pet -> pet.name.lowercase() }
    }

    // Find a pet by its record ID
    fun pet(id: Long): Pet?
    {
        return petCache.firstOrNull { pet -> pet.id == id }
    }

    // Read vaccinations by due date
    fun vaccinations(): List<VaccinationRecord>
    {
        return vaccinationCache.sortedBy { record -> record.dueDate }
    }

    // Read the latest medical records first
    fun medicalRecords(): List<MedicalRecord>
    {
        return medicalRecordCache.sortedByDescending { record -> record.visitDate }
    }

    // Read appointments by date, then time
    fun appointments(): List<Appointment>
    {
        val appointmentOrder: Comparator<Appointment> = compareBy(
            { appointment -> appointment.appointmentDate },
            { appointment -> appointment.appointmentTime }
        )
        return appointmentCache.sortedWith(appointmentOrder)
    }

    // Read the latest predictions first
    fun predictions(): List<BreedPrediction>
    {
        return predictionCache.sortedByDescending { prediction -> prediction.createdAt }
    }

    // Read the most recent connection check
    fun systemHealth(): SystemHealth?
    {
        return systemHealthCache
    }

    // Check Laravel, PostgreSQL and AI connections
    fun checkSystemHealth(onResult: (Result<SystemHealth>) -> Unit)
    {
        api.systemHealth().enqueueResult { result ->
            val healthResult: Result<SystemHealth> = result.map { health -> health.toSystemHealth() }
            healthResult.onSuccess { health ->
                systemHealthCache = health
            }
            healthResult.onFailure {
                systemHealthCache = SystemHealth(
                    overall = "unavailable",
                    laravel = "unavailable",
                    database = "unknown",
                    ai = "unknown"
                )
            }
            onResult(healthResult)
        }
    }

    // Create or update a pet, then refresh its cached record
    fun savePet(pet: Pet, onResult: (Result<Pet>) -> Unit)
    {
        val saveCall: Call<ApiPet> = if (pet.id > 0)
        {
            api.updatePet(pet.id, pet.toRequest())
        }
        else
        {
            api.createPet(pet.toRequest())
        }

        saveCall.enqueueResult { result ->
            val petResult: Result<Pet> = result.map { savedPet -> savedPet.toPet() }
            petResult.onSuccess { savedPet ->
                petCache = petCache.replaceOrAdd(savedPet) { cachedPet -> cachedPet.id }
            }
            onResult(petResult)
        }
    }

    // Delete a pet and remove its related records from the cache
    fun deletePet(id: Long, onResult: (Result<Unit>) -> Unit)
    {
        api.deletePet(id).enqueueEmpty { result ->
            result.onSuccess {
                petCache = petCache.filterNot { pet -> pet.id == id }
                vaccinationCache = vaccinationCache.filterNot { record -> record.petId == id }
                medicalRecordCache = medicalRecordCache.filterNot { record -> record.petId == id }
                appointmentCache = appointmentCache.filterNot { appointment -> appointment.petId == id }
            }
            onResult(result)
        }
    }

    // Create or update a vaccination record
    fun saveVaccination(record: VaccinationRecord, onResult: (Result<VaccinationRecord>) -> Unit)
    {
        val saveCall: Call<ApiVaccination> = if (record.id > 0)
        {
            api.updateVaccination(record.id, record.toRequest())
        }
        else
        {
            api.createVaccination(record.toRequest())
        }

        saveCall.enqueueResult { result ->
            val vaccinationResult: Result<VaccinationRecord> = result.map { savedRecord ->
                savedRecord.toVaccinationRecord()
            }
            vaccinationResult.onSuccess { savedRecord ->
                vaccinationCache = vaccinationCache.replaceOrAdd(savedRecord) { cachedRecord ->
                    cachedRecord.id
                }
            }
            onResult(vaccinationResult)
        }
    }

    // Delete a vaccination record
    fun deleteVaccination(id: Long, onResult: (Result<Unit>) -> Unit)
    {
        api.deleteVaccination(id).enqueueEmpty { result ->
            result.onSuccess {
                vaccinationCache = vaccinationCache.filterNot { record -> record.id == id }
            }
            onResult(result)
        }
    }

    // Create or update a medical record
    fun saveMedicalRecord(record: MedicalRecord, onResult: (Result<MedicalRecord>) -> Unit)
    {
        val saveCall: Call<ApiMedicalRecord> = if (record.id > 0)
        {
            api.updateMedicalRecord(record.id, record.toRequest())
        }
        else
        {
            api.createMedicalRecord(record.toRequest())
        }

        saveCall.enqueueResult { result ->
            val medicalResult: Result<MedicalRecord> = result.map { savedRecord ->
                savedRecord.toMedicalRecord()
            }
            medicalResult.onSuccess { savedRecord ->
                medicalRecordCache = medicalRecordCache.replaceOrAdd(savedRecord) { cachedRecord ->
                    cachedRecord.id
                }
            }
            onResult(medicalResult)
        }
    }

    // Delete a medical record
    fun deleteMedicalRecord(id: Long, onResult: (Result<Unit>) -> Unit)
    {
        api.deleteMedicalRecord(id).enqueueEmpty { result ->
            result.onSuccess {
                medicalRecordCache = medicalRecordCache.filterNot { record -> record.id == id }
            }
            onResult(result)
        }
    }

    // Create or update an appointment
    fun saveAppointment(appointment: Appointment, onResult: (Result<Appointment>) -> Unit)
    {
        val saveCall: Call<ApiAppointment> = if (appointment.id > 0)
        {
            api.updateAppointment(appointment.id, appointment.toRequest())
        }
        else
        {
            api.createAppointment(appointment.toRequest())
        }

        saveCall.enqueueResult { result ->
            val appointmentResult: Result<Appointment> = result.map { savedAppointment ->
                savedAppointment.toAppointment()
            }
            appointmentResult.onSuccess { savedAppointment ->
                appointmentCache = appointmentCache.replaceOrAdd(savedAppointment) { cachedAppointment ->
                    cachedAppointment.id
                }
            }
            onResult(appointmentResult)
        }
    }

    // Delete an appointment
    fun deleteAppointment(id: Long, onResult: (Result<Unit>) -> Unit)
    {
        api.deleteAppointment(id).enqueueEmpty { result ->
            result.onSuccess {
                appointmentCache = appointmentCache.filterNot { appointment -> appointment.id == id }
            }
            onResult(result)
        }
    }

    // Upload a photo and cache its saved prediction
    fun analyzeBreed(
        imageBytes: ByteArray,
        mimeType: String,
        fileName: String,
        petId: Long?,
        onResult: (Result<BreedPrediction>) -> Unit
    )
    {
        val imageBody: RequestBody = imageBytes.toRequestBody(mimeType.toMediaType())
        val imagePart: MultipartBody.Part = MultipartBody.Part.createFormData("image", fileName, imageBody)
        val petPart: RequestBody? = petId?.toString()?.toRequestBody("text/plain".toMediaType())

        api.createAiPrediction(imagePart, petPart).enqueue(object : Callback<ApiAiPrediction>
        {
            override fun onResponse(call: Call<ApiAiPrediction>, response: Response<ApiAiPrediction>)
            {
                val prediction: ApiAiPrediction? = response.body()
                if (response.isSuccessful && prediction != null)
                {
                    val savedPrediction: BreedPrediction = prediction.toBreedPrediction()
                    predictionCache = predictionCache.replaceOrAdd(savedPrediction) { cachedPrediction ->
                        cachedPrediction.id
                    }
                    onResult(Result.success(savedPrediction))
                }
                else
                {
                    onResult(Result.failure(response.aiFailure()))
                }
            }

            override fun onFailure(call: Call<ApiAiPrediction>, throwable: Throwable)
            {
                onResult(Result.failure(throwable))
            }
        })
    }

    // Calculate dashboard totals from the loaded records
    fun stats(): DashboardStats
    {
        return DashboardCalculator.calculate(
            pets = pets(),
            vaccinations = vaccinations(),
            appointments = appointments(),
            medicalRecords = medicalRecords()
        )
    }

    // Read a pet name for record labels
    fun petName(petId: Long): String
    {
        return pet(petId)?.name ?: "Unknown pet"
    }

    // Save the session token and active account
    private fun acceptAuthentication(token: String, user: ApiUser)
    {
        val sessionEditor: SharedPreferences.Editor = preferences.edit()
        sessionEditor.putString(KEY_TOKEN, token)
        sessionEditor.apply()

        NetworkModule.setAuthToken(token)
        activeUser = user.toUserAccount()
    }

    // Remove the token and records belonging to the current session
    private fun clearSession()
    {
        val sessionEditor: SharedPreferences.Editor = preferences.edit()
        sessionEditor.remove(KEY_TOKEN)
        sessionEditor.apply()

        NetworkModule.setAuthToken(null)
        activeUser = null
        petCache = emptyList()
        vaccinationCache = emptyList()
        medicalRecordCache = emptyList()
        appointmentCache = emptyList()
        predictionCache = emptyList()
    }

    // Send a request and return its response body asynchronously
    private fun <T> Call<T>.enqueueResult(onResult: (Result<T>) -> Unit)
    {
        enqueue(object : Callback<T>
        {
            override fun onResponse(call: Call<T>, response: Response<T>)
            {
                val body: T? = response.body()
                if (response.isSuccessful && body != null)
                {
                    onResult(Result.success(body))
                }
                else
                {
                    onResult(Result.failure(response.apiFailure()))
                }
            }

            override fun onFailure(call: Call<T>, throwable: Throwable)
            {
                onResult(Result.failure(throwable))
            }
        })
    }

    // Send a request that does not return a response body
    private fun Call<Unit>.enqueueEmpty(onResult: (Result<Unit>) -> Unit)
    {
        enqueue(object : Callback<Unit>
        {
            override fun onResponse(call: Call<Unit>, response: Response<Unit>)
            {
                if (response.isSuccessful)
                {
                    onResult(Result.success(Unit))
                }
                else
                {
                    onResult(Result.failure(response.apiFailure()))
                }
            }

            override fun onFailure(call: Call<Unit>, throwable: Throwable)
            {
                onResult(Result.failure(throwable))
            }
        })
    }

    // Convert API account details for the app
    private fun ApiUser.toUserAccount(): UserAccount
    {
        return UserAccount(id = id, name = name, email = email)
    }

    // Convert an API pet record for the app
    private fun ApiPet.toPet(): Pet
    {
        return Pet(
            id = id,
            ownerEmail = activeUser?.email.orEmpty(),
            name = name,
            species = species,
            breed = breed.orEmpty(),
            sex = sex,
            birthDate = birthDate.orEmpty(),
            weightKg = weightKg ?: 0.0,
            microchipNumber = microchipNumber.orEmpty(),
            notes = notes.orEmpty()
        )
    }

    // Prepare pet details for saving
    private fun Pet.toRequest(): PetRequest
    {
        return PetRequest(
            name = name.trim(),
            species = species,
            breed = breed.trim().nullIfBlank(),
            sex = sex,
            birthDate = birthDate.trim().nullIfBlank(),
            weightKg = weightKg.takeIf { weight -> weight > 0 },
            microchipNumber = microchipNumber.trim().nullIfBlank(),
            notes = notes.trim().nullIfBlank()
        )
    }

    // Convert an API vaccination record for the app
    private fun ApiVaccination.toVaccinationRecord(): VaccinationRecord
    {
        return VaccinationRecord(
            id = id,
            ownerEmail = activeUser?.email.orEmpty(),
            petId = petId,
            vaccineName = vaccineName,
            administeredDate = administeredDate.orEmpty(),
            dueDate = dueDate,
            clinic = clinic.orEmpty(),
            status = status,
            notes = notes.orEmpty()
        )
    }

    // Prepare vaccination details for saving
    private fun VaccinationRecord.toRequest(): VaccinationRequest
    {
        return VaccinationRequest(
            petId = petId,
            vaccineName = vaccineName.trim(),
            administeredDate = administeredDate.trim().nullIfBlank(),
            dueDate = dueDate,
            clinic = clinic.trim().nullIfBlank(),
            status = status,
            notes = notes.trim().nullIfBlank()
        )
    }

    // Convert an API medical record for the app
    private fun ApiMedicalRecord.toMedicalRecord(): MedicalRecord
    {
        return MedicalRecord(
            id = id,
            ownerEmail = activeUser?.email.orEmpty(),
            petId = petId,
            visitDate = visitDate,
            veterinarian = veterinarian.orEmpty(),
            diagnosis = diagnosis,
            treatment = treatment.orEmpty(),
            notes = notes.orEmpty()
        )
    }

    // Prepare medical details for saving
    private fun MedicalRecord.toRequest(): MedicalRecordRequest
    {
        return MedicalRecordRequest(
            petId = petId,
            visitDate = visitDate,
            veterinarian = veterinarian.trim().nullIfBlank(),
            diagnosis = diagnosis.trim(),
            treatment = treatment.trim().nullIfBlank(),
            notes = notes.trim().nullIfBlank()
        )
    }

    // Convert an API appointment for the app
    private fun ApiAppointment.toAppointment(): Appointment
    {
        return Appointment(
            id = id,
            ownerEmail = activeUser?.email.orEmpty(),
            petId = petId,
            appointmentDate = appointmentDate,
            appointmentTime = appointmentTime.take(5),
            clinic = clinic.orEmpty(),
            reason = reason,
            status = status,
            notes = notes.orEmpty()
        )
    }

    // Prepare appointment details for saving
    private fun Appointment.toRequest(): AppointmentRequest
    {
        return AppointmentRequest(
            petId = petId,
            appointmentDate = appointmentDate,
            appointmentTime = appointmentTime.take(5),
            clinic = clinic.trim().nullIfBlank(),
            reason = reason.trim(),
            status = status,
            notes = notes.trim().nullIfBlank()
        )
    }

    // Convert a saved API prediction for the app
    private fun ApiAiPrediction.toBreedPrediction(): BreedPrediction
    {
        return BreedPrediction(
            id = id,
            ownerEmail = activeUser?.email.orEmpty(),
            petId = petId,
            petName = pet?.name ?: petId?.let(::petName).orEmpty(),
            species = species,
            breed = breed,
            confidence = confidence,
            createdAt = createdAt,
            imageUrl = imageUrl.orEmpty(),
            modelVersion = modelVersion,
            topPredictions = topPredictions.map { prediction ->
                BreedPredictionOption(prediction.breed, prediction.confidence)
            },
            disclaimer = disclaimer
        )
    }

    // Convert service connection details for the dashboard
    private fun ApiSystemHealth.toSystemHealth(): SystemHealth
    {
        return SystemHealth(
            overall = status,
            laravel = services.laravel.status,
            database = services.database.status,
            ai = services.ai.status,
            aiModelVersion = services.ai.modelVersion.orEmpty(),
            checkedAt = checkedAt
        )
    }

    // Read an error returned by breed recognition
    private fun <T> Response<T>.aiFailure(): Throwable
    {
        return apiFailure("Breed recognition failed with HTTP ${code()}.")
    }

    // Read server validation messages and field errors
    private fun <T> Response<T>.apiFailure(
        fallback: String = "The server could not complete this request (HTTP ${code()})."
    ): Throwable
    {
        val responseText: String = errorBody()?.string().orEmpty()

        return runCatching {
            val payload: JSONObject = JSONObject(responseText)
            val validationErrors: JSONObject? = payload.optJSONObject("errors")
            val fieldErrors: MutableMap<String, String> = mutableMapOf()

            validationErrors?.keys()?.forEach { field ->
                val messages: JSONArray? = validationErrors.optJSONArray(field)
                val firstMessage: String = messages?.optString(0).orEmpty()

                if (firstMessage.isNotBlank())
                {
                    fieldErrors[field] = firstMessage
                }
            }

            val message: String = payload.optString("message")
                .ifBlank { fieldErrors.values.firstOrNull().orEmpty() }
                .ifBlank { fallback }

            ApiRequestException(message, code(), fieldErrors)
        }.getOrElse {
            ApiRequestException(fallback, code())
        }
    }

    // Send empty optional fields as null
    private fun String.nullIfBlank(): String?
    {
        return ifBlank { null }
    }

    // Replace a cached record or append a newly created record
    private fun <T> List<T>.replaceOrAdd(value: T, id: (T) -> Long): List<T>
    {
        val recordIndex: Int = indexOfFirst { record -> id(record) == id(value) }

        if (recordIndex < 0)
        {
            return this + value
        }

        return toMutableList().apply {
            this[recordIndex] = value
        }
    }

    // Preference names used by the authentication session
    companion object
    {
        private const val AUTH_PREFERENCES = "pawcare_auth"
        private const val LEGACY_DATA_PREFERENCES = "pawcare_store_v1"
        private const val KEY_TOKEN = "sanctum_token"
    }
}
