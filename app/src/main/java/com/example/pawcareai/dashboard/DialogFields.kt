package com.example.pawcareai.dashboard

import android.app.DatePickerDialog
import android.content.Context
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import com.example.pawcareai.R
import com.example.pawcareai.data.Pet
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.LocalDate

//form fields and the save dialog shared by every record form

//1.screen setup

//create the input area inside a dialog
fun Context.dialogBody(): LinearLayout
{
    val body = vertical()
    body.setPadding(dp(2), 0, dp(2), dp(4))
    return body
}

//allow a long dialog to scroll
private fun Context.scrollDialog(body: View): ScrollView
{
    val scrollView = ScrollView(this)
    scrollView.setPadding(dp(20), 0, dp(20), 0)
    scrollView.addView(body)
    return scrollView
}

//2.input

//select the pet linked to a record
fun LinearLayout.petSpinner(pets: List<Pet>, selectedId: Long?): Spinner
{
    val petOptions = pets.map { context.getString(R.string.pet_option, it.name, it.species) }
    val petInput = spinner(context.getString(R.string.field_pet), petOptions)
    val selectedIndex = pets.indexOfFirst { it.id == selectedId }
    if (selectedIndex >= 0) petInput.setSelection(selectedIndex)
    return petInput
}

//create a labelled dropdown
fun LinearLayout.spinner(title: String, options: List<String>, selected: String? = null): Spinner
{
    val titleMargin = if (childCount == 0) 0 else 10
    addView(context.label(title, 12, true, R.color.paw_muted).withTopMargin(titleMargin))

    val dropdown = Spinner(context)
    dropdown.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, options)
    val selectedIndex = options.indexOf(selected)
    if (selectedIndex >= 0) dropdown.setSelection(selectedIndex)
    dropdown.setPadding(context.dp(10), context.dp(8), context.dp(10), context.dp(8))
    addView(dropdown, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(50)))
    return dropdown
}

//create a form input
fun LinearLayout.field(
    hint: String,
    value: String? = null,
    inputType: Int = InputType.TYPE_CLASS_TEXT,
    lines: Int = 1
): EditText
{
    val input = EditText(context)
    input.hint = hint
    input.setText(value.orEmpty())
    input.inputType = inputType
    input.maxLines = lines
    input.minLines = lines
    input.setTextColor(context.color(R.color.paw_text))
    input.setHintTextColor(context.color(R.color.paw_muted))

    val inputLayout = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    inputLayout.topMargin = context.dp(if (childCount == 0) 0 else 8)
    addView(input, inputLayout)
    return input
}

//choose a date with the calendar
fun LinearLayout.dateField(hint: String, value: String? = null): EditText
{
    val dateInput = field(hint, value)
    dateInput.isFocusable = false
    dateInput.setOnClickListener {
        val initialDate = runCatching { LocalDate.parse(dateInput.text.toString()) }
            .getOrDefault(LocalDate.now())
        val datePicker = DatePickerDialog(
            context,
            { _, year, month, day -> dateInput.setText(LocalDate.of(year, month + 1, day).toString()) },
            initialDate.year,
            initialDate.monthValue - 1,
            initialDate.dayOfMonth
        )
        datePicker.show()
    }
    return dateInput
}

//3.process

//save a validated form while preventing duplicate button submissions
fun DashboardPage.showSaveDialog(
    title: String,
    body: LinearLayout,
    validate: () -> String?,
    save: ((Result<Unit>) -> Unit) -> Unit,
    onSaved: () -> Unit
)
{
    val saveDialog = MaterialAlertDialogBuilder(activity)
    saveDialog.setTitle(title)
    saveDialog.setView(activity.scrollDialog(body))
    saveDialog.setNegativeButton(R.string.action_cancel, null)
    saveDialog.setPositiveButton(R.string.action_save, null)
    val dialog = saveDialog.create()
    dialog.setOnShowListener {
        val saveButton = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
        saveButton.setOnClickListener {
            val error = validate()
            if (error != null)
            {
                toast(error)
                return@setOnClickListener
            }
            saveButton.isEnabled = false
            save { result ->
                saveButton.isEnabled = true
                handleDatabaseResult(result) {
                    dialog.dismiss()
                    onSaved()
                }
            }
        }
    }
    dialog.show()
}

//request a pet profile before adding health records
fun DashboardPage.requirePetFirst(addPet: () -> Unit)
{
    MaterialAlertDialogBuilder(activity)
        .setTitle(R.string.pet_required_title)
        .setMessage(R.string.pet_required_message)
        .setNegativeButton(R.string.action_cancel, null)
        .setPositiveButton(R.string.pet_dialog_add) { _, _ -> addPet() }
        .show()
}
