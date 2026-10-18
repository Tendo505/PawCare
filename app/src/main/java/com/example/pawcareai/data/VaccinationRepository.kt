package com.example.pawcareai.data

import com.example.pawcareai.network.ApiVaccination
import com.example.pawcareai.network.PawCareApi
import com.example.pawcareai.network.VaccinationRequest
import retrofit2.Call

//vaccination records loaded through laravel
class VaccinationRepository(private val api: PawCareApi)
{
    //temporary screen data loaded through laravel
    private var vaccinationCache: List<VaccinationRecord> = emptyList()

    //2.input

    //prepare vaccination details for saving
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

    //3.process

    //create or update a vaccination record
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

        saveCall.saveResult(
            convert = { it.toVaccinationRecord() },
            updateCache = { saved -> vaccinationCache = vaccinationCache.replaceOrAdd(saved) { it.id } },
            onResult = onResult
        )
    }

    //delete a vaccination record
    fun deleteVaccination(id: Long, onResult: (Result<Unit>) -> Unit)
    {
        api.deleteVaccination(id).enqueueEmpty { result ->
            result.onSuccess {
                vaccinationCache = vaccinationCache.filterNot { record -> record.id == id }
            }
            onResult(result)
        }
    }

    //replace cached vaccinations after a full refresh
    fun replaceCache(vaccinations: List<ApiVaccination>)
    {
        vaccinationCache = vaccinations.map { record -> record.toVaccinationRecord() }
    }

    //remove vaccinations linked to a deleted pet
    fun removePet(petId: Long)
    {
        vaccinationCache = vaccinationCache.filterNot { record -> record.petId == petId }
    }

    //remove vaccinations belonging to a cleared session
    fun clearCache()
    {
        vaccinationCache = emptyList()
    }

    //4.output

    //read vaccinations by due date
    fun vaccinations(): List<VaccinationRecord>
    {
        return vaccinationCache.sortedBy { record -> record.dueDate }
    }

    //convert an api vaccination record for the app
    private fun ApiVaccination.toVaccinationRecord(): VaccinationRecord
    {
        return VaccinationRecord(
            id = id,
            petId = petId,
            vaccineName = vaccineName,
            administeredDate = administeredDate.orEmpty(),
            dueDate = dueDate,
            clinic = clinic.orEmpty(),
            status = status,
            notes = notes.orEmpty()
        )
    }
}
