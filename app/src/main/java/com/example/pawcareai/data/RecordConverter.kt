package com.example.pawcareai.data

import com.example.pawcareai.network.ApiAiPrediction
import com.example.pawcareai.network.ApiAppointment
import com.example.pawcareai.network.ApiMedicalRecord
import com.example.pawcareai.network.ApiPet
import com.example.pawcareai.network.ApiPredictionPet
import com.example.pawcareai.network.ApiVaccination

//copies the json classes sent by laravel into the app's own record classes
//laravel leaves empty optional fields as null; the app uses "" instead so the screens never see null
object RecordConverter
{
    //4.output

    //convert one pet
    fun toPet(apiPet: ApiPet): Pet
    {
        return Pet(
            id = apiPet.id,
            name = apiPet.name,
            species = apiPet.species,
            breed = apiPet.breed ?: "",
            sex = apiPet.sex,
            birthDate = apiPet.birthDate ?: "",
            weightKg = apiPet.weightKg ?: 0.0,
            microchipNumber = apiPet.microchipNumber ?: "",
            notes = apiPet.notes ?: ""
        )
    }

    //convert one vaccination record
    fun toVaccination(apiVaccination: ApiVaccination): VaccinationRecord
    {
        return VaccinationRecord(
            id = apiVaccination.id,
            petId = apiVaccination.petId,
            vaccineName = apiVaccination.vaccineName,
            administeredDate = apiVaccination.administeredDate ?: "",
            dueDate = apiVaccination.dueDate,
            clinic = apiVaccination.clinic ?: "",
            status = apiVaccination.status,
            notes = apiVaccination.notes ?: ""
        )
    }

    //convert one medical record
    fun toMedicalRecord(apiRecord: ApiMedicalRecord): MedicalRecord
    {
        return MedicalRecord(
            id = apiRecord.id,
            petId = apiRecord.petId,
            visitDate = apiRecord.visitDate,
            veterinarian = apiRecord.veterinarian ?: "",
            diagnosis = apiRecord.diagnosis,
            treatment = apiRecord.treatment ?: "",
            notes = apiRecord.notes ?: ""
        )
    }

    //convert one appointment; the time is cut to HH:MM because laravel may send HH:MM:SS
    fun toAppointment(apiAppointment: ApiAppointment): Appointment
    {
        return Appointment(
            id = apiAppointment.id,
            petId = apiAppointment.petId,
            appointmentDate = apiAppointment.appointmentDate,
            appointmentTime = apiAppointment.appointmentTime.take(5),
            clinic = apiAppointment.clinic ?: "",
            reason = apiAppointment.reason,
            status = apiAppointment.status,
            notes = apiAppointment.notes ?: ""
        )
    }

    //convert one saved breed scan; the pet name comes from laravel, or from the cached pets
    fun toBreedPrediction(apiPrediction: ApiAiPrediction, cache: RecordCache): BreedPrediction
    {
        var petName: String = ""
        val linkedPet: ApiPredictionPet? = apiPrediction.pet
        val linkedPetId: Long? = apiPrediction.petId
        if (linkedPet != null)
        {
            petName = linkedPet.name
        }
        else if (linkedPetId != null)
        {
            petName = cache.petName(linkedPetId)
        }

        val options: MutableList<BreedPredictionOption> = mutableListOf()
        for (option in apiPrediction.topPredictions)
        {
            options.add(BreedPredictionOption(option.breed, option.confidence))
        }

        return BreedPrediction(
            id = apiPrediction.id,
            petId = apiPrediction.petId,
            petName = petName,
            species = apiPrediction.species,
            breed = apiPrediction.breed,
            confidence = apiPrediction.confidence,
            createdAt = apiPrediction.createdAt,
            imageUrl = apiPrediction.imageUrl ?: "",
            modelVersion = apiPrediction.modelVersion,
            topPredictions = options,
            disclaimer = apiPrediction.disclaimer
        )
    }
}
