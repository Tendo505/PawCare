package com.example.pawcareai.network

import com.example.pawcareai.BuildConfig
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import okhttp3.RequestBody

// Authentication details
data class AuthRequest(
    val email: String,
    val password: String,
    val name: String? = null
)

data class AuthResponse(
    val token: String,
    val user: ApiUser
)

data class ApiUser(
    val id: Long,
    val name: String,
    val email: String
)

data class ApiMessage(val message: String)

// Service connection details
data class ApiLaravelHealth(val status: String, val version: String = "")
data class ApiDatabaseHealth(val status: String, val driver: String = "")
data class ApiAiHealth(
    val status: String,
    val modelReady: Boolean = false,
    val modelVersion: String? = null,
    val labelCount: Int = 0
)
data class ApiServiceHealth(
    val laravel: ApiLaravelHealth,
    val database: ApiDatabaseHealth,
    val ai: ApiAiHealth
)
data class ApiSystemHealth(
    val status: String,
    val services: ApiServiceHealth,
    val checkedAt: String = ""
)

// Pet records
data class ApiPet(
    val id: Long,
    val name: String,
    val species: String,
    val breed: String? = null,
    val sex: String = "Unknown",
    val birthDate: String? = null,
    val weightKg: Double? = null,
    val microchipNumber: String? = null,
    val notes: String? = null
)

data class PetRequest(
    val name: String,
    val species: String,
    val breed: String? = null,
    val sex: String,
    val birthDate: String? = null,
    val weightKg: Double? = null,
    val microchipNumber: String? = null,
    val notes: String? = null
)

// Vaccination records
data class ApiVaccination(
    val id: Long,
    val petId: Long,
    val vaccineName: String,
    val administeredDate: String? = null,
    val dueDate: String,
    val clinic: String? = null,
    val status: String,
    val notes: String? = null
)

data class VaccinationRequest(
    val petId: Long,
    val vaccineName: String,
    val administeredDate: String? = null,
    val dueDate: String,
    val clinic: String? = null,
    val status: String,
    val notes: String? = null
)

// Medical records
data class ApiMedicalRecord(
    val id: Long,
    val petId: Long,
    val visitDate: String,
    val veterinarian: String? = null,
    val diagnosis: String,
    val treatment: String? = null,
    val notes: String? = null
)

data class MedicalRecordRequest(
    val petId: Long,
    val visitDate: String,
    val veterinarian: String? = null,
    val diagnosis: String,
    val treatment: String? = null,
    val notes: String? = null
)

// Appointment records
data class ApiAppointment(
    val id: Long,
    val petId: Long,
    val appointmentDate: String,
    val appointmentTime: String,
    val clinic: String? = null,
    val reason: String,
    val status: String,
    val notes: String? = null
)

data class AppointmentRequest(
    val petId: Long,
    val appointmentDate: String,
    val appointmentTime: String,
    val clinic: String? = null,
    val reason: String,
    val status: String,
    val notes: String? = null
)

// Breed prediction details
data class ApiPredictionItem(
    val breed: String,
    val confidence: Double
)

data class ApiPredictionPet(
    val id: Long,
    val name: String
)

data class ApiAiPrediction(
    val id: Long,
    val petId: Long? = null,
    val species: String,
    val breed: String,
    val confidence: Double,
    val topPredictions: List<ApiPredictionItem> = emptyList(),
    val modelVersion: String = "",
    val imageUrl: String? = null,
    val disclaimer: String = "",
    val createdAt: String = "",
    val pet: ApiPredictionPet? = null
)
data class ApiAiPredictionPage(val data: List<ApiAiPrediction> = emptyList())

interface PawCareApi
{
    // Read service status
    @GET("health")
    fun systemHealth(): Call<ApiSystemHealth>

    // Create account
    @POST("register")
    fun register(@Body request: AuthRequest): Call<AuthResponse>

    // Sign in
    @POST("login")
    fun login(@Body request: AuthRequest): Call<AuthResponse>

    // Read active account
    @GET("me")
    fun me(): Call<ApiUser>

    // Sign out
    @POST("logout")
    fun logout(): Call<ApiMessage>

    // Read pets
    @GET("pets")
    fun pets(): Call<List<ApiPet>>

    // Create pet
    @POST("pets")
    fun createPet(@Body pet: PetRequest): Call<ApiPet>

    // Update pet
    @PUT("pets/{id}")
    fun updatePet(@Path("id") id: Long, @Body pet: PetRequest): Call<ApiPet>

    // Delete pet
    @DELETE("pets/{id}")
    fun deletePet(@Path("id") id: Long): Call<Unit>

    // Read vaccinations
    @GET("vaccinations")
    fun vaccinations(): Call<List<ApiVaccination>>

    // Create vaccination
    @POST("vaccinations")
    fun createVaccination(@Body record: VaccinationRequest): Call<ApiVaccination>

    // Update vaccination
    @PUT("vaccinations/{id}")
    fun updateVaccination(
        @Path("id") id: Long,
        @Body record: VaccinationRequest
    ): Call<ApiVaccination>

    // Delete vaccination
    @DELETE("vaccinations/{id}")
    fun deleteVaccination(@Path("id") id: Long): Call<Unit>

    // Read medical records
    @GET("medical-records")
    fun medicalRecords(): Call<List<ApiMedicalRecord>>

    // Create medical record
    @POST("medical-records")
    fun createMedicalRecord(@Body record: MedicalRecordRequest): Call<ApiMedicalRecord>

    // Update medical record
    @PUT("medical-records/{id}")
    fun updateMedicalRecord(
        @Path("id") id: Long,
        @Body record: MedicalRecordRequest
    ): Call<ApiMedicalRecord>

    // Delete medical record
    @DELETE("medical-records/{id}")
    fun deleteMedicalRecord(@Path("id") id: Long): Call<Unit>

    // Read appointments
    @GET("appointments")
    fun appointments(): Call<List<ApiAppointment>>

    // Create appointment
    @POST("appointments")
    fun createAppointment(@Body appointment: AppointmentRequest): Call<ApiAppointment>

    // Update appointment
    @PUT("appointments/{id}")
    fun updateAppointment(
        @Path("id") id: Long,
        @Body appointment: AppointmentRequest
    ): Call<ApiAppointment>

    // Delete appointment
    @DELETE("appointments/{id}")
    fun deleteAppointment(@Path("id") id: Long): Call<Unit>

    // Read prediction history
    @GET("ai-predictions")
    fun aiPredictions(): Call<ApiAiPredictionPage>

    // Create breed prediction
    @Multipart
    @POST("ai-predictions")
    fun createAiPrediction(
        @Part image: MultipartBody.Part,
        @Part("pet_id") petId: RequestBody? = null
    ): Call<ApiAiPrediction>
}

object NetworkModule
{
    @Volatile
    private var authToken: String? = null

    // Update the token used by authenticated requests
    fun setAuthToken(token: String?)
    {
        authToken = token
    }

    private val backendClient: OkHttpClient = createBackendClient()

    // Create the API only when it is first needed
    val backend: PawCareApi by lazy {
        retrofit(BuildConfig.API_BASE_URL, backendClient).create(PawCareApi::class.java)
    }

    // Configure requests and basic connection logging
    private fun createBackendClient(): OkHttpClient
    {
        val logging: HttpLoggingInterceptor = HttpLoggingInterceptor()
        logging.level = HttpLoggingInterceptor.Level.BASIC

        val clientBuilder: OkHttpClient.Builder = OkHttpClient.Builder()
        clientBuilder.addInterceptor { chain ->
            val request: Request = prepareRequest(chain.request())
            chain.proceed(request)
        }
        clientBuilder.addInterceptor(logging)

        return clientBuilder.build()
    }

    // Add API headers and route emulator traffic to Herd
    private fun prepareRequest(originalRequest: Request): Request
    {
        val requestBuilder: Request.Builder = originalRequest.newBuilder()
        requestBuilder.header("Accept", "application/json")
        requestBuilder.header("Host", "pawcare-api.test")

        val token: String? = authToken
        token?.takeIf { it.isNotBlank() }?.let { currentToken ->
            requestBuilder.header("Authorization", "Bearer $currentToken")
        }

        return requestBuilder.build()
    }

    // Convert Laravel JSON field names to Kotlin properties
    private fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit
    {
        val gson: Gson = GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()

        val converter: GsonConverterFactory = GsonConverterFactory.create(gson)
        val retrofitBuilder: Retrofit.Builder = Retrofit.Builder()
        retrofitBuilder.baseUrl(baseUrl)
        retrofitBuilder.client(client)
        retrofitBuilder.addConverterFactory(converter)

        return retrofitBuilder.build()
    }
}
