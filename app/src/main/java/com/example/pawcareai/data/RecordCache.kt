package com.example.pawcareai.data

//the phone's memory copy of the records loaded from laravel
//the screens read from these lists, so they do not need to ask laravel again every time
class RecordCache(private val unknownPetName: String)
{
    //1.screen setup

    var pets: MutableList<Pet> = mutableListOf()
    var vaccinations: MutableList<VaccinationRecord> = mutableListOf()
    var medicalRecords: MutableList<MedicalRecord> = mutableListOf()
    var appointments: MutableList<Appointment> = mutableListOf()
    var predictions: MutableList<BreedPrediction> = mutableListOf()

    //3.process

    //forget every record, used when the user signs out or the token expires
    fun clearAll()
    {
        pets = mutableListOf()
        vaccinations = mutableListOf()
        medicalRecords = mutableListOf()
        appointments = mutableListOf()
        predictions = mutableListOf()
    }

    //remove a deleted pet and every health record that belonged to it
    //laravel deletes these records too, so the phone copy must match
    fun removePet(petId: Long)
    {
        pets.removeAll { pet -> pet.id == petId }
        vaccinations.removeAll { record -> record.petId == petId }
        medicalRecords.removeAll { record -> record.petId == petId }
        appointments.removeAll { appointment -> appointment.petId == petId }
    }

    //4.output

    //find a pet by its id, or null when it is not in the list
    fun findPet(petId: Long): Pet?
    {
        for (pet in pets)
        {
            if (pet.id == petId)
            {
                return pet
            }
        }
        return null
    }

    //read a pet's name for labels such as "Milo · Due 3 Nov 2026"
    fun petName(petId: Long): String
    {
        val pet: Pet? = findPet(petId)
        if (pet == null)
        {
            return unknownPetName
        }
        return pet.name
    }
}
