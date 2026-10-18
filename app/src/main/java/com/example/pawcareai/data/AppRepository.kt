package com.example.pawcareai.data

import android.content.Context
import com.example.pawcareai.R

//single entry point for the screens; each feature is handled by its own repository
class AppRepository(context: Context)
{
    //shared session and feature repositories
    private val session: ApiSession = ApiSession(context)
    private val unknownPetName: String = context.getString(R.string.unknown_pet)
    private val petRepository: PetRepository = PetRepository(session.api)
    private val vaccinationRepository: VaccinationRepository = VaccinationRepository(session.api)
    private val medicalRecordRepository: MedicalRecordRepository = MedicalRecordRepository(session.api)
    private val appointmentRepository: AppointmentRepository = AppointmentRepository(session.api)
    private val aiScanRepository: AiScanRepository = AiScanRepository(session.api, ::petName)
    private val systemHealthRepository: SystemHealthRepository = SystemHealthRepository(session.api)
    private val authRepository: AuthRepository = AuthRepository(session, ::clearRecords)

    //1.screen setup

    //read the signed-in account
    val currentUser: UserAccount?
        get()
        {
            return session.activeUser
        }

    //check whether an authentication token has been saved
    val hasSession: Boolean
        get()
        {
            return session.hasSession
        }

    //2.input

    //restore the account using its saved token
    fun restoreSession(onResult: (Result<UserAccount>) -> Unit) = authRepository.restoreSession(onResult)

    //create a new account through laravel
    fun register(name: String, email: String, password: String, onResult: (Result<UserAccount>) -> Unit) =
        authRepository.register(name, email, password, onResult)

    //sign in and save the returned token
    fun login(email: String, password: String, onResult: (Result<UserAccount>) -> Unit) =
        authRepository.login(email, password, onResult)

    //sign out and clear the local session
    fun logout(onComplete: () -> Unit) = authRepository.logout(onComplete)

    //3.process

    //read all records before replacing the current cache
    fun refreshData(onResult: (Result<Unit>) -> Unit)
    {
        RecordRefresh(session.api, ::replaceRecords, onResult).start()
    }

    //check laravel, database and ai connections
    fun checkSystemHealth(onResult: (Result<SystemHealth>) -> Unit) = systemHealthRepository.checkSystemHealth(onResult)

    //create or update a pet, then refresh its cached record
    fun savePet(pet: Pet, onResult: (Result<Pet>) -> Unit) = petRepository.savePet(pet, onResult)

    //delete a pet and remove its related records from the cache
    fun deletePet(id: Long, onResult: (Result<Unit>) -> Unit)
    {
        petRepository.deletePet(id) { result ->
            result.onSuccess { removeRecordsForPet(id) }
            onResult(result)
        }
    }

    //create or update a vaccination record
    fun saveVaccination(record: VaccinationRecord, onResult: (Result<VaccinationRecord>) -> Unit) =
        vaccinationRepository.saveVaccination(record, onResult)

    //delete a vaccination record
    fun deleteVaccination(id: Long, onResult: (Result<Unit>) -> Unit) = vaccinationRepository.deleteVaccination(id, onResult)

    //create or update a medical record
    fun saveMedicalRecord(record: MedicalRecord, onResult: (Result<MedicalRecord>) -> Unit) =
        medicalRecordRepository.saveMedicalRecord(record, onResult)

    //delete a medical record
    fun deleteMedicalRecord(id: Long, onResult: (Result<Unit>) -> Unit) = medicalRecordRepository.deleteMedicalRecord(id, onResult)

    //create or update an appointment
    fun saveAppointment(appointment: Appointment, onResult: (Result<Appointment>) -> Unit) =
        appointmentRepository.saveAppointment(appointment, onResult)

    //delete an appointment
    fun deleteAppointment(id: Long, onResult: (Result<Unit>) -> Unit) = appointmentRepository.deleteAppointment(id, onResult)

    //upload a photo and cache its saved prediction
    fun analyzeBreed(
        imageBytes: ByteArray,
        mimeType: String,
        fileName: String,
        petId: Long?,
        onResult: (Result<BreedPrediction>) -> Unit
    ) = aiScanRepository.analyzeBreed(imageBytes, mimeType, fileName, petId, onResult)

    //replace every cache with a completed refresh, pets first so predictions can read pet names
    private fun replaceRecords(records: LoadedRecords)
    {
        petRepository.replaceCache(records.pets)
        vaccinationRepository.replaceCache(records.vaccinations)
        medicalRecordRepository.replaceCache(records.medicalRecords)
        appointmentRepository.replaceCache(records.appointments)
        aiScanRepository.replaceCache(records.predictions)
    }

    //remove the health records linked to a deleted pet
    private fun removeRecordsForPet(petId: Long)
    {
        vaccinationRepository.removePet(petId)
        medicalRecordRepository.removePet(petId)
        appointmentRepository.removePet(petId)
    }

    //remove records belonging to a cleared session
    private fun clearRecords()
    {
        petRepository.clearCache()
        vaccinationRepository.clearCache()
        medicalRecordRepository.clearCache()
        appointmentRepository.clearCache()
        aiScanRepository.clearCache()
    }

    //4.output

    //read cached records in the order each screen shows them
    fun pets(): List<Pet> = petRepository.pets()
    fun pet(id: Long): Pet? = petRepository.pet(id)
    fun vaccinations(): List<VaccinationRecord> = vaccinationRepository.vaccinations()
    fun medicalRecords(): List<MedicalRecord> = medicalRecordRepository.medicalRecords()
    fun appointments(): List<Appointment> = appointmentRepository.appointments()
    fun predictions(): List<BreedPrediction> = aiScanRepository.predictions()

    //read the most recent connection check
    fun systemHealth(): SystemHealth? = systemHealthRepository.systemHealth()

    //calculate dashboard totals from the loaded records
    fun stats(): DashboardStats
    {
        return DashboardCalculator.calculate(
            pets = pets(),
            vaccinations = vaccinations(),
            appointments = appointments(),
            medicalRecords = medicalRecords()
        )
    }

    //read a pet name for record labels
    fun petName(petId: Long): String
    {
        return pet(petId)?.name ?: unknownPetName
    }
}
