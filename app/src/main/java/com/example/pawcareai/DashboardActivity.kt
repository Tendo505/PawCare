package com.example.pawcareai

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Typeface
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.pawcareai.data.ApiRequestException
import com.example.pawcareai.data.AppRepository
import com.example.pawcareai.data.Appointment
import com.example.pawcareai.data.AppointmentReminderCalculator
import com.example.pawcareai.data.BreedPrediction
import com.example.pawcareai.data.MedicalRecord
import com.example.pawcareai.data.Pet
import com.example.pawcareai.data.VaccinationRecord
import com.example.pawcareai.validation.AppointmentInputValidator
import com.example.pawcareai.validation.HealthInputValidator
import com.example.pawcareai.validation.PetInputValidator
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

class DashboardActivity : AppCompatActivity()
{
    //screen components and active scan state
    private lateinit var repository: AppRepository
    private lateinit var contentContainer: FrameLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var bottomNavigation: BottomNavigationView
    private var selectedImageUri: Uri? = null
    private var selectedAiPetId: Long? = null
    private var aiResult: BreedPrediction? = null
    private var aiError: String? = null
    private var aiLoading: Boolean = false

    //select a photo and clear the previous scan result
    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null)
        {
            selectedImageUri = uri
            aiResult = null
            aiError = null
            renderAiScreen()
        }
    }

    //1.screen setup

    //create the dashboard and restore the signed-in account
    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        repository = AppRepository(this)
        if (!repository.hasSession)
        {
            returnToLogin()
            return
        }
        setContentView(R.layout.activity_dashboard)
        contentContainer = findViewById(R.id.contentContainer)
        toolbar = findViewById(R.id.topAppBar)
        setToolbarActions()

        bottomNavigation = findViewById(R.id.bottomNavigation)
        setNavigationActions()
        showLoading("Loading your PostgreSQL records…")
        restoreAccountRecords()
    }

    //2.input

    //create or update a pet profile after validating the form
    private fun showPetDialog(existing: Pet? = null)
    {
        val body: LinearLayout = dialogBody()
        val name: EditText = body.field("Pet name", existing?.name)
        val species: Spinner = body.spinner("Species", listOf("Dog", "Cat"), existing?.species)
        val breed: EditText = body.field("Breed", existing?.breed)
        val sex: Spinner = body.spinner("Sex", listOf("Unknown", "Female", "Male"), existing?.sex)
        val birthDate: EditText = body.dateField("Birth date (YYYY-MM-DD)", existing?.birthDate)
        val savedWeight: String? = existing?.weightKg?.takeIf { weightKg -> weightKg > 0 }?.clean()
        val weight: EditText = body.field("Weight in kg", savedWeight, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val microchip: EditText = body.field("Microchip number (optional)", existing?.microchipNumber)
        val notes: EditText = body.field("Care notes (optional)", existing?.notes, lines = 3)

        val petDialog = MaterialAlertDialogBuilder(this)
        petDialog.setTitle(if (existing == null) "Add pet" else "Edit ${existing.name}")
        petDialog.setView(scrollDialog(body))
        petDialog.setNegativeButton("Cancel", null)
        petDialog.setPositiveButton("Save", null)
        val dialog = petDialog.create()
        dialog.setOnShowListener {
            val saveButton = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                val validation = PetInputValidator.validate(
                    name = name.text.toString(),
                    birthDate = birthDate.text.toString(),
                    weightKg = weight.text.toString()
                )
                name.error = validation.nameError
                birthDate.error = validation.birthDateError
                weight.error = validation.weightError
                if (!validation.isValid)
                {
                    Toast.makeText(this, validation.firstError, Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                saveButton.isEnabled = false
                val petRecord = Pet(
                    id = existing?.id ?: 0,
                    name = name.text.toString().trim(),
                    species = species.selectedItem.toString(),
                    breed = breed.text.toString().trim(),
                    sex = sex.selectedItem.toString(),
                    birthDate = birthDate.text.toString().trim(),
                    weightKg = weight.text.toString().toDoubleOrNull() ?: 0.0,
                    microchipNumber = microchip.text.toString().trim(),
                    notes = notes.text.toString().trim()
                )
                //refresh the screen only after saving succeeds
                repository.savePet(petRecord) { result ->
                    saveButton.isEnabled = true
                    handleDatabaseResult(result) {
                        dialog.dismiss()
                        renderPetsScreen()
                    }
                }
            }
        }
        dialog.show()
    }

    //create or update a vaccination linked to the selected pet
    private fun showVaccinationDialog(existing: VaccinationRecord? = null)
    {
        val pets = repository.pets()
        if (pets.isEmpty())
        {
            return requirePetFirst()
        }
        val body = dialogBody()
        val pet = body.petSpinner(pets, existing?.petId)
        val vaccine = body.field("Vaccine name", existing?.vaccineName)
        val administered = body.dateField("Administered date (optional)", existing?.administeredDate)
        val due = body.dateField("Next due date (YYYY-MM-DD)", existing?.dueDate)
        val clinic = body.field("Clinic (optional)", existing?.clinic)
        val status = body.spinner("Status", listOf("Upcoming", "Completed", "Overdue"), existing?.status)
        val notes = body.field("Notes (optional)", existing?.notes, lines = 2)
        showSaveDialog(
            title = if (existing == null) "Add vaccination" else "Edit vaccination",
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
                repository.saveVaccination(vaccination) { result ->
                    complete(result.map { Unit })
                }
            },
            onSaved = { renderHealthScreen() }
        )
    }

    //create or update a clinic appointment after checking its date and time
    private fun showAppointmentDialog(existing: Appointment? = null)
    {
        val pets = repository.pets()
        if (pets.isEmpty())
        {
            return requirePetFirst()
        }
        val body = dialogBody()
        val pet = body.petSpinner(pets, existing?.petId)
        val date = body.dateField("Appointment date (YYYY-MM-DD)", existing?.appointmentDate)
        val time = body.field("Time (HH:MM)", existing?.appointmentTime ?: "09:00")
        val clinic = body.field("Clinic", existing?.clinic)
        val reason = body.field("Reason for visit", existing?.reason)
        val status = body.spinner("Status", listOf("Scheduled", "Completed", "Cancelled"), existing?.status)
        val notes = body.field("Notes (optional)", existing?.notes, lines = 3)
        showSaveDialog(
            title = if (existing == null) "Book appointment" else "Edit appointment",
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
                repository.saveAppointment(appointment) { result ->
                    complete(result.map { Unit })
                }
            },
            onSaved = { renderHealthScreen() }
        )
    }

    //create or update a medical visit record for the selected pet
    private fun showMedicalRecordDialog(existing: MedicalRecord? = null)
    {
        val pets = repository.pets()
        if (pets.isEmpty())
        {
            return requirePetFirst()
        }
        val body = dialogBody()
        val pet = body.petSpinner(pets, existing?.petId)
        val date = body.dateField("Visit date (YYYY-MM-DD)", existing?.visitDate)
        val veterinarian = body.field("Veterinarian", existing?.veterinarian)
        val diagnosis = body.field("Diagnosis / visit outcome", existing?.diagnosis)
        val treatment = body.field("Treatment", existing?.treatment, lines = 2)
        val notes = body.field("Notes (optional)", existing?.notes, lines = 3)
        showSaveDialog(
            title = if (existing == null) "Add medical record" else "Edit medical record",
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
                repository.saveMedicalRecord(medicalRecord) { result ->
                    complete(result.map { Unit })
                }
            },
            onSaved = { renderHealthScreen() }
        )
    }

    //link a new prediction to a selected pet profile
    private fun choosePredictionPet()
    {
        val pets = repository.pets()
        val petOptions: List<String> = listOf("Do not link to a pet") + pets.map { pet -> pet.name }
        val petDialog = MaterialAlertDialogBuilder(this)
        petDialog.setTitle("Link prediction to pet")
        petDialog.setItems(petOptions.toTypedArray()) { _, position ->
            selectedAiPetId = if (position == 0) null else pets[position - 1].id
            renderAiScreen()
        }
        petDialog.setNegativeButton("Cancel", null)
        petDialog.show()
    }

    //request a pet profile before adding health records
    private fun requirePetFirst()
    {
        MaterialAlertDialogBuilder(this)
            .setTitle("Add a pet first")
            .setMessage("Health records must be linked to a pet profile.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Add pet") { _, _ -> showPetDialog() }
            .show()
    }

    //create the input area inside a dialog
    private fun dialogBody(): LinearLayout
    {
        val body = vertical()
        body.setPadding(dp(2), 0, dp(2), dp(4))
        return body
    }

    //allow a long dialog to scroll
    private fun scrollDialog(body: View): ScrollView
    {
        val scrollView = ScrollView(this)
        scrollView.setPadding(dp(20), 0, dp(20), 0)
        scrollView.addView(body)
        return scrollView
    }

    //select the pet linked to a record
    private fun LinearLayout.petSpinner(pets: List<Pet>, selectedId: Long?): Spinner
    {
        val petOptions = pets.map { "${it.name} (${it.species})" }
        val petInput = spinner("Pet", petOptions)
        val selectedIndex = pets.indexOfFirst { it.id == selectedId }
        if (selectedIndex >= 0) petInput.setSelection(selectedIndex)
        return petInput
    }

    //create a labelled dropdown
    private fun LinearLayout.spinner(title: String, options: List<String>, selected: String? = null): Spinner
    {
        val titleMargin = if (childCount == 0) 0 else 10
        addView(label(title, 12, true, R.color.paw_muted).withTopMargin(titleMargin))

        val dropdown = Spinner(this@DashboardActivity)
        dropdown.adapter = ArrayAdapter(this@DashboardActivity, android.R.layout.simple_spinner_dropdown_item, options)
        val selectedIndex = options.indexOf(selected)
        if (selectedIndex >= 0) dropdown.setSelection(selectedIndex)
        dropdown.setPadding(dp(10), dp(8), dp(10), dp(8))
        addView(dropdown, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)))
        return dropdown
    }

    //create a form input
    private fun LinearLayout.field(
        hint: String,
        value: String? = null,
        inputType: Int = InputType.TYPE_CLASS_TEXT,
        lines: Int = 1
    ): EditText
    {
        val input = EditText(this@DashboardActivity)
        input.hint = hint
        input.setText(value.orEmpty())
        input.inputType = inputType
        input.maxLines = lines
        input.minLines = lines
        input.setTextColor(color(R.color.paw_text))
        input.setHintTextColor(color(R.color.paw_muted))

        val inputLayout = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        inputLayout.topMargin = dp(if (childCount == 0) 0 else 8)
        addView(input, inputLayout)
        return input
    }

    //choose a date with the calendar
    private fun LinearLayout.dateField(hint: String, value: String? = null): EditText
    {
        val dateInput = field(hint, value)
        dateInput.isFocusable = false
        dateInput.setOnClickListener {
            val initialDate = runCatching { LocalDate.parse(dateInput.text.toString()) }
                .getOrDefault(LocalDate.now())
            val datePicker = DatePickerDialog(
                this@DashboardActivity,
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

    //load records after restoring the signed-in account
    private fun restoreAccountRecords()
    {
        repository.restoreSession { sessionResult ->
            if (isFinishing || isDestroyed) return@restoreSession
            sessionResult.onSuccess {
                repository.refreshData { dataResult ->
                    if (isFinishing || isDestroyed) return@refreshData
                    dataResult.onSuccess {
                        renderSelectedScreen(bottomNavigation.selectedItemId)
                        refreshSystemHealth()
                    }
                        .onFailure { showDatabaseError() }
                }
            }.onFailure { error ->
                if (error is ApiRequestException && error.statusCode == 401)
                {
                    returnToLogin()
                }
                else
                {
                    showDatabaseError()
                }
            }
        }
    }

    //validate the photo and request breed analysis through laravel
    private fun analyzeImage()
    {
        val uri = selectedImageUri ?: return
        val imageReadResult = runCatching {
            contentResolver.openInputStream(uri)?.use { imageStream ->
                //read at most the upload limit plus one byte
                val limit = 10 * 1024 * 1024
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (output.size() <= limit)
                {
                    val count = imageStream.read(buffer, 0, minOf(buffer.size, limit + 1 - output.size()))
                    if (count == -1) break
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        }
        val bytes: ByteArray? = imageReadResult.getOrNull()
        if (bytes == null)
        {
            aiError = "The selected image could not be read. Please choose another photo."
            renderAiScreen()
            return
        }
        if (bytes.size > 10 * 1024 * 1024)
        {
            aiError = "The selected image is larger than 10 MB. Choose a smaller photo."
            renderAiScreen()
            return
        }
        val mime = contentResolver.getType(uri) ?: "image/jpeg"
        val extension = when (mime)
        {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/jpeg" -> "jpg"
            else -> {
                aiError = "Choose a JPG, PNG, or WebP image."
                renderAiScreen()
                return
            }
        }
        aiLoading = true
        aiError = null
        renderAiScreen()
        repository.analyzeBreed(bytes, mime, "pet-photo.$extension", selectedAiPetId) { result ->
            aiLoading = false
            result.onSuccess { prediction -> aiResult = prediction }
                .onFailure { error ->
                    aiError = error.localizedMessage
                        ?: "The image could not be analyzed. Check Laravel and FastAPI, then try again."
                }
            if (!isFinishing && !isDestroyed && bottomNavigation.selectedItemId == R.id.nav_ai)
            {
                renderAiScreen()
            }
        }
    }

    //update the connection status shown on the home screen
    private fun refreshSystemHealth()
    {
        repository.checkSystemHealth {
            if (!isFinishing && !isDestroyed && bottomNavigation.selectedItemId == R.id.nav_home)
            {
                renderHomeScreen()
            }
        }
    }

    //save a validated form while preventing duplicate button submissions
    private fun showSaveDialog(
        title: String,
        body: LinearLayout,
        validate: () -> String?,
        save: ((Result<Unit>) -> Unit) -> Unit,
        onSaved: () -> Unit
    )
    {
        val saveDialog = MaterialAlertDialogBuilder(this)
        saveDialog.setTitle(title)
        saveDialog.setView(scrollDialog(body))
        saveDialog.setNegativeButton("Cancel", null)
        saveDialog.setPositiveButton("Save", null)
        val dialog = saveDialog.create()
        dialog.setOnShowListener {
            val saveButton = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                val error = validate()
                if (error != null)
                {
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show()
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

    //confirm before deleting a record
    private fun confirmDelete(title: String, message: String, action: () -> Unit)
    {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("Keep", null)
            .setPositiveButton("Delete") { _, _ -> action() }
            .show()
    }

    //handle the result of a database update
    private fun <T> handleDatabaseResult(result: Result<T>, onSuccess: (T) -> Unit)
    {
        result.onSuccess(onSuccess)
            .onFailure {
                Toast.makeText(
                    this,
                    "The database could not be updated. Check the Laravel connection and try again.",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    //4.output

    //display account details, care totals, reminders and quick actions
    private fun renderHomeScreen()
    {
        toolbar.title = "PawCare"
        val user = repository.currentUser ?: return
        val root = screen(R.layout.screen_home)
        root.findViewById<TextView>(R.id.homeGreeting).text = "Hello, ${user.name.substringBefore(' ')} 👋"
        bindSystemHealth(root)

        val stats = repository.stats()
        root.findViewById<TextView>(R.id.petStatValue).text = stats.petCount.toString()
        root.findViewById<TextView>(R.id.vaccineStatValue).text = stats.upcomingVaccinations.toString()
        root.findViewById<TextView>(R.id.appointmentStatValue).text = stats.scheduledAppointments.toString()
        root.findViewById<TextView>(R.id.medicalStatValue).text = stats.medicalRecordCount.toString()
        val remindersContainer = root.findViewById<LinearLayout>(R.id.homeReminders)
        val reminders = mutableListOf<Triple<String, String, String>>()
        val upcomingAppointments = AppointmentReminderCalculator.upcoming(repository.appointments(), limit = 3)
        upcomingAppointments.forEach { reminder ->
            val appointment = reminder.appointment
            val appointmentDetails = listOf(appointment.reason, appointment.clinic)
                .filter { detail -> detail.isNotBlank() }
                .joinToString(" · ")
            reminders += Triple(
                "Appointment · ${repository.petName(appointment.petId)}",
                reminder.label,
                appointmentDetails
            )
        }
        val dueVaccinations = repository.vaccinations()
            .filter { vaccination -> vaccination.status != "Completed" }
            .take(2)
        dueVaccinations.forEach { vaccination ->
            reminders += Triple(
                "Vaccination · ${repository.petName(vaccination.petId)}",
                "Due ${prettyDate(vaccination.dueDate)}",
                vaccination.vaccineName
            )
        }
        if (reminders.isEmpty())
        {
            remindersContainer.addView(emptyCard("No reminders yet", "Add an appointment or vaccination record to see it here.").withTopMargin(10))
        }
        else
        {
            reminders.forEach { (title, date, detail) ->
                remindersContainer.addView(infoCard(title, date, detail).withTopMargin(10))
            }
        }


        root.findViewById<MaterialButton>(R.id.homeAddPet).setOnClickListener { showPetDialog() }
        root.findViewById<MaterialButton>(R.id.homeBookVisit).setOnClickListener { showAppointmentDialog() }
        root.findViewById<MaterialButton>(R.id.homeRecognize).setOnClickListener {
            bottomNavigation.selectedItemId = R.id.nav_ai
        }
        show(root)
    }

    //display all pet profiles and the add-pet action
    private fun renderPetsScreen()
    {
        toolbar.title = "My pets"
        val root = screen(R.layout.screen_pets)
        root.findViewById<MaterialButton>(R.id.petsAdd).setOnClickListener { showPetDialog() }
        val list = root.findViewById<LinearLayout>(R.id.petsList)
        val pets = repository.pets()
        if (pets.isEmpty())
        {
            list.addView(emptyCard("No pets yet", "Create your first pet profile to start tracking their health.").withTopMargin(18))
        }
        else
        {
            pets.forEach { pet -> list.addView(petCard(pet).withTopMargin(14)) }
        }
        show(root)
    }

    //display vaccination, appointment and medical record sections
    private fun renderHealthScreen()
    {
        toolbar.title = "Health records"
        val root = screen(R.layout.screen_health)
        root.findViewById<MaterialButton>(R.id.healthAddVaccine).setOnClickListener { showVaccinationDialog() }
        root.findViewById<MaterialButton>(R.id.healthBookVisit).setOnClickListener { showAppointmentDialog() }
        root.findViewById<MaterialButton>(R.id.healthAddRecord).setOnClickListener { showMedicalRecordDialog() }

        showRecords(root.findViewById(R.id.vaccinationList), repository.vaccinations(),
            "No vaccination records", "Add past doses and future due dates.", ::vaccinationCard)
        showRecords(root.findViewById(R.id.appointmentList), repository.appointments(),
            "No appointments", "Schedule a clinic visit and keep the details handy.", ::appointmentCard)
        showRecords(root.findViewById(R.id.medicalList), repository.medicalRecords(),
            "No medical history", "Record diagnoses and treatments after a clinic visit.", ::medicalCard)
        show(root)
    }

    //display records or the matching empty message with the same spacing
    private fun <T> showRecords(
        root: LinearLayout,
        records: List<T>,
        emptyTitle: String,
        emptyMessage: String,
        createCard: (T) -> View
    )
    {
        if (records.isEmpty())
        {
            root.addView(emptyCard(emptyTitle, emptyMessage).withTopMargin(10))
        }
        records.forEach { record ->
            root.addView(createCard(record).withTopMargin(10))
        }
    }

    //display photo selection, analysis feedback and saved predictions
    private fun renderAiScreen()
    {
        toolbar.title = "AI breed recognition"
        val root = screen(R.layout.screen_ai)
        val pets = repository.pets()
        val currentPetId = selectedAiPetId
        if (currentPetId != null && repository.pet(currentPetId) == null)
        {
            selectedAiPetId = null
        }
        if (selectedAiPetId == null && pets.size == 1)
        {
            selectedAiPetId = pets.first().id
        }
        val linkedPet = selectedAiPetId?.let(repository::pet)
        val linkedPetButton = root.findViewById<MaterialButton>(R.id.aiLinkedPet)
        linkedPetButton.text = linkedPet?.let { pet -> "Linked pet: ${pet.name}" } ?: "Link to a pet (optional)"
        linkedPetButton.isEnabled = !aiLoading
        linkedPetButton.setOnClickListener { choosePredictionPet() }
        bindImagePreview(root)

        val choosePhotoButton = root.findViewById<MaterialButton>(R.id.aiChoosePhoto)
        choosePhotoButton.text = if (selectedImageUri == null) "Choose photo" else "Choose another photo"
        choosePhotoButton.isEnabled = !aiLoading
        choosePhotoButton.setOnClickListener { imagePicker.launch("image/*") }
        val analyzeButton = root.findViewById<MaterialButton>(R.id.aiAnalyze)
        analyzeButton.isEnabled = selectedImageUri != null && !aiLoading
        analyzeButton.setOnClickListener { analyzeImage() }
        root.findViewById<View>(R.id.aiProgress).visibility = if (aiLoading) View.VISIBLE else View.GONE

        val feedback = root.findViewById<LinearLayout>(R.id.aiFeedback)
        aiResult?.let { result -> feedback.addView(predictionResultCard(result).withTopMargin(18)) }
        aiError?.let { message ->
            feedback.addView(emptyCard("Could not analyze the photo", message).withTopMargin(18))
        }
        val historyList = root.findViewById<LinearLayout>(R.id.aiHistory)
        val history = repository.predictions()
        if (history.isEmpty())
        {
            historyList.addView(emptyCard("No saved predictions", "A successful result will be stored in PostgreSQL and listed here.").withTopMargin(10))
        }
        else
        {
            history.take(5).forEach { prediction -> historyList.addView(predictionCard(prediction).withTopMargin(10)) }
        }
        show(root)
    }

    //fill the connection card from the latest service check
    private fun bindSystemHealth(root: View)
    {
        val health = repository.systemHealth()
        root.findViewById<TextView>(R.id.connectionTitle).text = when (health?.overall)
        {
            "ok" -> "All services connected"
            "degraded" -> "Core records connected; one service needs attention"
            "unavailable" -> "Laravel connection unavailable"
            else -> "Checking system services…"
        }
        root.findViewById<TextView>(R.id.connectionLaravel).text = "Laravel API: ${serviceLabel(health?.laravel)}"
        root.findViewById<TextView>(R.id.connectionDatabase).text = "PostgreSQL: ${serviceLabel(health?.database)}"
        root.findViewById<TextView>(R.id.connectionAi).text = "AI service: ${serviceLabel(health?.ai)}"
        val model = root.findViewById<TextView>(R.id.connectionModel)
        model.visibility = if (health?.aiModelVersion.isNullOrBlank()) View.GONE else View.VISIBLE
        model.text = "Model: ${health?.aiModelVersion}"
        root.findViewById<MaterialButton>(R.id.connectionRetry).setOnClickListener { refreshSystemHealth() }
    }

    //display a readable name for each service status
    private fun serviceLabel(status: String?): String
    {
        return when (status)
        {
            "ok", "ready" -> "Connected"
            "model_missing" -> "Model not loaded"
            "unavailable" -> "Unavailable"
            "unknown" -> "Not confirmed"
            else -> "Checking"
        }
    }

    //show the selected photo without decoding its full camera resolution
    private fun bindImagePreview(root: View)
    {
        val uri = selectedImageUri ?: return
        val photo = root.findViewById<ImageView>(R.id.aiPhoto)
        val bitmap = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            {
                //keep camera orientation while limiting preview memory
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val largestSide = maxOf(info.size.width, info.size.height)
                    decoder.setTargetSampleSize(maxOf(1, (largestSide + 2047) / 2048))
                }
            }
            else
            {
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = true
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                options.inSampleSize = 1
                while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 2048)
                {
                    options.inSampleSize *= 2
                }
                options.inJustDecodeBounds = false
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            }
        }.getOrNull()
        if (bitmap != null)
        {
            photo.setImageBitmap(bitmap)
            photo.visibility = View.VISIBLE
            root.findViewById<View>(R.id.aiPlaceholder).visibility = View.GONE
        }
    }

    //create the latest breed match and alternative predictions
    private fun predictionResultCard(result: BreedPrediction): MaterialCardView
    {
        val resultBody: LinearLayout = vertical(18)
        resultBody.addView(label("Best match", 13, true, R.color.paw_primary))
        resultBody.addView(label(result.breed.replace('_', ' '), 25, true, R.color.paw_text).withTopMargin(4))

        val speciesName: String = result.species.replaceFirstChar { character -> character.uppercase() }
        //model probability x 100 for display
        val confidencePercent: Int = (result.confidence * 100).toInt()
        resultBody.addView(label("$speciesName · $confidencePercent% confidence", 15, false, R.color.paw_muted).withTopMargin(4))
        if (result.topPredictions.size > 1)
        {
            resultBody.addView(label("Other possibilities", 14, true, R.color.paw_text).withTopMargin(14))
            result.topPredictions.drop(1).take(2).forEach { prediction ->
                val predictionText: String = "• ${prediction.breed.replace('_', ' ')} — ${(prediction.confidence * 100).toInt()}%"
                resultBody.addView(label(predictionText, 14, false, R.color.paw_muted).withTopMargin(5))
            }
        }
        if (result.petName.isNotBlank())
        {
            resultBody.addView(label("Saved for ${result.petName}", 13, true, R.color.paw_primary).withTopMargin(12))
        }

        val resultCard: MaterialCardView = card(R.color.white)
        resultCard.strokeColor = color(R.color.paw_primary)
        resultCard.strokeWidth = dp(1)
        resultCard.addView(resultBody)
        return resultCard
    }

    //create a saved prediction history card
    private fun predictionCard(prediction: BreedPrediction): MaterialCardView
    {
        val predictionBody: LinearLayout = vertical(16)
        predictionBody.addView(label(prediction.breed.replace('_', ' '), 18, true, R.color.paw_text))
        val speciesName: String = prediction.species.replaceFirstChar { character -> character.uppercase() }
        //model probability x 100 for display
        val confidencePercent: Int = (prediction.confidence * 100).toInt()
        predictionBody.addView(label("$speciesName · $confidencePercent% confidence", 14, false, R.color.paw_primary).withTopMargin(4))

        val details = mutableListOf<String>()
        if (prediction.petName.isNotBlank())
        {
            details += prediction.petName
        }
        prediction.createdAt.substringBefore('T')
            .takeIf { date -> date.isNotBlank() }
            ?.let { date -> details += prettyDate(date) }
        if (details.isNotEmpty())
        {
            predictionBody.addView(label(details.joinToString(" · "), 13, false, R.color.paw_muted).withTopMargin(6))
        }

        val historyCard: MaterialCardView = card(R.color.white)
        historyCard.addView(predictionBody)
        return historyCard
    }

    //display each saved pet inside the pet screen
    private fun petCard(pet: Pet): MaterialCardView
    {
        val body = vertical(18)
        val heading = LinearLayout(this)
        heading.gravity = Gravity.CENTER_VERTICAL
        val icon = label(if (pet.species == "Cat") "🐈" else "🐕", 34, false, R.color.paw_text)
        icon.id = R.id.petIcon
        heading.addView(icon, LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.WRAP_CONTENT))
        val identity = vertical()
        val name = label(pet.name, 21, true, R.color.paw_text)
        name.id = R.id.petName
        identity.addView(name)
        val breed = label("${pet.breed.ifBlank { "Unknown breed" }} · ${pet.species}", 14, false, R.color.paw_muted)
        breed.id = R.id.petBreed
        identity.addView(breed.withTopMargin(2))
        heading.addView(identity, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        body.addView(heading)
        val details = label("${pet.sex}  •  ${pet.weightKg.clean()} kg  •  Born ${prettyDate(pet.birthDate)}", 14, false, R.color.paw_muted)
        details.id = R.id.petDetails
        body.addView(details.withTopMargin(13))
        val microchip = label("Microchip: ${pet.microchipNumber}", 13, false, R.color.paw_muted)
        microchip.id = R.id.petMicrochip
        microchip.visibility = if (pet.microchipNumber.isBlank()) View.GONE else View.VISIBLE
        body.addView(microchip.withTopMargin(5))
        body.addView(recordActions(R.id.petEdit, R.id.petDelete,
            onEdit = { showPetDialog(pet) },
            onDelete = {
                confirmDelete("Delete ${pet.name}?", "All linked health records will also be removed.") {
                    repository.deletePet(pet.id) { result ->
                        handleDatabaseResult(result) { renderPetsScreen() }
                    }
                }
            }
        ).withTopMargin(12))
        val container = card(R.color.white)
        container.id = R.id.petCard
        container.addView(body)
        return container
    }

    //create a vaccination card with its saved care details
    private fun vaccinationCard(item: VaccinationRecord): MaterialCardView
    {
        val vaccinationDetails: String = listOfNotNull(
            item.status,
            item.clinic.takeIf { it.isNotBlank() },
            item.administeredDate.takeIf { it.isNotBlank() }?.let { "Given ${prettyDate(it)}" },
            item.notes.takeIf { it.isNotBlank() }
        ).joinToString(" · ")

        return recordCard(
            title = item.vaccineName,
            subtitle = "${repository.petName(item.petId)} · Due ${prettyDate(item.dueDate)}",
            detail = vaccinationDetails,
            onEdit = { showVaccinationDialog(item) },
            onDelete = {
                confirmDelete("Delete vaccination record?", item.vaccineName) {
                    repository.deleteVaccination(item.id) { result ->
                        handleDatabaseResult(result) { renderHealthScreen() }
                    }
                }
            }
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

        return recordCard(
            title = item.reason,
            subtitle = "${repository.petName(item.petId)} · ${prettyDate(item.appointmentDate)} at ${item.appointmentTime}",
            detail = appointmentDetails,
            onEdit = { showAppointmentDialog(item) },
            onDelete = {
                confirmDelete("Cancel and remove appointment?", item.reason) {
                    repository.deleteAppointment(item.id) { result ->
                        handleDatabaseResult(result) { renderHealthScreen() }
                    }
                }
            }
        )
    }

    //create a medical record card with the visit outcome
    private fun medicalCard(item: MedicalRecord): MaterialCardView
    {
        val medicalDetails: String = listOf(item.veterinarian, item.treatment)
            .filter { detail -> detail.isNotBlank() }
            .joinToString(" · ")

        return recordCard(
            title = item.diagnosis,
            subtitle = "${repository.petName(item.petId)} · ${prettyDate(item.visitDate)}",
            detail = medicalDetails,
            onEdit = { showMedicalRecordDialog(item) },
            onDelete = {
                confirmDelete("Delete medical record?", item.diagnosis) {
                    repository.deleteMedicalRecord(item.id) { result ->
                        handleDatabaseResult(result) { renderHealthScreen() }
                    }
                }
            }
        )
    }

    //display each saved health record inside the health screen
    private fun recordCard(
        title: String,
        subtitle: String,
        detail: String,
        onEdit: () -> Unit,
        onDelete: () -> Unit
    ): MaterialCardView
    {
        val body = vertical(16)
        val titleView = label(title, 17, true, R.color.paw_text)
        titleView.id = R.id.recordTitle
        body.addView(titleView)
        val subtitleView = label(subtitle, 14, false, R.color.paw_primary)
        subtitleView.id = R.id.recordSubtitle
        body.addView(subtitleView.withTopMargin(4))
        val detailView = label(detail, 13, false, R.color.paw_muted)
        detailView.id = R.id.recordDetail
        detailView.visibility = if (detail.isBlank()) View.GONE else View.VISIBLE
        body.addView(detailView.withTopMargin(5))
        body.addView(recordActions(R.id.recordEdit, R.id.recordDelete, onEdit, onDelete).withTopMargin(8))
        val container = card(R.color.white)
        container.id = R.id.recordCard
        container.addView(body)
        return container
    }

    //keep edit and delete actions beside their record
    private fun recordActions(editId: Int, deleteId: Int, onEdit: () -> Unit, onDelete: () -> Unit): LinearLayout
    {
        val actions = LinearLayout(this)
        actions.gravity = Gravity.END
        listOf(Triple(editId, "Edit", onEdit), Triple(deleteId, "Delete", onDelete)).forEach { (id, text, action) ->
            val button = MaterialButton(this, null, R.attr.materialButtonOutlinedStyle)
            button.id = id
            button.text = text
            button.textSize = 12f
            button.cornerRadius = dp(12)
            button.minHeight = 0
            button.insetTop = 0
            button.insetBottom = 0
            button.setOnClickListener { action() }
            val position = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42))
            position.leftMargin = dp(8)
            actions.addView(button, position)
        }
        return actions
    }

    //create the content area with the existing screen spacing
    private fun screen(layout: Int = 0): LinearLayout
    {
        if (layout != 0)
        {
            return layoutInflater.inflate(layout, contentContainer, false) as LinearLayout
        }
        val root = vertical(20)
        root.setPadding(dp(20), dp(18), dp(20), dp(32))
        return root
    }

    //display the selected module inside the content container
    private fun show(root: LinearLayout)
    {
        contentContainer.removeAllViews()
        val scrollView = ScrollView(this)
        scrollView.isFillViewport = true
        scrollView.addView(root)
        contentContainer.addView(scrollView)
    }

    //display progress while records are loading
    private fun showLoading(message: String)
    {
        toolbar.title = "PawCare"
        val root = screen()
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.addView(ProgressBar(this))
        root.addView(label(message, 15, true, R.color.paw_text).withTopMargin(14))
        show(root)
    }

    //retry loading records after a connection error
    private fun showDatabaseError()
    {
        val root = screen()
        root.addView(emptyCard("Could not load your records", "Make sure PostgreSQL and the Laravel API are running, then try again."))
        root.addView(actionButton("Try again") {
            showLoading("Loading your PostgreSQL records…")
            restoreAccountRecords()
        }.withTopMargin(12))
        show(root)
    }

    //create a vertical group of views
    private fun vertical(padding: Int = 0): LinearLayout
    {
        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        if (padding > 0) column.setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
        column.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return column
    }

    //create text with the existing font settings
    private fun label(text: String, size: Int, bold: Boolean, colorRes: Int): TextView
    {
        val textLabel = TextView(this)
        textLabel.text = text
        textLabel.textSize = size.toFloat()
        textLabel.setTextColor(color(colorRes))
        if (bold) textLabel.setTypeface(textLabel.typeface, Typeface.BOLD)
        textLabel.setLineSpacing(0f, 1.1f)
        return textLabel
    }

    //create a card with the existing colours and shape
    private fun card(background: Int): MaterialCardView
    {
        val container = MaterialCardView(this)
        container.radius = dp(20).toFloat()
        container.cardElevation = dp(1).toFloat()
        container.setCardBackgroundColor(color(background))
        container.strokeColor = color(R.color.paw_surface_variant)
        container.strokeWidth = dp(1)
        container.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return container
    }

    //display an empty or error message inside the current screen
    private fun emptyCard(title: String, subtitle: String): MaterialCardView
    {
        val body = vertical(18)
        val titleView = label(title, 16, true, R.color.paw_text)
        titleView.id = R.id.messageTitle
        body.addView(titleView)
        val subtitleView = label(subtitle, 13, false, R.color.paw_muted)
        subtitleView.id = R.id.messageSubtitle
        body.addView(subtitleView.withTopMargin(4))
        val container = card(R.color.white)
        container.id = R.id.messageCard
        container.addView(body)
        return container
    }

    //display the summary of a saved record
    private fun infoCard(title: String, date: String, detail: String): MaterialCardView
    {
        val content = vertical(15)
        content.addView(label(title, 16, true, R.color.paw_text))
        content.addView(label(date, 13, true, R.color.paw_primary).withTopMargin(4))
        if (detail.isNotBlank()) content.addView(label(detail, 13, false, R.color.paw_muted).withTopMargin(3))

        val container = card(R.color.white)
        container.addView(content)
        return container
    }

    //create a full-width action button
    private fun actionButton(text: String, click: () -> Unit): MaterialButton
    {
        val button = MaterialButton(this)
        button.text = text
        button.cornerRadius = dp(14)
        button.setOnClickListener { click() }
        button.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52))
        return button
    }

    //set the top spacing while retaining the view size
    private fun View.withTopMargin(value: Int): View
    {
        val currentLayout = layoutParams
            ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val marginLayout = when (currentLayout)
        {
            is LinearLayout.LayoutParams -> currentLayout
            else -> LinearLayout.LayoutParams(currentLayout.width, currentLayout.height)
        }
        marginLayout.topMargin = dp(value)
        layoutParams = marginLayout
        return this
    }

    //resolve a colour resource
    private fun color(resource: Int): Int = ContextCompat.getColor(this, resource)

    //convert spacing for the screen density
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    //format a weight without unnecessary decimal places
    private fun Double.clean(): String = if (this % 1.0 == 0.0) toInt().toString() else String.format(Locale.US, "%.1f", this)

    //format a saved date for display
    private fun prettyDate(value: String): String
    {
        if (value.isBlank()) return "Not set"
        return try
        {
            LocalDate.parse(value).format(DateTimeFormatter.ofPattern("d MMM yyyy"))
        }
        catch (_: DateTimeParseException)
        {
            value
        }
    }

    //5.navigation

    //confirm sign-out before returning to login
    private fun setToolbarActions()
    {
        toolbar.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.action_logout)
            {
                val signOutDialog = MaterialAlertDialogBuilder(this)
                signOutDialog.setTitle("Sign out?")
                signOutDialog.setMessage("Your PawCare records will remain safely stored in PostgreSQL.")
                signOutDialog.setNegativeButton("Cancel", null)
                signOutDialog.setPositiveButton("Sign out") { _, _ ->
                    repository.logout { returnToLogin() }
                }
                signOutDialog.show()
                true
            }
            else
            {
                false
            }
        }
    }

    //switch the content container from the selected tab
    private fun setNavigationActions()
    {
        bottomNavigation.setOnItemSelectedListener { item ->
            renderSelectedScreen(item.itemId)
            true
        }
    }

    //keep asynchronous refreshes on the tab the user selected
    private fun renderSelectedScreen(itemId: Int)
    {
        when (itemId)
        {
            R.id.nav_home -> renderHomeScreen()
            R.id.nav_pets -> renderPetsScreen()
            R.id.nav_health -> renderHealthScreen()
            R.id.nav_ai -> renderAiScreen()
        }
    }

    //return to authentication after logout
    private fun returnToLogin()
    {
        val loginIntent = Intent(this, LoginActivity::class.java)
        startActivity(loginIntent)
        finish()
    }
}
