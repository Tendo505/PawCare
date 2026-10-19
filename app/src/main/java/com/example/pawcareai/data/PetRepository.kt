package com.example.pawcareai.data

import com.example.pawcareai.network.ApiPet
import com.example.pawcareai.network.PawCareApi
import com.example.pawcareai.network.PetRequest
import retrofit2.Call

//saves and deletes pet profiles through laravel, then updates the phone copy
class PetRepository(
    private val api: PawCareApi,
    private val cache: RecordCache
)
{
    //copies laravel's json classes into the app's record classes
    private val converter: RecordConverter = RecordConverter(cache)

    //2.input

    //build the json body laravel expects; empty optional fields are sent as null
    private fun createRequest(pet: Pet): PetRequest
    {
        var weight: Double? = null
        if (pet.weightKg > 0)
        {
            weight = pet.weightKg
        }

        return PetRequest(
            name = pet.name.trim(),
            species = pet.species,
            breed = blankToNull(pet.breed),
            sex = pet.sex,
            birthDate = blankToNull(pet.birthDate),
            weightKg = weight,
            microchipNumber = blankToNull(pet.microchipNumber),
            notes = blankToNull(pet.notes)
        )
    }

    //3.process

    //create a new pet (POST pets) or update an existing one (PUT pets/{id})
    fun savePet(pet: Pet, onResult: (Result<Pet>) -> Unit)
    {
        val request: PetRequest = createRequest(pet)
        val saveCall: Call<ApiPet>
        if (pet.id > 0)
        {
            saveCall = api.updatePet(pet.id, request)
        }
        else
        {
            saveCall = api.createPet(request)
        }

        saveCall.enqueue(ApiCallback<ApiPet>(
            onSuccess = { apiPet ->
                val savedPet: Pet = converter.toPet(apiPet)
                putInCache(savedPet)
                onResult(Result.success(savedPet))
            },
            onFailure = { error ->
                onResult(Result.failure(error))
            }
        ))
    }

    //delete a pet (DELETE pets/{id}); its health records are removed from the phone copy too
    fun deletePet(id: Long, onResult: (Result<Unit>) -> Unit)
    {
        api.deletePet(id).enqueue(EmptyApiCallback(
            onSuccess = {
                cache.removePet(id)
                onResult(Result.success(Unit))
            },
            onFailure = { error ->
                onResult(Result.failure(error))
            }
        ))
    }

    //replace the pet with the same id, or add it at the end when it is new
    private fun putInCache(savedPet: Pet)
    {
        for (index in cache.pets.indices)
        {
            if (cache.pets[index].id == savedPet.id)
            {
                cache.pets[index] = savedPet
                return
            }
        }
        cache.pets.add(savedPet)
    }

    //4.output

    //read pets in alphabetical order
    fun pets(): List<Pet>
    {
        return cache.pets.sortedBy { pet -> pet.name.lowercase() }
    }
}
