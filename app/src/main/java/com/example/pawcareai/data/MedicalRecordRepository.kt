package com.example.pawcareai.data

import com.example.pawcareai.network.ApiMedicalRecord
import com.example.pawcareai.network.MedicalRecordRequest
import com.example.pawcareai.network.PawCareApi
import retrofit2.Call

//medical visit records loaded through laravel
class MedicalRecordRepository(private val api: PawCareApi)
{
    //temporary screen data loaded through laravel
    private var medicalRecordCache: List<MedicalRecord> = emptyList()

    //2.input

    //prepare medical details for saving
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

    //3.process

    //create or update a medical record
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

        saveCall.saveResult(
            convert = { it.toMedicalRecord() },
            updateCache = { saved -> medicalRecordCache = medicalRecordCache.replaceOrAdd(saved) { it.id } },
            onResult = onResult
        )
    }

    //delete a medical record
    fun deleteMedicalRecord(id: Long, onResult: (Result<Unit>) -> Unit)
    {
        api.deleteMedicalRecord(id).enqueueEmpty { result ->
            result.onSuccess {
                medicalRecordCache = medicalRecordCache.filterNot { record -> record.id == id }
            }
            onResult(result)
        }
    }

    //replace cached medical records after a full refresh
    fun replaceCache(medicalRecords: List<ApiMedicalRecord>)
    {
        medicalRecordCache = medicalRecords.map { record -> record.toMedicalRecord() }
    }

    //remove medical records linked to a deleted pet
    fun removePet(petId: Long)
    {
        medicalRecordCache = medicalRecordCache.filterNot { record -> record.petId == petId }
    }

    //remove medical records belonging to a cleared session
    fun clearCache()
    {
        medicalRecordCache = emptyList()
    }

    //4.output

    //read the latest medical records first
    fun medicalRecords(): List<MedicalRecord>
    {
        return medicalRecordCache.sortedByDescending { record -> record.visitDate }
    }

    //convert an api medical record for the app
    private fun ApiMedicalRecord.toMedicalRecord(): MedicalRecord
    {
        return MedicalRecord(
            id = id,
            petId = petId,
            visitDate = visitDate,
            veterinarian = veterinarian.orEmpty(),
            diagnosis = diagnosis,
            treatment = treatment.orEmpty(),
            notes = notes.orEmpty()
        )
    }
}
