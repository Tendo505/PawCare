package com.example.pawcareai.data

import com.example.pawcareai.network.ApiPet
import com.example.pawcareai.network.PawCareApi
import com.example.pawcareai.network.PetRequest
import retrofit2.Call

//pet profiles loaded through laravel
class PetRepository(private val api: PawCareApi)
{
    //temporary screen data loaded through laravel
    private var petCache: List<Pet> = emptyList()

    //2.input

    //prepare pet details for saving
    private fun Pet.toRequest(): PetRequest
    {
        return PetRequest(
            name = name.trim(),
            species = species,
            breed = breed.trim().nullIfBlank(),
            sex = sex,
            birthDate = birthDate.trim().nullIfBlank(),
            weightKg = weightKg.takeIf { weight -> weight > 0 },
            microchipNumber = microchipNumber.trim().nullIfBlank(),
            notes = notes.trim().nullIfBlank()
        )
    }

    //3.process

    //create or update a pet, then refresh its cached record
    fun savePet(pet: Pet, onResult: (Result<Pet>) -> Unit)
    {
        val saveCall: Call<ApiPet> = if (pet.id > 0)
        {
            api.updatePet(pet.id, pet.toRequest())
        }
        else
        {
            api.createPet(pet.toRequest())
        }

        saveCall.saveResult(
            convert = { it.toPet() },
            updateCache = { saved -> petCache = petCache.replaceOrAdd(saved) { it.id } },
            onResult = onResult
        )
    }

    //delete a pet and remove it from the cache
    fun deletePet(id: Long, onResult: (Result<Unit>) -> Unit)
    {
        api.deletePet(id).enqueueEmpty { result ->
            result.onSuccess {
                petCache = petCache.filterNot { pet -> pet.id == id }
            }
            onResult(result)
        }
    }

    //replace cached pets after a full refresh
    fun replaceCache(pets: List<ApiPet>)
    {
        petCache = pets.map { pet -> pet.toPet() }
    }

    //remove pets belonging to a cleared session
    fun clearCache()
    {
        petCache = emptyList()
    }

    //4.output

    //read pets in alphabetical order
    fun pets(): List<Pet>
    {
        return petCache.sortedBy { pet -> pet.name.lowercase() }
    }

    //find a pet by its record id
    fun pet(id: Long): Pet?
    {
        return petCache.firstOrNull { pet -> pet.id == id }
    }

    //convert an api pet record for the app
    private fun ApiPet.toPet(): Pet
    {
        return Pet(
            id = id,
            name = name,
            species = species,
            breed = breed.orEmpty(),
            sex = sex,
            birthDate = birthDate.orEmpty(),
            weightKg = weightKg ?: 0.0,
            microchipNumber = microchipNumber.orEmpty(),
            notes = notes.orEmpty()
        )
    }
}
