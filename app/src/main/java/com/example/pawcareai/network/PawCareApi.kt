package com.example.pawcareai.network

import com.example.pawcareai.BuildConfig
import com.example.pawcareai.data.Appointment
import com.example.pawcareai.data.MedicalRecord
import com.example.pawcareai.data.Pet
import com.example.pawcareai.data.VaccinationRecord
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
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

data class AuthRequest(val email: String, val password: String, val name: String? = null)
data class AuthResponse(val token: String, val user: ApiUser)
data class ApiUser(val id: Long, val name: String, val email: String)
data class BreedPredictionResponse(
    val species: String,
    val breed: String,
    val confidence: Double,
    val top_predictions: List<PredictionItem> = emptyList(),
    val model_ready: Boolean = true,
    val disclaimer: String = ""
)
data class PredictionItem(val breed: String, val confidence: Double)

interface PawCareApi {
    @POST("register") fun register(@Body request: AuthRequest): Call<AuthResponse>
    @POST("login") fun login(@Body request: AuthRequest): Call<AuthResponse>
    @GET("pets") fun pets(): Call<List<Pet>>
    @POST("pets") fun createPet(@Body pet: Pet): Call<Pet>
    @PUT("pets/{id}") fun updatePet(@Path("id") id: Long, @Body pet: Pet): Call<Pet>
    @DELETE("pets/{id}") fun deletePet(@Path("id") id: Long): Call<Unit>
    @GET("vaccinations") fun vaccinations(): Call<List<VaccinationRecord>>
    @POST("vaccinations") fun createVaccination(@Body record: VaccinationRecord): Call<VaccinationRecord>
    @GET("medical-records") fun medicalRecords(): Call<List<MedicalRecord>>
    @POST("medical-records") fun createMedicalRecord(@Body record: MedicalRecord): Call<MedicalRecord>
    @GET("appointments") fun appointments(): Call<List<Appointment>>
    @POST("appointments") fun createAppointment(@Body appointment: Appointment): Call<Appointment>
}

interface BreedRecognitionApi {
    @Multipart
    @POST("predict")
    fun predict(@Part image: MultipartBody.Part): Call<BreedPredictionResponse>
}

object NetworkModule {
    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    val backend: PawCareApi by lazy { retrofit(BuildConfig.API_BASE_URL).create(PawCareApi::class.java) }
    val breedRecognition: BreedRecognitionApi by lazy { retrofit(BuildConfig.AI_BASE_URL).create(BreedRecognitionApi::class.java) }

    private fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()))
        .build()
}
