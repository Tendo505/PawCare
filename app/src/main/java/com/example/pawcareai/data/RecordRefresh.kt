package com.example.pawcareai.data

import com.example.pawcareai.network.ApiAiPrediction
import com.example.pawcareai.network.ApiAppointment
import com.example.pawcareai.network.ApiMedicalRecord
import com.example.pawcareai.network.ApiPet
import com.example.pawcareai.network.ApiVaccination
import com.example.pawcareai.network.PawCareApi
import retrofit2.Call

//every record list returned by one full refresh
class LoadedRecords(
    val pets: List<ApiPet>,
    val vaccinations: List<ApiVaccination>,
    val medicalRecords: List<ApiMedicalRecord>,
    val appointments: List<ApiAppointment>,
    val predictions: List<ApiAiPrediction>
)

//load pets, vaccinations, medical records, appointments and predictions one after another,
//then hand them over together so the cache is replaced only after all requests succeed
class RecordRefresh(
    private val api: PawCareApi,
    private val saveRecords: (LoadedRecords) -> Unit,
    private val onResult: (Result<Unit>) -> Unit
)
{
    //records collected by the earlier steps
    private var pets: List<ApiPet> = emptyList()
    private var vaccinations: List<ApiVaccination> = emptyList()
    private var medicalRecords: List<ApiMedicalRecord> = emptyList()
    private var appointments: List<ApiAppointment> = emptyList()

    //3.process

    //step 1: read pets
    fun start()
    {
        api.pets().thenRun { loaded -> pets = loaded; loadVaccinations() }
    }

    //step 2: read vaccination records
    private fun loadVaccinations()
    {
        api.vaccinations().thenRun { loaded -> vaccinations = loaded; loadMedicalRecords() }
    }

    //step 3: read medical records
    private fun loadMedicalRecords()
    {
        api.medicalRecords().thenRun { loaded -> medicalRecords = loaded; loadAppointments() }
    }

    //step 4: read appointments
    private fun loadAppointments()
    {
        api.appointments().thenRun { loaded -> appointments = loaded; loadPredictions() }
    }

    //step 5: read prediction history and finish refreshing records
    private fun loadPredictions()
    {
        api.aiPredictions().thenRun { page -> finish(page.data) }
    }

    //replace cached records only after all requests succeed
    private fun finish(predictions: List<ApiAiPrediction>)
    {
        saveRecords(LoadedRecords(pets, vaccinations, medicalRecords, appointments, predictions))
        onResult(Result.success(Unit))
    }

    //run the next step after a success, or stop and report the first failure
    private fun <T> Call<T>.thenRun(nextStep: (T) -> Unit)
    {
        enqueueResult { result ->
            result.onSuccess(nextStep)
            result.onFailure { error -> onResult(Result.failure(error)) }
        }
    }
}
