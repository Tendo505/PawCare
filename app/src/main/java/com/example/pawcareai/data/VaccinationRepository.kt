package com.example.pawcareai.data

import com.example.pawcareai.network.ApiVaccination
import com.example.pawcareai.network.PawCareApi
import com.example.pawcareai.network.VaccinationRequest
import retrofit2.Call

//saves and deletes vaccination records through laravel, then updates the phone copy
class VaccinationRepository(
    private val api: PawCareApi,
    private val cache: RecordCache
)
{
    //2.input

    //build the json body laravel expects; empty optional fields are sent as null
    private fun createRequest(record: VaccinationRecord): VaccinationRequest
    {
        return VaccinationRequest(
            petId = record.petId,
            vaccineName = record.vaccineName.trim(),
            administeredDate = blankToNull(record.administeredDate),
            dueDate = record.dueDate,
            clinic = blankToNull(record.clinic),
            status = record.status,
            notes = blankToNull(record.notes)
        )
    }

    //3.process

    //create a new vaccination (POST) or update an existing one (PUT vaccinations/{id})
    fun saveVaccination(record: VaccinationRecord, onResult: (Result<VaccinationRecord>) -> Unit)
    {
        val request: VaccinationRequest = createRequest(record)
        val saveCall: Call<ApiVaccination>
        if (record.id > 0)
        {
            saveCall = api.updateVaccination(record.id, request)
        }
        else
        {
            saveCall = api.createVaccination(request)
        }

        saveCall.enqueue(ApiCallback<ApiVaccination>(
            onSuccess = { apiVaccination ->
                val savedRecord: VaccinationRecord = RecordConverter.toVaccination(apiVaccination)
                putInCache(savedRecord)
                onResult(Result.success(savedRecord))
            },
            onFailure = { error ->
                onResult(Result.failure(error))
            }
        ))
    }

    //delete a vaccination (DELETE vaccinations/{id})
    fun deleteVaccination(id: Long, onResult: (Result<Unit>) -> Unit)
    {
        api.deleteVaccination(id).enqueue(EmptyApiCallback(
            onSuccess = {
                cache.vaccinations.removeAll { record -> record.id == id }
                onResult(Result.success(Unit))
            },
            onFailure = { error ->
                onResult(Result.failure(error))
            }
        ))
    }

    //replace the record with the same id, or add it at the end when it is new
    private fun putInCache(savedRecord: VaccinationRecord)
    {
        for (index in cache.vaccinations.indices)
        {
            if (cache.vaccinations[index].id == savedRecord.id)
            {
                cache.vaccinations[index] = savedRecord
                return
            }
        }
        cache.vaccinations.add(savedRecord)
    }

    //4.output

    //read vaccinations with the nearest due date first
    fun vaccinations(): List<VaccinationRecord>
    {
        return cache.vaccinations.sortedBy { record -> record.dueDate }
    }
}
