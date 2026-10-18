package com.example.pawcareai.dashboard

import android.widget.LinearLayout
import com.example.pawcareai.R
import com.example.pawcareai.data.VaccinationRecord
import com.example.pawcareai.validation.HealthInputValidator
import com.google.android.material.card.MaterialCardView

//the vaccination list, card and form on the health tab
class VaccinationsSection(
    private val page: DashboardPage,
    private val petDialog: PetDialog,
    private val onChanged: () -> Unit
)
{
    private val context = page.activity

    //api values offered by the status dropdown
    private val statusOptions: List<String> = listOf("Upcoming", "Completed", "Overdue")

    //2.input

    //create or update a vaccination linked to the selected pet
    fun showDialog(existing: VaccinationRecord? = null)
    {
        val pets = page.repository.pets()
        if (pets.isEmpty())
        {
            return page.requirePetFirst { petDialog.show() }
        }
        val body = context.dialogBody()
        val pet = body.petSpinner(pets, existing?.petId)
        val vaccine = body.field(context.getString(R.string.field_vaccine_name), existing?.vaccineName)
        val administered = body.dateField(context.getString(R.string.field_administered_date), existing?.administeredDate)
        val due = body.dateField(context.getString(R.string.field_due_date), existing?.dueDate)
        val clinic = body.field(context.getString(R.string.field_clinic_optional), existing?.clinic)
        val status = body.spinner(context.getString(R.string.field_status), statusOptions, existing?.status)
        val notes = body.field(context.getString(R.string.field_notes), existing?.notes, lines = 2)
        page.showSaveDialog(
            title = context.getString(if (existing == null) R.string.vaccination_dialog_add else R.string.vaccination_dialog_edit),
            body = body,
            validate = {
                val validation = HealthInputValidator.validateVaccination(
                    vaccineName = vaccine.text.toString(),
                    administeredDate = administered.text.toString(),
                    dueDate = due.text.toString()
                )
                vaccine.error = validation.vaccineNameError
                administered.error = validation.administeredDateError
                due.error = validation.dueDateError
                validation.firstError
            },
            save = { complete ->
                val vaccination = VaccinationRecord(
                    id = existing?.id ?: 0,
                    petId = pets[pet.selectedItemPosition].id,
                    vaccineName = vaccine.text.toString().trim(),
                    administeredDate = administered.text.toString(),
                    dueDate = due.text.toString(),
                    clinic = clinic.text.toString().trim(),
                    status = status.selectedItem.toString(),
                    notes = notes.text.toString().trim()
                )
                page.repository.saveVaccination(vaccination) { result ->
                    complete(result.map { Unit })
                }
            },
            onSaved = onChanged
        )
    }

    //3.process

    //delete a vaccination after the user confirms
    private fun confirmDelete(item: VaccinationRecord)
    {
        page.confirmDelete(context.getString(R.string.vaccination_delete_title), item.vaccineName) {
            page.repository.deleteVaccination(item.id) { result ->
                page.handleDatabaseResult(result) { onChanged() }
            }
        }
    }

    //4.output

    //list every vaccination, or the empty message
    fun showList(list: LinearLayout)
    {
        list.showRecords(
            page.repository.vaccinations(),
            context.getString(R.string.vaccinations_empty_title),
            context.getString(R.string.vaccinations_empty_message),
            ::vaccinationCard
        )
    }

    //create a vaccination card with its saved care details
    private fun vaccinationCard(item: VaccinationRecord): MaterialCardView
    {
        val vaccinationDetails: String = listOfNotNull(
            item.status,
            item.clinic.takeIf { it.isNotBlank() },
            item.administeredDate.takeIf { it.isNotBlank() }?.let { context.getString(R.string.vaccination_given, context.prettyDate(it)) },
            item.notes.takeIf { it.isNotBlank() }
        ).joinToString(" · ")

        return context.recordCard(
            title = item.vaccineName,
            subtitle = context.getString(R.string.vaccination_subtitle, page.repository.petName(item.petId), context.prettyDate(item.dueDate)),
            detail = vaccinationDetails,
            onEdit = { showDialog(item) },
            onDelete = { confirmDelete(item) }
        )
    }
}
