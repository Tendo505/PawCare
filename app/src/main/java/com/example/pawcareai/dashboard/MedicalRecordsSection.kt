package com.example.pawcareai.dashboard

import android.widget.LinearLayout
import com.example.pawcareai.R
import com.example.pawcareai.data.MedicalRecord
import com.example.pawcareai.validation.HealthInputValidator
import com.google.android.material.card.MaterialCardView

//the medical history list, card and form on the health tab
class MedicalRecordsSection(
    private val page: DashboardPage,
    private val petDialog: PetDialog,
    private val onChanged: () -> Unit
)
{
    private val context = page.activity

    //2.input

    //create or update a medical visit record for the selected pet
    fun showDialog(existing: MedicalRecord? = null)
    {
        val pets = page.repository.pets()
        if (pets.isEmpty())
        {
            return page.requirePetFirst { petDialog.show() }
        }
        val body = context.dialogBody()
        val pet = body.petSpinner(pets, existing?.petId)
        val date = body.dateField(context.getString(R.string.field_visit_date), existing?.visitDate)
        val veterinarian = body.field(context.getString(R.string.field_veterinarian), existing?.veterinarian)
        val diagnosis = body.field(context.getString(R.string.field_diagnosis), existing?.diagnosis)
        val treatment = body.field(context.getString(R.string.field_treatment), existing?.treatment, lines = 2)
        val notes = body.field(context.getString(R.string.field_notes), existing?.notes, lines = 3)
        page.showSaveDialog(
            title = context.getString(if (existing == null) R.string.medical_dialog_add else R.string.medical_dialog_edit),
            body = body,
            validate = {
                val validation = HealthInputValidator.validateMedicalRecord(
                    visitDate = date.text.toString(),
                    diagnosis = diagnosis.text.toString()
                )
                date.error = validation.visitDateError
                diagnosis.error = validation.diagnosisError
                validation.firstError
            },
            save = { complete ->
                val medicalRecord = MedicalRecord(
                    id = existing?.id ?: 0,
                    petId = pets[pet.selectedItemPosition].id,
                    visitDate = date.text.toString(),
                    veterinarian = veterinarian.text.toString().trim(),
                    diagnosis = diagnosis.text.toString().trim(),
                    treatment = treatment.text.toString().trim(),
                    notes = notes.text.toString().trim()
                )
                page.repository.saveMedicalRecord(medicalRecord) { result ->
                    complete(result.map { Unit })
                }
            },
            onSaved = onChanged
        )
    }

    //3.process

    //delete a medical record after the user confirms
    private fun confirmDelete(item: MedicalRecord)
    {
        page.confirmDelete(context.getString(R.string.medical_delete_title), item.diagnosis) {
            page.repository.deleteMedicalRecord(item.id) { result ->
                page.handleDatabaseResult(result) { onChanged() }
            }
        }
    }

    //4.output

    //list every medical record, or the empty message
    fun showList(list: LinearLayout)
    {
        list.showRecords(
            page.repository.medicalRecords(),
            context.getString(R.string.medical_empty_title),
            context.getString(R.string.medical_empty_message),
            ::medicalCard
        )
    }

    //create a medical record card with the visit outcome
    private fun medicalCard(item: MedicalRecord): MaterialCardView
    {
        val medicalDetails: String = listOf(item.veterinarian, item.treatment)
            .filter { detail -> detail.isNotBlank() }
            .joinToString(" · ")

        return context.recordCard(
            title = item.diagnosis,
            subtitle = context.getString(R.string.medical_subtitle, page.repository.petName(item.petId), context.prettyDate(item.visitDate)),
            detail = medicalDetails,
            onEdit = { showDialog(item) },
            onDelete = { confirmDelete(item) }
        )
    }
}
