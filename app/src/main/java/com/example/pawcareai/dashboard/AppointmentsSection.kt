package com.example.pawcareai.dashboard

import android.widget.LinearLayout
import com.example.pawcareai.R
import com.example.pawcareai.data.Appointment
import com.example.pawcareai.data.AppointmentReminderCalculator
import com.example.pawcareai.validation.AppointmentInputValidator
import com.google.android.material.card.MaterialCardView

//the appointment list, card and booking form on the health tab
class AppointmentsSection(
    private val page: DashboardPage,
    private val petDialog: PetDialog,
    private val onChanged: () -> Unit
)
{
    private val context = page.activity

    //api values offered by the status dropdown
    private val statusOptions: List<String> = listOf("Scheduled", "Completed", "Cancelled")

    //2.input

    //create or update a clinic appointment after checking its date and time
    fun showDialog(existing: Appointment? = null)
    {
        val pets = page.repository.pets()
        if (pets.isEmpty())
        {
            return page.requirePetFirst { petDialog.show() }
        }
        val body = context.dialogBody()
        val pet = body.petSpinner(pets, existing?.petId)
        val date = body.dateField(context.getString(R.string.field_appointment_date), existing?.appointmentDate)
        val time = body.field(context.getString(R.string.field_time), existing?.appointmentTime ?: "09:00")
        val clinic = body.field(context.getString(R.string.field_clinic), existing?.clinic)
        val reason = body.field(context.getString(R.string.field_reason), existing?.reason)
        val status = body.spinner(context.getString(R.string.field_status), statusOptions, existing?.status)
        val notes = body.field(context.getString(R.string.field_notes), existing?.notes, lines = 3)
        page.showSaveDialog(
            title = context.getString(if (existing == null) R.string.appointment_dialog_add else R.string.appointment_dialog_edit),
            body = body,
            validate = {
                val validation = AppointmentInputValidator.validate(
                    appointmentDate = date.text.toString(),
                    appointmentTime = time.text.toString(),
                    reason = reason.text.toString(),
                    status = status.selectedItem.toString()
                )
                date.error = validation.dateError
                time.error = validation.timeError
                reason.error = validation.reasonError
                validation.firstError()
            },
            save = { complete ->
                val appointment = Appointment(
                    id = existing?.id ?: 0,
                    petId = pets[pet.selectedItemPosition].id,
                    appointmentDate = date.text.toString(),
                    appointmentTime = time.text.toString(),
                    clinic = clinic.text.toString().trim(),
                    reason = reason.text.toString().trim(),
                    status = status.selectedItem.toString(),
                    notes = notes.text.toString().trim()
                )
                page.repository.saveAppointment(appointment) { result ->
                    complete(result.map { Unit })
                }
            },
            onSaved = onChanged
        )
    }

    //3.process

    //delete an appointment after the user confirms
    private fun confirmDelete(item: Appointment)
    {
        page.confirmDelete(context.getString(R.string.appointment_delete_title), item.reason) {
            page.repository.deleteAppointment(item.id) { result ->
                page.handleDatabaseResult(result) { onChanged() }
            }
        }
    }

    //4.output

    //list every appointment, or the empty message
    fun showList(list: LinearLayout)
    {
        list.showRecords(
            page.repository.appointments(),
            context.getString(R.string.appointments_empty_title),
            context.getString(R.string.appointments_empty_message),
            ::appointmentCard
        )
    }

    //create an appointment card with its reminder and status
    private fun appointmentCard(item: Appointment): MaterialCardView
    {
        val appointmentDetails: String = listOfNotNull(
            item.status,
            item.clinic.takeIf { it.isNotBlank() },
            AppointmentReminderCalculator.labelFor(item),
            item.notes.takeIf { it.isNotBlank() }
        ).joinToString(" · ")
        val subtitle = context.getString(
            R.string.appointment_subtitle,
            page.repository.petName(item.petId),
            context.prettyDate(item.appointmentDate),
            item.appointmentTime
        )

        return context.recordCard(
            title = item.reason,
            subtitle = subtitle,
            detail = appointmentDetails,
            onEdit = { showDialog(item) },
            onDelete = { confirmDelete(item) }
        )
    }
}
