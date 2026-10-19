package com.example.pawcareai.data

import com.example.pawcareai.network.ApiMedicalRecord
import com.example.pawcareai.network.MedicalRecordRequest
import com.example.pawcareai.network.PawCareApi
import retrofit2.Call

//saves and deletes medical visit records through laravel, then updates the phone copy
class MedicalRecordRepository(
    private val api: PawCareApi,
    private val cache: RecordCache
)
{
    //copies laravel's json classes into the app's record classes
    private val converter: RecordConverter = RecordConverter(cache)

    //2.input

    //build the json body laravel expects; empty optional fields are sent as null
    private fun createRequest(record: MedicalRecord): MedicalRecordRequest
    {
        return MedicalRecordRequest(
            petId = record.petId,
            visitDate = record.visitDate,
            veterinarian = blankToNull(record.veterinarian),
            diagnosis = record.diagnosis.trim(),
            treatment = blankToNull(record.treatment),
            notes = blankToNull(record.notes)
        )
    }

    //3.process

    //create a new medical record (POST) or update an existing one (PUT medical-records/{id})
    fun saveMedicalRecord(record: MedicalRecord, onResult: (Result<MedicalRecord>) -> Unit)
    {
        val request: MedicalRecordRequest = createRequest(record)
        val saveCall: Call<ApiMedicalRecord>
        if (record.id > 0)
        {
            saveCall = api.updateMedicalRecord(record.id, request)
        }
        else
        {
            saveCall = api.createMedicalRecord(request)
        }

        saveCall.enqueue(ApiCallback<ApiMedicalRecord>(
            onSuccess = { apiRecord ->
                val savedRecord: MedicalRecord = converter.toMedicalRecord(apiRecord)
                putInCache(savedRecord)
                onResult(Result.success(savedRecord))
            },
            onFailure = { error ->
                onResult(Result.failure(error))
            }
        ))
    }

    //delete a medical record (DELETE medical-records/{id})
    fun deleteMedicalRecord(id: Long, onResult: (Result<Unit>) -> Unit)
    {
        api.deleteMedicalRecord(id).enqueue(EmptyApiCallback(
            onSuccess = {
                cache.medicalRecords.removeAll { record -> record.id == id }
                onResult(Result.success(Unit))
            },
            onFailure = { error ->
                onResult(Result.failure(error))
            }
        ))
    }

    //replace the record with the same id, or add it at the end when it is new
    private fun putInCache(savedRecord: MedicalRecord)
    {
        for (index in cache.medicalRecords.indices)
        {
            if (cache.medicalRecords[index].id == savedRecord.id)
            {
                cache.medicalRecords[index] = savedRecord
                return
            }
        }
        cache.medicalRecords.add(savedRecord)
    }

    //4.output

    //read the latest visit first
    fun medicalRecords(): List<MedicalRecord>
    {
        return cache.medicalRecords.sortedByDescending { record -> record.visitDate }
    }
}
