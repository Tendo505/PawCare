package com.example.pawcareai.dashboard

import android.app.DatePickerDialog
import android.content.Context
import android.text.InputType
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.example.pawcareai.R
import com.example.pawcareai.data.Pet
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.LocalDate

//builds the input forms shown inside the add and edit dialogs
class FormBuilder(
    private val context: Context,
    private val views: ViewMaker
)
{
    //1.screen setup

    //create the empty column that the form inputs are added to
    fun createFormBody(): LinearLayout
    {
        val body: LinearLayout = views.column(0)
        body.setPadding(views.dp(2), 0, views.dp(2), views.dp(4))
        return body
    }

    //create a dialog with Cancel and Save buttons around the form
    //the Save click is added later by each dialog, after the dialog is shown
    fun createSaveDialog(title: String, body: LinearLayout): AlertDialog
    {
        //a long form scrolls instead of going off the screen
        val scrollView: ScrollView = ScrollView(context)
        scrollView.setPadding(views.dp(20), 0, views.dp(20), 0)
        scrollView.addView(body)

        val builder: MaterialAlertDialogBuilder = MaterialAlertDialogBuilder(context)
        builder.setTitle(title)
        builder.setView(scrollView)
        builder.setNegativeButton(R.string.action_cancel, null)
        builder.setPositiveButton(R.string.action_save, null)
        return builder.create()
    }

    //2.input

    //add a text input; value is the saved text when editing, or null for a new record
    fun addTextField(body: LinearLayout, hint: String, value: String?, lines: Int): EditText
    {
        return addField(body, hint, value, InputType.TYPE_CLASS_TEXT, lines)
    }

    //add a number input that allows decimals, used for the weight
    fun addDecimalField(body: LinearLayout, hint: String, value: String?): EditText
    {
        return addField(body, hint, value, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL, 1)
    }

    //add a date input; tapping it opens a calendar instead of the keyboard
    fun addDateField(body: LinearLayout, hint: String, value: String?): EditText
    {
        val dateInput: EditText = addField(body, hint, value, InputType.TYPE_CLASS_TEXT, 1)
        dateInput.isFocusable = false
        dateInput.setOnClickListener {
            openCalendar(dateInput)
        }
        return dateInput
    }

    //add a dropdown with a small title above it, and select the saved option when editing
    fun addDropdown(body: LinearLayout, title: String, options: List<String>, selected: String?): Spinner
    {
        var titleMargin: Int = 10
        if (body.childCount == 0)
        {
            titleMargin = 0
        }
        val titleView: TextView = views.label(title, 12, true, R.color.paw_muted)
        views.addWithTopMargin(body, titleView, titleMargin)

        val dropdown: Spinner = Spinner(context)
        dropdown.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, options)
        val selectedIndex: Int = options.indexOf(selected)
        if (selectedIndex >= 0)
        {
            dropdown.setSelection(selectedIndex)
        }
        dropdown.setPadding(views.dp(10), views.dp(8), views.dp(10), views.dp(8))
        body.addView(dropdown, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, views.dp(50)))
        return dropdown
    }

    //add the "Pet" dropdown, showing each pet as "Milo (Cat)"
    fun addPetDropdown(body: LinearLayout, pets: List<Pet>, selectedPetId: Long?): Spinner
    {
        val petOptions: MutableList<String> = mutableListOf()
        var selectedIndex: Int = -1
        for (index in pets.indices)
        {
            val pet: Pet = pets[index]
            petOptions.add(context.getString(R.string.pet_option, pet.name, pet.species))
            if (pet.id == selectedPetId)
            {
                selectedIndex = index
            }
        }

        val dropdown: Spinner = addDropdown(body, context.getString(R.string.field_pet), petOptions, null)
        if (selectedIndex >= 0)
        {
            dropdown.setSelection(selectedIndex)
        }
        return dropdown
    }

    //3.process

    //create one input; inputs after the first get a small gap above them
    private fun addField(body: LinearLayout, hint: String, value: String?, inputType: Int, lines: Int): EditText
    {
        val input: EditText = EditText(context)
        input.hint = hint
        if (value != null)
        {
            input.setText(value)
        }
        input.inputType = inputType
        input.maxLines = lines
        input.minLines = lines
        input.setTextColor(views.color(R.color.paw_text))
        input.setHintTextColor(views.color(R.color.paw_muted))

        val params: LinearLayout.LayoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        if (body.childCount > 0)
        {
            params.topMargin = views.dp(8)
        }
        body.addView(input, params)
        return input
    }

    //open the calendar on the date already typed, or on today, and write the chosen date back
    private fun openCalendar(dateInput: EditText)
    {
        var startDate: LocalDate = LocalDate.now()
        try
        {
            startDate = LocalDate.parse(dateInput.text.toString())
        }
        catch (error: Exception)
        {
            //the field is empty or not a date, so the calendar starts on today
        }

        val calendar: DatePickerDialog = DatePickerDialog(
            context,
            { picker, year, month, day ->
                //android counts months from 0, LocalDate counts from 1
                val chosenDate: LocalDate = LocalDate.of(year, month + 1, day)
                dateInput.setText(chosenDate.toString())
            },
            startDate.year,
            startDate.monthValue - 1,
            startDate.dayOfMonth
        )
        calendar.show()
    }
}
