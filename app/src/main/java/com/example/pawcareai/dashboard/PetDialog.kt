package com.example.pawcareai.dashboard

import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import com.example.pawcareai.R
import com.example.pawcareai.data.Pet
import com.example.pawcareai.validation.PetInputValidator

//create or update a pet profile after validating the form
class PetDialog(
    private val page: DashboardPage,
    private val onSaved: () -> Unit
)
{
    //api values offered by the dropdowns
    private val speciesOptions: List<String> = listOf("Dog", "Cat")
    private val sexOptions: List<String> = listOf("Unknown", "Female", "Male")

    //the inputs of the dialog currently on screen
    private class PetForm(
        val name: EditText,
        val species: Spinner,
        val breed: EditText,
        val sex: Spinner,
        val birthDate: EditText,
        val weight: EditText,
        val microchip: EditText,
        val notes: EditText
    )

    //1.screen setup

    //open the pet form, filled in when editing
    fun show(existing: Pet? = null)
    {
        val context = page.activity
        val body = context.dialogBody()
        val form = createForm(body, existing)
        val title = if (existing == null)
        {
            context.getString(R.string.pet_dialog_add)
        }
        else
        {
            context.getString(R.string.pet_dialog_edit, existing.name)
        }
        page.showSaveDialog(
            title = title,
            body = body,
            validate = { validate(form) },
            save = { complete -> savePet(form, existing, complete) },
            onSaved = onSaved
        )
    }

    //2.input

    //add the pet inputs to the dialog body
    private fun createForm(body: LinearLayout, existing: Pet?): PetForm
    {
        val context = page.activity
        val savedWeight: String? = existing?.weightKg?.takeIf { weightKg -> weightKg > 0 }?.clean()
        return PetForm(
            name = body.field(context.getString(R.string.field_pet_name), existing?.name),
            species = body.spinner(context.getString(R.string.field_species), speciesOptions, existing?.species),
            breed = body.field(context.getString(R.string.field_breed), existing?.breed),
            sex = body.spinner(context.getString(R.string.field_sex), sexOptions, existing?.sex),
            birthDate = body.dateField(context.getString(R.string.field_birth_date), existing?.birthDate),
            weight = body.field(context.getString(R.string.field_weight), savedWeight, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL),
            microchip = body.field(context.getString(R.string.field_microchip), existing?.microchipNumber),
            notes = body.field(context.getString(R.string.field_care_notes), existing?.notes, lines = 3)
        )
    }

    //3.process

    //show field errors and return the first one, or null when the form is valid
    private fun validate(form: PetForm): String?
    {
        val validation = PetInputValidator.validate(
            name = form.name.text.toString(),
            birthDate = form.birthDate.text.toString(),
            weightKg = form.weight.text.toString()
        )
        form.name.error = validation.nameError
        form.birthDate.error = validation.birthDateError
        form.weight.error = validation.weightError
        return if (validation.isValid) null else validation.firstError
    }

    //refresh the screen only after saving succeeds
    private fun savePet(form: PetForm, existing: Pet?, complete: (Result<Unit>) -> Unit)
    {
        val petRecord = Pet(
            id = existing?.id ?: 0,
            name = form.name.text.toString().trim(),
            species = form.species.selectedItem.toString(),
            breed = form.breed.text.toString().trim(),
            sex = form.sex.selectedItem.toString(),
            birthDate = form.birthDate.text.toString().trim(),
            weightKg = form.weight.text.toString().toDoubleOrNull() ?: 0.0,
            microchipNumber = form.microchip.text.toString().trim(),
            notes = form.notes.text.toString().trim()
        )
        page.repository.savePet(petRecord) { result ->
            complete(result.map { Unit })
        }
    }
}
