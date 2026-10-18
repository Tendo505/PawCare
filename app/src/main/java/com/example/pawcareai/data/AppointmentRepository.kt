package com.example.pawcareai.data

import com.example.pawcareai.network.ApiAppointment
import com.example.pawcareai.network.AppointmentRequest
import com.example.pawcareai.network.PawCareApi
import retrofit2.Call

//clinic appointments loaded through laravel
class AppointmentRepository(private val api: PawCareApi)
{
    //temporary screen data loaded through laravel
    private var appointmentCache: List<Appointment> = emptyList()

    //2.input

    //prepare appointment details for saving
    private fun Appointment.toRequest(): AppointmentRequest
    {
        return AppointmentRequest(
            petId = petId,
            appointmentDate = appointmentDate,
            appointmentTime = appointmentTime.take(5),
            clinic = clinic.trim().nullIfBlank(),
            reason = reason.trim(),
            status = status,
            notes = notes.trim().nullIfBlank()
        )
    }

    //3.process

    //create or update an appointment
    fun saveAppointment(appointment: Appointment, onResult: (Result<Appointment>) -> Unit)
    {
        val saveCall: Call<ApiAppointment> = if (appointment.id > 0)
        {
            api.updateAppointment(appointment.id, appointment.toRequest())
        }
        else
        {
            api.createAppointment(appointment.toRequest())
        }

        saveCall.saveResult(
            convert = { it.toAppointment() },
            updateCache = { saved -> appointmentCache = appointmentCache.replaceOrAdd(saved) { it.id } },
            onResult = onResult
        )
    }

    //delete an appointment
    fun deleteAppointment(id: Long, onResult: (Result<Unit>) -> Unit)
    {
        api.deleteAppointment(id).enqueueEmpty { result ->
            result.onSuccess {
                appointmentCache = appointmentCache.filterNot { appointment -> appointment.id == id }
            }
            onResult(result)
        }
    }

    //replace cached appointments after a full refresh
    fun replaceCache(appointments: List<ApiAppointment>)
    {
        appointmentCache = appointments.map { appointment -> appointment.toAppointment() }
    }

    //remove appointments linked to a deleted pet
    fun removePet(petId: Long)
    {
        appointmentCache = appointmentCache.filterNot { appointment -> appointment.petId == petId }
    }

    //remove appointments belonging to a cleared session
    fun clearCache()
    {
        appointmentCache = emptyList()
    }

    //4.output

    //read appointments by date, then time
    fun appointments(): List<Appointment>
    {
        val appointmentOrder: Comparator<Appointment> = compareBy(
            { appointment -> appointment.appointmentDate },
            { appointment -> appointment.appointmentTime }
        )
        return appointmentCache.sortedWith(appointmentOrder)
    }

    //convert an api appointment for the app
    private fun ApiAppointment.toAppointment(): Appointment
    {
        return Appointment(
            id = id,
            petId = petId,
            appointmentDate = appointmentDate,
            appointmentTime = appointmentTime.take(5),
            clinic = clinic.orEmpty(),
            reason = reason,
            status = status,
            notes = notes.orEmpty()
        )
    }
}
