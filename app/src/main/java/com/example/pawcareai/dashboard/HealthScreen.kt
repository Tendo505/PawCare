package com.example.pawcareai.dashboard

import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import com.example.pawcareai.R
import com.example.pawcareai.data.Appointment
import com.example.pawcareai.data.AppointmentReminderCalculator
import com.example.pawcareai.data.MedicalRecord
import com.example.pawcareai.data.Pet
import com.example.pawcareai.data.VaccinationRecord
import com.example.pawcareai.validation.AppointmentInputValidator
import com.example.pawcareai.validation.AppointmentValidationResult
import com.example.pawcareai.validation.HealthInputValidator
import com.example.pawcareai.validation.MedicalRecordValidationResult
import com.example.pawcareai.validation.VaccinationValidationResult
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

//the Health tab (layout: screen_health.xml) shows three lists, each with its own pop-up form:
//vaccinations, appointments and medical records
class HealthScreen(
    private val page: DashboardPage,
    private val petsScreen: PetsScreen
)
{
    private val views: ViewMaker = page.views
    private val cards: RecordCards = RecordCards(page)

    //the pets offered in the "Pet" dropdown of the pop-up that is open now
    private var petChoices: List<Pet> = emptyList()

    //the record being edited in each pop-up, or null when adding a new one
    private var editingVaccination: VaccinationRecord? = null
    private var editingAppointment: Appointment? = null
    private var editingMedicalRecord: MedicalRecord? = null

    //vaccination pop-up inputs
    private lateinit var vaccinePetInput: Spinner
    private lateinit var vaccineNameInput: EditText
    private lateinit var vaccineGivenInput: EditText
    private lateinit var vaccineDueInput: EditText
    private lateinit var vaccineClinicInput: EditText
    private lateinit var vaccineStatusInput: Spinner
    private lateinit var vaccineNotesInput: EditText

    //appointment pop-up inputs
    private lateinit var visitPetInput: Spinner
    private lateinit var visitDateInput: EditText
    private lateinit var visitTimeInput: EditText
    private lateinit var visitClinicInput: EditText
    private lateinit var visitReasonInput: EditText
    private lateinit var visitStatusInput: Spinner
    private lateinit var visitNotesInput: EditText

    //medical record pop-up inputs
    private lateinit var medicalPetInput: Spinner
    private lateinit var medicalDateInput: EditText
    private lateinit var medicalVetInput: EditText
    private lateinit var medicalDiagnosisInput: EditText
    private lateinit var medicalTreatmentInput: EditText
    private lateinit var medicalNotesInput: EditText

    //1.screen setup

    //draw the health screen: three "add" buttons and three lists
    fun render()
    {
        page.setTitle(R.string.title_health)
        val root: LinearLayout = page.inflate(R.layout.screen_health)

        val addVaccineButton: MaterialButton = root.findViewById(R.id.healthAddVaccine)
        addVaccineButton.setOnClickListener {
            showVaccinationForm(null)
        }
        val bookVisitButton: MaterialButton = root.findViewById(R.id.healthBookVisit)
        bookVisitButton.setOnClickListener {
            showAppointmentForm(null)
        }
        val addRecordButton: MaterialButton = root.findViewById(R.id.healthAddRecord)
        addRecordButton.setOnClickListener {
            showMedicalRecordForm(null)
        }

        showVaccinations(root.findViewById(R.id.vaccinationList))
        showAppointments(root.findViewById(R.id.appointmentList))
        showMedicalRecords(root.findViewById(R.id.medicalList))
        page.show(root)
    }

    //2.input

    //open the vaccination pop-up; pass a record to edit it, or null to add a new one
    fun showVaccinationForm(record: VaccinationRecord?)
    {
        petChoices = page.repository.pets()
        if (petChoices.isEmpty())
        {
            petsScreen.askToAddPetFirst()
            return
        }
        editingVaccination = record
        val form: FormBuilder = page.form
        val body: LinearLayout = form.createFormBody()
        vaccinePetInput = form.addPetDropdown(body, petChoices, record?.petId)
        vaccineNameInput = form.addTextField(body, text(R.string.field_vaccine_name), record?.vaccineName, 1)
        vaccineGivenInput = form.addDateField(body, text(R.string.field_administered_date), record?.administeredDate)
        vaccineDueInput = form.addDateField(body, text(R.string.field_due_date), record?.dueDate)
        vaccineClinicInput = form.addTextField(body, text(R.string.field_clinic_optional), record?.clinic, 1)
        vaccineStatusInput = form.addDropdown(body, text(R.string.field_status), listOf("Upcoming", "Completed", "Overdue"), record?.status)
        vaccineNotesInput = form.addTextField(body, text(R.string.field_notes), record?.notes, 2)

        var title: String = text(R.string.vaccination_dialog_add)
        if (record != null)
        {
            title = text(R.string.vaccination_dialog_edit)
        }
        val dialog: AlertDialog = form.createSaveDialog(title, body)
        dialog.setOnShowListener {
            //replace the normal Save click so the pop-up stays open when the form has errors
            val saveButton: Button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                saveVaccination(dialog, saveButton)
            }
        }
        dialog.show()
    }

    //open the appointment pop-up; pass an appointment to edit it, or null to book a new one
    //the Home screen's "Book visit" button also opens this
    fun showAppointmentForm(appointment: Appointment?)
    {
        petChoices = page.repository.pets()
        if (petChoices.isEmpty())
        {
            petsScreen.askToAddPetFirst()
            return
        }
        editingAppointment = appointment
        var savedTime: String = "09:00"
        if (appointment != null)
        {
            savedTime = appointment.appointmentTime
        }
        val form: FormBuilder = page.form
        val body: LinearLayout = form.createFormBody()
        visitPetInput = form.addPetDropdown(body, petChoices, appointment?.petId)
        visitDateInput = form.addDateField(body, text(R.string.field_appointment_date), appointment?.appointmentDate)
        visitTimeInput = form.addTextField(body, text(R.string.field_time), savedTime, 1)
        visitClinicInput = form.addTextField(body, text(R.string.field_clinic), appointment?.clinic, 1)
        visitReasonInput = form.addTextField(body, text(R.string.field_reason), appointment?.reason, 1)
        visitStatusInput = form.addDropdown(body, text(R.string.field_status), listOf("Scheduled", "Completed", "Cancelled"), appointment?.status)
        visitNotesInput = form.addTextField(body, text(R.string.field_notes), appointment?.notes, 3)

        var title: String = text(R.string.appointment_dialog_add)
        if (appointment != null)
        {
            title = text(R.string.appointment_dialog_edit)
        }
        val dialog: AlertDialog = form.createSaveDialog(title, body)
        dialog.setOnShowListener {
            //replace the normal Save click so the pop-up stays open when the form has errors
            val saveButton: Button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                saveAppointment(dialog, saveButton)
            }
        }
        dialog.show()
    }

    //open the medical record pop-up; pass a record to edit it, or null to add a new one
    fun showMedicalRecordForm(record: MedicalRecord?)
    {
        petChoices = page.repository.pets()
        if (petChoices.isEmpty())
        {
            petsScreen.askToAddPetFirst()
            return
        }
        editingMedicalRecord = record
        val form: FormBuilder = page.form
        val body: LinearLayout = form.createFormBody()
        medicalPetInput = form.addPetDropdown(body, petChoices, record?.petId)
        medicalDateInput = form.addDateField(body, text(R.string.field_visit_date), record?.visitDate)
        medicalVetInput = form.addTextField(body, text(R.string.field_veterinarian), record?.veterinarian, 1)
        medicalDiagnosisInput = form.addTextField(body, text(R.string.field_diagnosis), record?.diagnosis, 1)
        medicalTreatmentInput = form.addTextField(body, text(R.string.field_treatment), record?.treatment, 2)
        medicalNotesInput = form.addTextField(body, text(R.string.field_notes), record?.notes, 3)

        var title: String = text(R.string.medical_dialog_add)
        if (record != null)
        {
            title = text(R.string.medical_dialog_edit)
        }
        val dialog: AlertDialog = form.createSaveDialog(title, body)
        dialog.setOnShowListener {
            //replace the normal Save click so the pop-up stays open when the form has errors
            val saveButton: Button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                saveMedicalRecord(dialog, saveButton)
            }
        }
        dialog.show()
    }

    //3.process

    //check the vaccination form, then send it to laravel (POST or PUT vaccinations)
    private fun saveVaccination(dialog: AlertDialog, saveButton: Button)
    {
        val validation: VaccinationValidationResult = HealthInputValidator.validateVaccination(
            vaccineName = vaccineNameInput.text.toString(),
            administeredDate = vaccineGivenInput.text.toString(),
            dueDate = vaccineDueInput.text.toString()
        )
        vaccineNameInput.error = validation.vaccineNameError
        vaccineGivenInput.error = validation.administeredDateError
        vaccineDueInput.error = validation.dueDateError
        if (validation.firstError != null)
        {
            page.toast(validation.firstError)
            return
        }

        //id 0 tells the repository to create a new record
        var id: Long = 0
        val editing: VaccinationRecord? = editingVaccination
        if (editing != null)
        {
            id = editing.id
        }
        val record: VaccinationRecord = VaccinationRecord(
            id = id,
            petId = petChoices[vaccinePetInput.selectedItemPosition].id,
            vaccineName = vaccineNameInput.text.toString().trim(),
            administeredDate = vaccineGivenInput.text.toString(),
            dueDate = vaccineDueInput.text.toString(),
            clinic = vaccineClinicInput.text.toString().trim(),
            status = vaccineStatusInput.selectedItem.toString(),
            notes = vaccineNotesInput.text.toString().trim()
        )

        //stop a second tap from saving twice; close the pop-up only after a successful save
        saveButton.isEnabled = false
        page.repository.saveVaccination(record) { result ->
            saveButton.isEnabled = true
            finishSave(dialog, result.isSuccess)
        }
    }

    //check the appointment form, then send it to laravel (POST or PUT appointments)
    private fun saveAppointment(dialog: AlertDialog, saveButton: Button)
    {
        val validation: AppointmentValidationResult = AppointmentInputValidator.validate(
            appointmentDate = visitDateInput.text.toString(),
            appointmentTime = visitTimeInput.text.toString(),
            reason = visitReasonInput.text.toString(),
            status = visitStatusInput.selectedItem.toString()
        )
        visitDateInput.error = validation.dateError
        visitTimeInput.error = validation.timeError
        visitReasonInput.error = validation.reasonError
        val firstError: String? = validation.firstError()
        if (firstError != null)
        {
            page.toast(firstError)
            return
        }

        //id 0 tells the repository to create a new appointment
        var id: Long = 0
        val editing: Appointment? = editingAppointment
        if (editing != null)
        {
            id = editing.id
        }
        val appointment: Appointment = Appointment(
            id = id,
            petId = petChoices[visitPetInput.selectedItemPosition].id,
            appointmentDate = visitDateInput.text.toString(),
            appointmentTime = visitTimeInput.text.toString(),
            clinic = visitClinicInput.text.toString().trim(),
            reason = visitReasonInput.text.toString().trim(),
            status = visitStatusInput.selectedItem.toString(),
            notes = visitNotesInput.text.toString().trim()
        )

        //stop a second tap from booking twice; close the pop-up only after a successful save
        saveButton.isEnabled = false
        page.repository.saveAppointment(appointment) { result ->
            saveButton.isEnabled = true
            finishSave(dialog, result.isSuccess)
        }
    }

    //check the medical record form, then send it to laravel (POST or PUT medical-records)
    private fun saveMedicalRecord(dialog: AlertDialog, saveButton: Button)
    {
        val validation: MedicalRecordValidationResult = HealthInputValidator.validateMedicalRecord(
            visitDate = medicalDateInput.text.toString(),
            diagnosis = medicalDiagnosisInput.text.toString()
        )
        medicalDateInput.error = validation.visitDateError
        medicalDiagnosisInput.error = validation.diagnosisError
        if (validation.firstError != null)
        {
            page.toast(validation.firstError)
            return
        }

        //id 0 tells the repository to create a new record
        var id: Long = 0
        val editing: MedicalRecord? = editingMedicalRecord
        if (editing != null)
        {
            id = editing.id
        }
        val record: MedicalRecord = MedicalRecord(
            id = id,
            petId = petChoices[medicalPetInput.selectedItemPosition].id,
            visitDate = medicalDateInput.text.toString(),
            veterinarian = medicalVetInput.text.toString().trim(),
            diagnosis = medicalDiagnosisInput.text.toString().trim(),
            treatment = medicalTreatmentInput.text.toString().trim(),
            notes = medicalNotesInput.text.toString().trim()
        )

        //stop a second tap from saving twice; close the pop-up only after a successful save
        saveButton.isEnabled = false
        page.repository.saveMedicalRecord(record) { result ->
            saveButton.isEnabled = true
            finishSave(dialog, result.isSuccess)
        }
    }

    //after laravel answers: close the pop-up and redraw the lists, or show an error and keep the pop-up open
    private fun finishSave(dialog: AlertDialog, saved: Boolean)
    {
        if (saved)
        {
            dialog.dismiss()
            render()
        }
        else
        {
            page.showDatabaseUpdateError()
        }
    }

    //after a delete request: redraw the lists, or show an error
    private fun finishDelete(deleted: Boolean)
    {
        if (deleted)
        {
            render()
        }
        else
        {
            page.showDatabaseUpdateError()
        }
    }

    //4.output

    //one card per vaccination, or a message when there are none
    private fun showVaccinations(list: LinearLayout)
    {
        val records: List<VaccinationRecord> = page.repository.vaccinations()
        if (records.isEmpty())
        {
            views.addWithTopMargin(list, views.messageCard(text(R.string.vaccinations_empty_title), text(R.string.vaccinations_empty_message)), 10)
        }
        for (record in records)
        {
            views.addWithTopMargin(list, vaccinationCard(record), 10)
        }
    }

    //"Rabies / Milo · Due 3 Nov 2026 / Upcoming · PawCare Clinic · Given 3 Nov 2025"
    private fun vaccinationCard(record: VaccinationRecord): MaterialCardView
    {
        val petName: String = page.repository.petName(record.petId)
        val dueDate: String = TextFormat.prettyDate(page.dashboard, record.dueDate)
        val subtitle: String = page.dashboard.getString(R.string.vaccination_subtitle, petName, dueDate)

        //join the details that are filled in with " · "
        val details: MutableList<String> = mutableListOf()
        details.add(record.status)
        if (record.clinic.isNotBlank())
        {
            details.add(record.clinic)
        }
        if (record.administeredDate.isNotBlank())
        {
            val givenDate: String = TextFormat.prettyDate(page.dashboard, record.administeredDate)
            details.add(page.dashboard.getString(R.string.vaccination_given, givenDate))
        }
        if (record.notes.isNotBlank())
        {
            details.add(record.notes)
        }

        return cards.recordCard(
            title = record.vaccineName,
            subtitle = subtitle,
            detail = details.joinToString(" · "),
            onEdit = { showVaccinationForm(record) },
            onDelete = {
                //ask first, then DELETE vaccinations/{id}
                page.confirmDelete(text(R.string.vaccination_delete_title), record.vaccineName) {
                    page.repository.deleteVaccination(record.id) { result ->
                        finishDelete(result.isSuccess)
                    }
                }
            }
        )
    }

    //one card per appointment, or a message when there are none
    private fun showAppointments(list: LinearLayout)
    {
        val appointments: List<Appointment> = page.repository.appointments()
        if (appointments.isEmpty())
        {
            views.addWithTopMargin(list, views.messageCard(text(R.string.appointments_empty_title), text(R.string.appointments_empty_message)), 10)
        }
        for (appointment in appointments)
        {
            views.addWithTopMargin(list, appointmentCard(appointment), 10)
        }
    }

    //"Wellness check / Milo · 3 Nov 2026 at 10:00 / Scheduled · PawCare Clinic · Tomorrow at 10:00"
    private fun appointmentCard(appointment: Appointment): MaterialCardView
    {
        val petName: String = page.repository.petName(appointment.petId)
        val date: String = TextFormat.prettyDate(page.dashboard, appointment.appointmentDate)
        val subtitle: String = page.dashboard.getString(R.string.appointment_subtitle, petName, date, appointment.appointmentTime)

        //join the details that are filled in with " · "
        val details: MutableList<String> = mutableListOf()
        details.add(appointment.status)
        if (appointment.clinic.isNotBlank())
        {
            details.add(appointment.clinic)
        }
        val reminder: String? = AppointmentReminderCalculator.labelFor(appointment)
        if (reminder != null)
        {
            details.add(reminder)
        }
        if (appointment.notes.isNotBlank())
        {
            details.add(appointment.notes)
        }

        return cards.recordCard(
            title = appointment.reason,
            subtitle = subtitle,
            detail = details.joinToString(" · "),
            onEdit = { showAppointmentForm(appointment) },
            onDelete = {
                //ask first, then DELETE appointments/{id}
                page.confirmDelete(text(R.string.appointment_delete_title), appointment.reason) {
                    page.repository.deleteAppointment(appointment.id) { result ->
                        finishDelete(result.isSuccess)
                    }
                }
            }
        )
    }

    //one card per medical record, or a message when there are none
    private fun showMedicalRecords(list: LinearLayout)
    {
        val records: List<MedicalRecord> = page.repository.medicalRecords()
        if (records.isEmpty())
        {
            views.addWithTopMargin(list, views.messageCard(text(R.string.medical_empty_title), text(R.string.medical_empty_message)), 10)
        }
        for (record in records)
        {
            views.addWithTopMargin(list, medicalCard(record), 10)
        }
    }

    //"Routine check / Milo · 3 Oct 2026 / Dr Tan · Ear drops"
    private fun medicalCard(record: MedicalRecord): MaterialCardView
    {
        val petName: String = page.repository.petName(record.petId)
        val visitDate: String = TextFormat.prettyDate(page.dashboard, record.visitDate)
        val subtitle: String = page.dashboard.getString(R.string.medical_subtitle, petName, visitDate)

        //join the details that are filled in with " · "
        val details: MutableList<String> = mutableListOf()
        if (record.veterinarian.isNotBlank())
        {
            details.add(record.veterinarian)
        }
        if (record.treatment.isNotBlank())
        {
            details.add(record.treatment)
        }

        return cards.recordCard(
            title = record.diagnosis,
            subtitle = subtitle,
            detail = details.joinToString(" · "),
            onEdit = { showMedicalRecordForm(record) },
            onDelete = {
                //ask first, then DELETE medical-records/{id}
                page.confirmDelete(text(R.string.medical_delete_title), record.diagnosis) {
                    page.repository.deleteMedicalRecord(record.id) { result ->
                        finishDelete(result.isSuccess)
                    }
                }
            }
        )
    }

    //read a label from strings.xml
    private fun text(stringResource: Int): String
    {
        return page.dashboard.getString(stringResource)
    }
}
