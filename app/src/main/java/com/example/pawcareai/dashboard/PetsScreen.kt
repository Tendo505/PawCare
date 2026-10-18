package com.example.pawcareai.dashboard

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.example.pawcareai.R
import com.example.pawcareai.data.Pet
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

//the pets tab: every pet profile and the add-pet action
class PetsScreen(
    private val page: DashboardPage,
    private val petDialog: PetDialog
)
{
    private val context = page.activity

    //1.screen setup

    //display all pet profiles and the add-pet action
    fun render()
    {
        page.setTitle(context.getString(R.string.title_pets))
        val root = page.inflate(R.layout.screen_pets)
        root.findViewById<MaterialButton>(R.id.petsAdd).setOnClickListener { petDialog.show() }
        showPets(root.findViewById(R.id.petsList))
        page.show(root)
    }

    //3.process

    //delete a pet after the user confirms
    private fun confirmDeletePet(pet: Pet)
    {
        val title = context.getString(R.string.pet_delete_title, pet.name)
        page.confirmDelete(title, context.getString(R.string.pet_delete_message)) {
            page.repository.deletePet(pet.id) { result ->
                page.handleDatabaseResult(result) { render() }
            }
        }
    }

    //4.output

    //list each pet, or an empty message when there are none
    private fun showPets(list: LinearLayout)
    {
        val pets = page.repository.pets()
        if (pets.isEmpty())
        {
            val emptyTitle = context.getString(R.string.pets_empty_title)
            val emptyMessage = context.getString(R.string.pets_empty_message)
            list.addView(context.emptyCard(emptyTitle, emptyMessage).withTopMargin(18))
        }
        else
        {
            pets.forEach { pet -> list.addView(petCard(pet).withTopMargin(14)) }
        }
    }

    //display each saved pet inside the pet screen
    private fun petCard(pet: Pet): MaterialCardView
    {
        val body = context.vertical(18)
        body.addView(petHeading(pet))
        val details = context.label(
            context.getString(R.string.pet_details, pet.sex, pet.weightKg.clean(), context.prettyDate(pet.birthDate)),
            14, false, R.color.paw_muted
        )
        details.id = R.id.petDetails
        body.addView(details.withTopMargin(13))
        val microchip = context.label(context.getString(R.string.pet_microchip, pet.microchipNumber), 13, false, R.color.paw_muted)
        microchip.id = R.id.petMicrochip
        microchip.visibility = if (pet.microchipNumber.isBlank()) View.GONE else View.VISIBLE
        body.addView(microchip.withTopMargin(5))
        body.addView(context.recordActions(R.id.petEdit, R.id.petDelete,
            onEdit = { petDialog.show(pet) },
            onDelete = { confirmDeletePet(pet) }
        ).withTopMargin(12))
        val container = context.card(R.color.white)
        container.id = R.id.petCard
        container.addView(body)
        return container
    }

    //show the species icon, name and breed in one row
    private fun petHeading(pet: Pet): LinearLayout
    {
        val heading = LinearLayout(context)
        heading.gravity = Gravity.CENTER_VERTICAL
        val icon = context.label(if (pet.species == "Cat") "🐈" else "🐕", 34, false, R.color.paw_text)
        icon.id = R.id.petIcon
        heading.addView(icon, LinearLayout.LayoutParams(context.dp(52), ViewGroup.LayoutParams.WRAP_CONTENT))
        val identity = context.vertical()
        val name = context.label(pet.name, 21, true, R.color.paw_text)
        name.id = R.id.petName
        identity.addView(name)
        val breedName = pet.breed.ifBlank { context.getString(R.string.pet_unknown_breed) }
        val breed = context.label(context.getString(R.string.pet_breed_species, breedName, pet.species), 14, false, R.color.paw_muted)
        breed.id = R.id.petBreed
        identity.addView(breed.withTopMargin(2))
        heading.addView(identity, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return heading
    }
}
