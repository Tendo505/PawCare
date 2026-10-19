package com.example.pawcareai.data

import com.example.pawcareai.network.ApiAppointment
import com.example.pawcareai.network.AppointmentRequest
import com.example.pawcareai.network.PawCareApi
import retrofit2.Call

//saves and deletes clinic appointments through laravel, then updates the phone copy
class AppointmentRepository(
    private val api: PawCareApi,
    private val cache: RecordCache
)
{
    //2.input

    //build the json body laravel expects; the time is sent as HH:MM
    private fun createRequest(appointment: Appointment): AppointmentRequest
    {
        return AppointmentRequest(
            petId = appointment.petId,
            appointmentDate = appointment.appointmentDate,
            appointmentTime = appointment.appointmentTime.take(5),
            clinic = blankToNull(appointment.clinic),
            reason = appointment.reason.trim(),
            status = appointment.status,
            notes = blankToNull(appointment.notes)
        )
    }

    //3.process

    //create a new appointment (POST) or update an existing one (PUT appointments/{id})
    fun saveAppointment(appointment: Appointment, onResult: (Result<Appointment>) -> Unit)
    {
        val request: AppointmentRequest = createRequest(appointment)
        val saveCall: Call<ApiAppointment>
        if (appointment.id > 0)
        {
            saveCall = api.updateAppointment(appointment.id, request)
        }
        else
        {
            saveCall = api.createAppointment(request)
        }

        saveCall.enqueue(ApiCallback<ApiAppointment>(
            onSuccess = { apiAppointment ->
                val savedAppointment: Appointment = RecordConverter.toAppointment(apiAppointment)
                putInCache(savedAppointment)
                onResult(Result.success(savedAppointment))
            },
            onFailure = { error ->
                onResult(Result.failure(error))
            }
        ))
    }

    //delete an appointment (DELETE appointments/{id})
    fun deleteAppointment(id: Long, onResult: (Result<Unit>) -> Unit)
    {
        api.deleteAppointment(id).enqueue(EmptyApiCallback(
            onSuccess = {
                cache.appointments.removeAll { appointment -> appointment.id == id }
                onResult(Result.success(Unit))
            },
            onFailure = { error ->
                onResult(Result.failure(error))
            }
        ))
    }

    //replace the appointment with the same id, or add it at the end when it is new
    private fun putInCache(savedAppointment: Appointment)
    {
        for (index in cache.appointments.indices)
        {
            if (cache.appointments[index].id == savedAppointment.id)
            {
                cache.appointments[index] = savedAppointment
                return
            }
        }
        cache.appointments.add(savedAppointment)
    }

    //4.output

    //read appointments by date, and by time when two are on the same day
    fun appointments(): List<Appointment>
    {
        return cache.appointments.sortedWith(compareBy({ appointment -> appointment.appointmentDate }, { appointment -> appointment.appointmentTime }))
    }
}
