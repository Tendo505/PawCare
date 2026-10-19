package com.example.pawcareai.dashboard

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.example.pawcareai.R
import com.example.pawcareai.data.Pet
import com.example.pawcareai.validation.PetInputValidator
import com.example.pawcareai.validation.PetValidationResult
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

//the Pets tab (layout: screen_pets.xml): one card per pet, and the "Add pet" / "Edit pet" pop-up form
class PetsScreen(private val page: DashboardPage)
{
    private val views: ViewMaker = page.views
    private val cards: RecordCards = RecordCards(page)

    //the pet being edited in the pop-up, or null when adding a new pet
    private var editingPet: Pet? = null

    //the inputs of the pop-up form that is open now
    private lateinit var nameInput: EditText
    private lateinit var speciesInput: Spinner
    private lateinit var breedInput: EditText
    private lateinit var sexInput: Spinner
    private lateinit var birthDateInput: EditText
    private lateinit var weightInput: EditText
    private lateinit var microchipInput: EditText
    private lateinit var notesInput: EditText

    //1.screen setup

    //draw the pets screen
    fun render()
    {
        page.setTitle(R.string.title_pets)
        val root: LinearLayout = page.inflate(R.layout.screen_pets)
        val addButton: MaterialButton = root.findViewById(R.id.petsAdd)
        addButton.setOnClickListener {
            showPetForm(null)
        }
        showPets(root.findViewById(R.id.petsList))
        page.show(root)
    }

    //2.input

    //open the pop-up form; pass a pet to edit it, or null to add a new one
    fun showPetForm(pet: Pet?)
    {
        editingPet = pet
        val body: LinearLayout = page.form.createFormBody()
        createInputs(body, pet)

        var title: String = page.dashboard.getString(R.string.pet_dialog_add)
        if (pet != null)
        {
            title = page.dashboard.getString(R.string.pet_dialog_edit, pet.name)
        }

        val dialog: AlertDialog = page.form.createSaveDialog(title, body)
        dialog.setOnShowListener {
            //replace the normal Save click so the pop-up stays open when the form has errors
            val saveButton: Button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                onSaveClicked(dialog, saveButton)
            }
        }
        dialog.show()
    }

    //health records must belong to a pet, so the health pop-ups call this when there are no pets yet
    fun askToAddPetFirst()
    {
        val builder: MaterialAlertDialogBuilder = MaterialAlertDialogBuilder(page.dashboard)
        builder.setTitle(R.string.pet_required_title)
        builder.setMessage(R.string.pet_required_message)
        builder.setNegativeButton(R.string.action_cancel, null)
        builder.setPositiveButton(R.string.pet_dialog_add) { alert, which ->
            showPetForm(null)
        }
        builder.show()
    }

    //add every input to the pop-up; when editing, each one starts with the saved value
    private fun createInputs(body: LinearLayout, pet: Pet?)
    {
        val form: FormBuilder = page.form
        var savedWeight: String? = null
        if (pet != null && pet.weightKg > 0)
        {
            savedWeight = TextFormat.weight(pet.weightKg)
        }

        nameInput = form.addTextField(body, text(R.string.field_pet_name), pet?.name, 1)
        speciesInput = form.addDropdown(body, text(R.string.field_species), listOf("Dog", "Cat"), pet?.species)
        breedInput = form.addTextField(body, text(R.string.field_breed), pet?.breed, 1)
        sexInput = form.addDropdown(body, text(R.string.field_sex), listOf("Unknown", "Female", "Male"), pet?.sex)
        birthDateInput = form.addDateField(body, text(R.string.field_birth_date), pet?.birthDate)
        weightInput = form.addDecimalField(body, text(R.string.field_weight), savedWeight)
        microchipInput = form.addTextField(body, text(R.string.field_microchip), pet?.microchipNumber, 1)
        notesInput = form.addTextField(body, text(R.string.field_care_notes), pet?.notes, 3)
    }

    //3.process

    //check the form, then send it to laravel; the pop-up closes only after a successful save
    private fun onSaveClicked(dialog: AlertDialog, saveButton: Button)
    {
        val validation: PetValidationResult = PetInputValidator.validate(
            name = nameInput.text.toString(),
            birthDate = birthDateInput.text.toString(),
            weightKg = weightInput.text.toString()
        )
        nameInput.error = validation.nameError
        birthDateInput.error = validation.birthDateError
        weightInput.error = validation.weightError
        if (!validation.isValid)
        {
            page.toast(validation.firstError)
            return
        }

        //stop a second tap from saving the pet twice
        saveButton.isEnabled = false
        page.repository.savePet(readPet()) { result ->
            saveButton.isEnabled = true
            if (result.isSuccess)
            {
                dialog.dismiss()
                render()
            }
            else
            {
                page.showDatabaseUpdateError()
            }
        }
    }

    //copy the form into a Pet; id 0 tells the repository to create a new pet
    private fun readPet(): Pet
    {
        var id: Long = 0
        val pet: Pet? = editingPet
        if (pet != null)
        {
            id = pet.id
        }

        var weight: Double = 0.0
        val typedWeight: Double? = weightInput.text.toString().toDoubleOrNull()
        if (typedWeight != null)
        {
            weight = typedWeight
        }

        return Pet(
            id = id,
            name = nameInput.text.toString().trim(),
            species = speciesInput.selectedItem.toString(),
            breed = breedInput.text.toString().trim(),
            sex = sexInput.selectedItem.toString(),
            birthDate = birthDateInput.text.toString().trim(),
            weightKg = weight,
            microchipNumber = microchipInput.text.toString().trim(),
            notes = notesInput.text.toString().trim()
        )
    }

    //ask before deleting (DELETE pets/{id}); laravel also deletes the pet's health records
    private fun confirmDelete(pet: Pet)
    {
        val title: String = page.dashboard.getString(R.string.pet_delete_title, pet.name)
        val message: String = page.dashboard.getString(R.string.pet_delete_message)
        page.confirmDelete(title, message) {
            page.repository.deletePet(pet.id) { result ->
                if (result.isSuccess)
                {
                    render()
                }
                else
                {
                    page.showDatabaseUpdateError()
                }
            }
        }
    }

    //4.output

    //add one card per pet, or a message when there are none
    private fun showPets(list: LinearLayout)
    {
        val pets: List<Pet> = page.repository.pets()
        if (pets.isEmpty())
        {
            val title: String = page.dashboard.getString(R.string.pets_empty_title)
            val message: String = page.dashboard.getString(R.string.pets_empty_message)
            views.addWithTopMargin(list, views.messageCard(title, message), 18)
            return
        }
        for (pet in pets)
        {
            views.addWithTopMargin(list, petCard(pet), 14)
        }
    }

    //a pet card: icon, name and breed on top, then sex, weight, birth date and microchip
    private fun petCard(pet: Pet): MaterialCardView
    {
        val body: LinearLayout = views.column(18)
        body.addView(petHeading(pet))

        val weight: String = TextFormat.weight(pet.weightKg)
        val birthDate: String = TextFormat.prettyDate(page.dashboard, pet.birthDate)
        val detailsText: String = page.dashboard.getString(R.string.pet_details, pet.sex, weight, birthDate)
        val details: TextView = views.label(detailsText, 14, false, R.color.paw_muted)
        details.id = R.id.petDetails
        views.addWithTopMargin(body, details, 13)

        val microchipText: String = page.dashboard.getString(R.string.pet_microchip, pet.microchipNumber)
        val microchip: TextView = views.label(microchipText, 13, false, R.color.paw_muted)
        microchip.id = R.id.petMicrochip
        if (pet.microchipNumber.isBlank())
        {
            microchip.visibility = View.GONE
        }
        views.addWithTopMargin(body, microchip, 5)

        val buttons: LinearLayout = cards.editDeleteButtons(R.id.petEdit, R.id.petDelete,
            onEdit = { showPetForm(pet) },
            onDelete = { confirmDelete(pet) }
        )
        views.addWithTopMargin(body, buttons, 12)

        val card: MaterialCardView = views.card(R.color.white)
        card.id = R.id.petCard
        card.addView(body)
        return card
    }

    //the top row of a pet card: a cat or dog emoji, then the name and "breed · species"
    private fun petHeading(pet: Pet): LinearLayout
    {
        val heading: LinearLayout = LinearLayout(page.dashboard)
        heading.gravity = Gravity.CENTER_VERTICAL

        var emoji: String = "🐕"
        if (pet.species == "Cat")
        {
            emoji = "🐈"
        }
        val icon: TextView = views.label(emoji, 34, false, R.color.paw_text)
        icon.id = R.id.petIcon
        heading.addView(icon, LinearLayout.LayoutParams(views.dp(52), ViewGroup.LayoutParams.WRAP_CONTENT))

        val nameColumn: LinearLayout = views.column(0)
        val name: TextView = views.label(pet.name, 21, true, R.color.paw_text)
        name.id = R.id.petName
        nameColumn.addView(name)

        var breedName: String = pet.breed
        if (breedName.isBlank())
        {
            breedName = page.dashboard.getString(R.string.pet_unknown_breed)
        }
        val breedText: String = page.dashboard.getString(R.string.pet_breed_species, breedName, pet.species)
        val breed: TextView = views.label(breedText, 14, false, R.color.paw_muted)
        breed.id = R.id.petBreed
        views.addWithTopMargin(nameColumn, breed, 2)

        heading.addView(nameColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return heading
    }

    //read a label from strings.xml
    private fun text(stringResource: Int): String
    {
        return page.dashboard.getString(stringResource)
    }
}
