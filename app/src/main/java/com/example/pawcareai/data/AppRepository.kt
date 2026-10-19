package com.example.pawcareai.data

import android.content.Context
import com.example.pawcareai.R

//the single class the screens talk to
//it passes each request to the repository for that feature, so the screens never deal with laravel directly
class AppRepository(context: Context)
{
    //1.screen setup

    //shared login session and the phone copy of the records
    private val session: ApiSession = ApiSession(context)
    private val cache: RecordCache = RecordCache(context.getString(R.string.unknown_pet))

    //one repository per feature
    private val authRepository: AuthRepository = AuthRepository(session, cache)
    private val petRepository: PetRepository = PetRepository(session.api, cache)
    private val vaccinationRepository: VaccinationRepository = VaccinationRepository(session.api, cache)
    private val medicalRecordRepository: MedicalRecordRepository = MedicalRecordRepository(session.api, cache)
    private val appointmentRepository: AppointmentRepository = AppointmentRepository(session.api, cache)
    private val aiScanRepository: AiScanRepository = AiScanRepository(session.api, cache)
    private val systemHealthRepository: SystemHealthRepository = SystemHealthRepository(session.api)

    //the signed-in account, or null before the session is restored
    val currentUser: UserAccount?
        get()
        {
            return session.activeUser
        }

    //true when a login token is saved on this phone
    val hasSession: Boolean
        get()
        {
            return session.hasSession()
        }

    //2.input

    //restore the saved login when the app opens
    fun restoreSession(onResult: (Result<UserAccount>) -> Unit)
    {
        authRepository.restoreSession(onResult)
    }

    //create a new account
    fun register(name: String, email: String, password: String, onResult: (Result<UserAccount>) -> Unit)
    {
        authRepository.register(name, email, password, onResult)
    }

    //sign in with an existing account
    fun login(email: String, password: String, onResult: (Result<UserAccount>) -> Unit)
    {
        authRepository.login(email, password, onResult)
    }

    //sign out and forget the saved login
    fun logout(onComplete: () -> Unit)
    {
        authRepository.logout(onComplete)
    }

    //3.process

    //load every record list from laravel (see RecordRefresh for the five steps)
    fun refreshData(onResult: (Result<Unit>) -> Unit)
    {
        val refresh: RecordRefresh = RecordRefresh(session.api, cache, onResult)
        refresh.start()
    }

    //check laravel, the database and the ai service
    fun checkSystemHealth(onResult: (Result<SystemHealth>) -> Unit)
    {
        systemHealthRepository.checkSystemHealth(onResult)
    }

    //save a new or edited pet
    fun savePet(pet: Pet, onResult: (Result<Pet>) -> Unit)
    {
        petRepository.savePet(pet, onResult)
    }

    //delete a pet and its health records
    fun deletePet(id: Long, onResult: (Result<Unit>) -> Unit)
    {
        petRepository.deletePet(id, onResult)
    }

    //save a new or edited vaccination
    fun saveVaccination(record: VaccinationRecord, onResult: (Result<VaccinationRecord>) -> Unit)
    {
        vaccinationRepository.saveVaccination(record, onResult)
    }

    //delete a vaccination
    fun deleteVaccination(id: Long, onResult: (Result<Unit>) -> Unit)
    {
        vaccinationRepository.deleteVaccination(id, onResult)
    }

    //save a new or edited medical record
    fun saveMedicalRecord(record: MedicalRecord, onResult: (Result<MedicalRecord>) -> Unit)
    {
        medicalRecordRepository.saveMedicalRecord(record, onResult)
    }

    //delete a medical record
    fun deleteMedicalRecord(id: Long, onResult: (Result<Unit>) -> Unit)
    {
        medicalRecordRepository.deleteMedicalRecord(id, onResult)
    }

    //save a new or edited appointment
    fun saveAppointment(appointment: Appointment, onResult: (Result<Appointment>) -> Unit)
    {
        appointmentRepository.saveAppointment(appointment, onResult)
    }

    //delete an appointment
    fun deleteAppointment(id: Long, onResult: (Result<Unit>) -> Unit)
    {
        appointmentRepository.deleteAppointment(id, onResult)
    }

    //upload a photo for breed recognition
    fun analyzeBreed(imageBytes: ByteArray, mimeType: String, fileName: String, petId: Long?, onResult: (Result<BreedPrediction>) -> Unit)
    {
        aiScanRepository.analyzeBreed(imageBytes, mimeType, fileName, petId, onResult)
    }

    //4.output

    //pets in alphabetical order
    fun pets(): List<Pet>
    {
        return petRepository.pets()
    }

    //one pet by id, or null when it no longer exists
    fun pet(id: Long): Pet?
    {
        return cache.findPet(id)
    }

    //vaccinations with the nearest due date first
    fun vaccinations(): List<VaccinationRecord>
    {
        return vaccinationRepository.vaccinations()
    }

    //medical records with the latest visit first
    fun medicalRecords(): List<MedicalRecord>
    {
        return medicalRecordRepository.medicalRecords()
    }

    //appointments by date and time
    fun appointments(): List<Appointment>
    {
        return appointmentRepository.appointments()
    }

    //breed scans with the newest first
    fun predictions(): List<BreedPrediction>
    {
        return aiScanRepository.predictions()
    }

    //the most recent service check
    fun systemHealth(): SystemHealth?
    {
        return systemHealthRepository.systemHealth()
    }

    //the four totals on the home screen
    fun stats(): DashboardStats
    {
        return DashboardCalculator.calculate(pets(), vaccinations(), appointments(), medicalRecords())
    }

    //a pet's name for record labels, or "Unknown pet"
    fun petName(petId: Long): String
    {
        return cache.petName(petId)
    }
}
