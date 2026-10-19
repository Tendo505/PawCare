package com.example.pawcareai.data

import com.example.pawcareai.network.ApiAiPrediction
import com.example.pawcareai.network.ApiAiPredictionPage
import com.example.pawcareai.network.ApiAppointment
import com.example.pawcareai.network.ApiMedicalRecord
import com.example.pawcareai.network.ApiPet
import com.example.pawcareai.network.ApiVaccination
import com.example.pawcareai.network.PawCareApi

//loads every record list from laravel, one request after another:
//pets -> vaccinations -> medical records -> appointments -> breed scans
//the phone copy is replaced only when all five requests succeed, so a failed
//refresh never leaves the screens with half of the new records
class RecordRefresh(
    private val api: PawCareApi,
    private val cache: RecordCache,
    private val onResult: (Result<Unit>) -> Unit
)
{
    //the lists collected by the earlier steps, kept here until the last step finishes
    private var loadedPets: List<ApiPet> = emptyList()
    private var loadedVaccinations: List<ApiVaccination> = emptyList()
    private var loadedMedicalRecords: List<ApiMedicalRecord> = emptyList()
    private var loadedAppointments: List<ApiAppointment> = emptyList()

    //3.process

    //step 1: read pets (GET pets)
    fun start()
    {
        api.pets().enqueue(ApiCallback<List<ApiPet>>(
            onSuccess = { pets ->
                loadedPets = pets
                loadVaccinations()
            },
            onFailure = { error -> stopWithError(error) }
        ))
    }

    //step 2: read vaccinations (GET vaccinations)
    private fun loadVaccinations()
    {
        api.vaccinations().enqueue(ApiCallback<List<ApiVaccination>>(
            onSuccess = { vaccinations ->
                loadedVaccinations = vaccinations
                loadMedicalRecords()
            },
            onFailure = { error -> stopWithError(error) }
        ))
    }

    //step 3: read medical records (GET medical-records)
    private fun loadMedicalRecords()
    {
        api.medicalRecords().enqueue(ApiCallback<List<ApiMedicalRecord>>(
            onSuccess = { medicalRecords ->
                loadedMedicalRecords = medicalRecords
                loadAppointments()
            },
            onFailure = { error -> stopWithError(error) }
        ))
    }

    //step 4: read appointments (GET appointments)
    private fun loadAppointments()
    {
        api.appointments().enqueue(ApiCallback<List<ApiAppointment>>(
            onSuccess = { appointments ->
                loadedAppointments = appointments
                loadPredictions()
            },
            onFailure = { error -> stopWithError(error) }
        ))
    }

    //step 5: read the breed scan history (GET ai-predictions), then save everything
    private fun loadPredictions()
    {
        api.aiPredictions().enqueue(ApiCallback<ApiAiPredictionPage>(
            onSuccess = { page ->
                saveAllRecords(page.data)
                onResult(Result.success(Unit))
            },
            onFailure = { error -> stopWithError(error) }
        ))
    }

    //any failed step stops the refresh; the old phone copy stays as it was
    private fun stopWithError(error: Throwable)
    {
        onResult(Result.failure(error))
    }

    //4.output

    //replace the phone copy; pets go first because breed scans look up pet names
    private fun saveAllRecords(predictions: List<ApiAiPrediction>)
    {
        cache.pets = mutableListOf()
        for (apiPet in loadedPets)
        {
            cache.pets.add(RecordConverter.toPet(apiPet))
        }

        cache.vaccinations = mutableListOf()
        for (apiVaccination in loadedVaccinations)
        {
            cache.vaccinations.add(RecordConverter.toVaccination(apiVaccination))
        }

        cache.medicalRecords = mutableListOf()
        for (apiRecord in loadedMedicalRecords)
        {
            cache.medicalRecords.add(RecordConverter.toMedicalRecord(apiRecord))
        }

        cache.appointments = mutableListOf()
        for (apiAppointment in loadedAppointments)
        {
            cache.appointments.add(RecordConverter.toAppointment(apiAppointment))
        }

        cache.predictions = mutableListOf()
        for (apiPrediction in predictions)
        {
            cache.predictions.add(RecordConverter.toBreedPrediction(apiPrediction, cache))
        }
    }
}
