package com.example.pawcareai

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
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
import com.example.pawcareai.data.AppRepository
import com.example.pawcareai.data.Appointment
import com.example.pawcareai.data.BreedPrediction
import com.example.pawcareai.data.MedicalRecord
import com.example.pawcareai.data.Pet
import com.example.pawcareai.data.VaccinationRecord
import com.example.pawcareai.network.BreedPredictionResponse
import com.example.pawcareai.network.NetworkModule
import com.example.pawcareai.validation.PetInputValidator
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

class DashboardActivity : AppCompatActivity() {
    private lateinit var repository: AppRepository
    private lateinit var contentContainer: FrameLayout
    private lateinit var toolbar: MaterialToolbar
    private var selectedImageUri: Uri? = null
    private var aiResult: BreedPredictionResponse? = null
    private var aiError: String? = null
    private var aiLoading = false

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            aiResult = null
            aiError = null
            renderAiScreen()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = AppRepository(this)
        if (repository.currentUser == null) {
            returnToLogin()
            return
        }
        setContentView(R.layout.activity_dashboard)
        contentContainer = findViewById(R.id.contentContainer)
        toolbar = findViewById(R.id.topAppBar)
        toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.action_logout) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Sign out?")
                    .setMessage("Your local pet records will remain on this device.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Sign out") { _, _ ->
                        repository.logout()
                        returnToLogin()
                    }.show()
                true
            } else false
        }

        findViewById<BottomNavigationView>(R.id.bottomNavigation).setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> renderHomeScreen()
                R.id.nav_pets -> renderPetsScreen()
                R.id.nav_health -> renderHealthScreen()
                R.id.nav_ai -> renderAiScreen()
            }
            true
        }
        renderHomeScreen()
    }
    //bot home
    private fun renderHomeScreen() {
        toolbar.title = "PawCare"
        val root = screen()
        val user = repository.currentUser ?: return
        root.addView(label("Hello, ${user.name.substringBefore(' ')} 👋", 28, true, R.color.paw_text))
        root.addView(label("Here is your pet care overview.", 15, false, R.color.paw_muted).withTopMargin(4))

        val hero = card(R.color.paw_primary).apply {
            addView(vertical(18).apply {
                addView(label("Healthy routines, happier paws", 21, true, R.color.white))
                addView(label("Track visits, vaccines, and reminders in one private place.", 14, false, R.color.white).withTopMargin(8))
            })
        }.withTopMargin(22)
        root.addView(hero)

        val stats = repository.stats()
        root.addView(sectionTitle("At a glance").withTopMargin(24))
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(statCard("🐾", stats.petCount.toString(), "Pets"), weighted())
            addView(statCard("💉", stats.upcomingVaccinations.toString(), "Vaccines due"), weighted(left = 10))
        }.withTopMargin(10))
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(statCard("📅", stats.scheduledAppointments.toString(), "Appointments"), weighted())
            addView(statCard("🩺", stats.medicalRecordCount.toString(), "Health records"), weighted(left = 10))
        }.withTopMargin(10))

        root.addView(sectionTitle("Coming up").withTopMargin(24))
        val reminders = mutableListOf<Triple<String, String, String>>()
        repository.appointments().filter { it.status == "Scheduled" }.take(2).forEach {
            reminders += Triple("Appointment · ${repository.petName(it.petId)}", "${prettyDate(it.appointmentDate)} at ${it.appointmentTime}", it.reason)
        }
        repository.vaccinations().filter { it.status != "Completed" }.take(2).forEach {
            reminders += Triple("Vaccination · ${repository.petName(it.petId)}", "Due ${prettyDate(it.dueDate)}", it.vaccineName)
        }
        if (reminders.isEmpty()) {
            root.addView(emptyCard("No reminders yet", "Add an appointment or vaccination record to see it here.").withTopMargin(10))
        } else {
            reminders.forEach { (title, date, detail) -> root.addView(infoCard(title, date, detail).withTopMargin(10)) }
        }

        root.addView(sectionTitle("Quick actions").withTopMargin(24))
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(actionButton("Add pet") { showPetDialog() }, weighted())
            addView(actionButton("Book visit") { showAppointmentDialog() }, weighted(left = 10))
        }.withTopMargin(10))
        root.addView(actionButton("Recognize a breed") {
            findViewById<BottomNavigationView>(R.id.bottomNavigation).selectedItemId = R.id.nav_ai
        }.withTopMargin(10))
        show(root)
    }
    //bot nav pet
    private fun renderPetsScreen() {
        toolbar.title = "My pets"
        val root = screen()
        root.addView(header("Pet profiles", "Keep identity and care details together.", "Add pet") { showPetDialog() })
        val pets = repository.pets()
        if (pets.isEmpty()) {
            root.addView(emptyCard("No pets yet", "Create your first pet profile to start tracking their health.").withTopMargin(18))
        } else {
            pets.forEach { pet ->
                root.addView(petCard(pet).withTopMargin(14))
            }
        }
        show(root)
    }
    //health bot
    private fun renderHealthScreen() {
        toolbar.title = "Health records"
        val root = screen()
        root.addView(label("Health timeline", 28, true, R.color.paw_text))
        root.addView(label("Vaccinations, visits, and appointments for every pet.", 15, false, R.color.paw_muted).withTopMargin(4))

        root.addView(moduleHeader("💉 Vaccinations", "Add vaccine") { showVaccinationDialog() }.withTopMargin(24))
        val vaccinations = repository.vaccinations()
        if (vaccinations.isEmpty()) root.addView(emptyCard("No vaccination records", "Add past doses and future due dates.").withTopMargin(10))
        vaccinations.forEach { item -> root.addView(vaccinationCard(item).withTopMargin(10)) }

        root.addView(moduleHeader("📅 Appointments", "Book visit") { showAppointmentDialog() }.withTopMargin(28))
        val appointments = repository.appointments()
        if (appointments.isEmpty()) root.addView(emptyCard("No appointments", "Schedule a clinic visit and keep the details handy.").withTopMargin(10))
        appointments.forEach { item -> root.addView(appointmentCard(item).withTopMargin(10)) }

        root.addView(moduleHeader("🩺 Medical records", "Add record") { showMedicalRecordDialog() }.withTopMargin(28))
        val records = repository.medicalRecords()
        if (records.isEmpty()) root.addView(emptyCard("No medical history", "Record diagnoses and treatments after a clinic visit.").withTopMargin(10))
        records.forEach { item -> root.addView(medicalCard(item).withTopMargin(10)) }
        show(root)
    }

    private fun renderAiScreen() {
        toolbar.title = "AI breed recognition"
        val root = screen()
        root.addView(label("Recognize a breed", 28, true, R.color.paw_text))
        root.addView(label("Choose a clear, well-lit photo with one cat or dog.", 15, false, R.color.paw_muted).withTopMargin(4))

        val preview = MaterialCardView(this).apply {
            radius = dp(22).toFloat()
            setCardBackgroundColor(color(R.color.paw_surface_variant))
            strokeColor = color(R.color.paw_primary)
            strokeWidth = dp(1)
            val uri = selectedImageUri
            if (uri == null) {
                addView(vertical(26).apply {
                    gravity = Gravity.CENTER
                    addView(label("📷", 46, false, R.color.paw_text).centered())
                    addView(label("No photo selected", 18, true, R.color.paw_text).centered().withTopMargin(8))
                    addView(label("JPG and PNG images are supported", 13, false, R.color.paw_muted).centered().withTopMargin(4))
                })
            } else {
                addView(ImageView(this@DashboardActivity).apply {
                    setImageURI(uri)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260))
                })
            }
        }.withTopMargin(22)
        root.addView(preview)
        root.addView(actionButton(if (selectedImageUri == null) "Choose photo" else "Choose another photo") { imagePicker.launch("image/*") }.withTopMargin(14))
        root.addView(actionButton("Analyze breed") { analyzeImage() }.apply { isEnabled = selectedImageUri != null && !aiLoading }.withTopMargin(10))

        if (aiLoading) {
            root.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(ProgressBar(this@DashboardActivity), LinearLayout.LayoutParams(dp(28), dp(28)))
                addView(label("Analyzing image…", 15, true, R.color.paw_text).withLeftMargin(12))
            }.withTopMargin(20))
        }
        aiResult?.let { result ->
            root.addView(card(R.color.white).apply {
                strokeColor = color(R.color.paw_primary)
                strokeWidth = dp(1)
                addView(vertical(18).apply {
                    addView(label("Best match", 13, true, R.color.paw_primary))
                    addView(label(result.breed.replace('_', ' '), 25, true, R.color.paw_text).withTopMargin(4))
                    addView(label("${result.species.replaceFirstChar { it.uppercase() }} · ${(result.confidence * 100).toInt()}% confidence", 15, false, R.color.paw_muted).withTopMargin(4))
                    if (result.top_predictions.size > 1) {
                        addView(label("Other possibilities", 14, true, R.color.paw_text).withTopMargin(14))
                        result.top_predictions.drop(1).take(2).forEach {
                            addView(label("• ${it.breed.replace('_', ' ')} — ${(it.confidence * 100).toInt()}%", 14, false, R.color.paw_muted).withTopMargin(5))
                        }
                    }
                })
            }.withTopMargin(18))
        }
        aiError?.let { message -> root.addView(emptyCard("Could not analyze the photo", message).withTopMargin(18)) }

        root.addView(label("AI results are guidance only. Breed predictions can be wrong, especially for mixed breeds. They do not provide a medical diagnosis.", 12, false, R.color.paw_muted).withTopMargin(18))
        val history = repository.predictions().take(3)
        if (history.isNotEmpty()) {
            root.addView(sectionTitle("Recent scans").withTopMargin(24))
            history.forEach {
                root.addView(infoCard(it.breed.replace('_', ' '), prettyDate(it.createdAt.take(10)), "${it.species} · ${(it.confidence * 100).toInt()}% confidence").withTopMargin(10))
            }
        }
        show(root)
    }

    private fun analyzeImage() {
        val uri = selectedImageUri ?: return
        val bytes = runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        if (bytes == null) {
            aiError = "The selected image could not be read. Please choose another photo."
            renderAiScreen()
            return
        }
        aiLoading = true
        aiError = null
        renderAiScreen()
        val mime = contentResolver.getType(uri) ?: "image/jpeg"
        val body = bytes.toRequestBody(mime.toMediaType())
        val part = MultipartBody.Part.createFormData("image", "pet-photo.${if (mime.contains("png")) "png" else "jpg"}", body)
        NetworkModule.breedRecognition.predict(part).enqueue(object : Callback<BreedPredictionResponse> {
            override fun onResponse(call: Call<BreedPredictionResponse>, response: Response<BreedPredictionResponse>) {
                aiLoading = false
                val result = response.body()
                if (response.isSuccessful && result != null) {
                    aiResult = result
                    repository.savePrediction(BreedPrediction(
                        species = result.species,
                        breed = result.breed,
                        confidence = result.confidence,
                        createdAt = java.time.LocalDateTime.now().toString(),
                        imageUri = uri.toString()
                    ))
                } else {
                    aiError = "The AI service returned ${response.code()}. Confirm that the FastAPI service is running on port 8001."
                }
                renderAiScreen()
            }

            override fun onFailure(call: Call<BreedPredictionResponse>, throwable: Throwable) {
                aiLoading = false
                aiError = "Start the included AI service, then try again. (${throwable.localizedMessage ?: "service unavailable"})"
                renderAiScreen()
            }
        })
    }

    private fun petCard(pet: Pet): MaterialCardView = card(R.color.white).apply {
        addView(vertical(18).apply {
            addView(LinearLayout(this@DashboardActivity).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(label(if (pet.species == "Cat") "🐈" else "🐕", 34, false, R.color.paw_text), LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(vertical().apply {
                    addView(label(pet.name, 21, true, R.color.paw_text))
                    addView(label("${pet.breed.ifBlank { "Unknown breed" }} · ${pet.species}", 14, false, R.color.paw_muted).withTopMargin(2))
                }, weighted())
            })
            addView(label("${pet.sex}  •  ${pet.weightKg.clean()} kg  •  Born ${prettyDate(pet.birthDate)}", 14, false, R.color.paw_muted).withTopMargin(13))
            if (pet.microchipNumber.isNotBlank()) addView(label("Microchip: ${pet.microchipNumber}", 13, false, R.color.paw_muted).withTopMargin(5))
            addView(actionRow(
                "Edit" to { showPetDialog(pet) },
                "Delete" to { confirmDelete("Delete ${pet.name}?", "All linked health records will also be removed.") { repository.deletePet(pet.id); renderPetsScreen() } }
            ).withTopMargin(12))
        })
    }

    private fun vaccinationCard(item: VaccinationRecord): MaterialCardView = recordCard(
        title = item.vaccineName,
        subtitle = "${repository.petName(item.petId)} · Due ${prettyDate(item.dueDate)}",
        detail = "${item.status}${if (item.clinic.isBlank()) "" else " · ${item.clinic}"}",
        onEdit = { showVaccinationDialog(item) },
        onDelete = { confirmDelete("Delete vaccination record?", item.vaccineName) { repository.deleteVaccination(item.id); renderHealthScreen() } }
    )

    private fun appointmentCard(item: Appointment): MaterialCardView = recordCard(
        title = item.reason,
        subtitle = "${repository.petName(item.petId)} · ${prettyDate(item.appointmentDate)} at ${item.appointmentTime}",
        detail = "${item.status}${if (item.clinic.isBlank()) "" else " · ${item.clinic}"}",
        onEdit = { showAppointmentDialog(item) },
        onDelete = { confirmDelete("Cancel and remove appointment?", item.reason) { repository.deleteAppointment(item.id); renderHealthScreen() } }
    )

    private fun medicalCard(item: MedicalRecord): MaterialCardView = recordCard(
        title = item.diagnosis,
        subtitle = "${repository.petName(item.petId)} · ${prettyDate(item.visitDate)}",
        detail = listOf(item.veterinarian, item.treatment).filter { it.isNotBlank() }.joinToString(" · "),
        onEdit = { showMedicalRecordDialog(item) },
        onDelete = { confirmDelete("Delete medical record?", item.diagnosis) { repository.deleteMedicalRecord(item.id); renderHealthScreen() } }
    )

    private fun recordCard(title: String, subtitle: String, detail: String, onEdit: () -> Unit, onDelete: () -> Unit): MaterialCardView = card(R.color.white).apply {
        addView(vertical(16).apply {
            addView(label(title, 17, true, R.color.paw_text))
            addView(label(subtitle, 14, false, R.color.paw_primary).withTopMargin(4))
            if (detail.isNotBlank()) addView(label(detail, 13, false, R.color.paw_muted).withTopMargin(5))
            addView(actionRow("Edit" to onEdit, "Delete" to onDelete).withTopMargin(8))
        })
    }

    private fun showPetDialog(existing: Pet? = null) {
        val body = dialogBody()
        val name = body.field("Pet name", existing?.name)
        val species = body.spinner("Species", listOf("Dog", "Cat"), existing?.species)
        val breed = body.field("Breed", existing?.breed)
        val sex = body.spinner("Sex", listOf("Unknown", "Female", "Male"), existing?.sex)
        val birthDate = body.dateField("Birth date (YYYY-MM-DD)", existing?.birthDate)
        val weight = body.field("Weight in kg", existing?.weightKg?.takeIf { it > 0 }?.clean(), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val microchip = body.field("Microchip number (optional)", existing?.microchipNumber)
        val notes = body.field("Care notes (optional)", existing?.notes, lines = 3)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) "Add pet" else "Edit ${existing.name}")
            .setView(scrollDialog(body))
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val validation = PetInputValidator.validate(
                    name = name.text.toString(),
                    birthDate = birthDate.text.toString(),
                    weightKg = weight.text.toString()
                )
                name.error = validation.nameError
                birthDate.error = validation.birthDateError
                weight.error = validation.weightError
                if (!validation.isValid) {
                    Toast.makeText(this, validation.firstError, Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                repository.savePet(Pet(
                    id = existing?.id ?: 0,
                    name = name.text.toString().trim(),
                    species = species.selectedItem.toString(),
                    breed = breed.text.toString().trim(),
                    sex = sex.selectedItem.toString(),
                    birthDate = birthDate.text.toString().trim(),
                    weightKg = weight.text.toString().toDoubleOrNull() ?: 0.0,
                    microchipNumber = microchip.text.toString().trim(),
                    notes = notes.text.toString().trim()
                ))
                dialog.dismiss()
                renderPetsScreen()
            }
        }
        dialog.show()
    }

    private fun showVaccinationDialog(existing: VaccinationRecord? = null) {
        val pets = repository.pets()
        if (pets.isEmpty()) return requirePetFirst()
        val body = dialogBody()
        val pet = body.petSpinner(pets, existing?.petId)
        val vaccine = body.field("Vaccine name", existing?.vaccineName)
        val administered = body.dateField("Administered date (optional)", existing?.administeredDate)
        val due = body.dateField("Next due date (YYYY-MM-DD)", existing?.dueDate)
        val clinic = body.field("Clinic (optional)", existing?.clinic)
        val status = body.spinner("Status", listOf("Upcoming", "Completed", "Overdue"), existing?.status)
        showSaveDialog(if (existing == null) "Add vaccination" else "Edit vaccination", body) {
            if (vaccine.text.toString().isBlank() || !isIsoDate(due.text.toString())) return@showSaveDialog "Vaccine name and a valid due date are required."
            repository.saveVaccination(VaccinationRecord(existing?.id ?: 0, petId = pets[pet.selectedItemPosition].id, vaccineName = vaccine.text.toString().trim(), administeredDate = administered.text.toString(), dueDate = due.text.toString(), clinic = clinic.text.toString().trim(), status = status.selectedItem.toString()))
            renderHealthScreen()
            null
        }
    }

    private fun showAppointmentDialog(existing: Appointment? = null) {
        val pets = repository.pets()
        if (pets.isEmpty()) return requirePetFirst()
        val body = dialogBody()
        val pet = body.petSpinner(pets, existing?.petId)
        val date = body.dateField("Appointment date (YYYY-MM-DD)", existing?.appointmentDate)
        val time = body.field("Time (HH:MM)", existing?.appointmentTime ?: "09:00")
        val clinic = body.field("Clinic", existing?.clinic)
        val reason = body.field("Reason for visit", existing?.reason)
        val status = body.spinner("Status", listOf("Scheduled", "Completed", "Cancelled"), existing?.status)
        showSaveDialog(if (existing == null) "Book appointment" else "Edit appointment", body) {
            if (!isIsoDate(date.text.toString()) || !isTime(time.text.toString()) || reason.text.toString().isBlank()) return@showSaveDialog "A valid date, 24-hour time, and reason are required."
            repository.saveAppointment(Appointment(existing?.id ?: 0, petId = pets[pet.selectedItemPosition].id, appointmentDate = date.text.toString(), appointmentTime = time.text.toString(), clinic = clinic.text.toString().trim(), reason = reason.text.toString().trim(), status = status.selectedItem.toString()))
            renderHealthScreen()
            null
        }
    }

    private fun showMedicalRecordDialog(existing: MedicalRecord? = null) {
        val pets = repository.pets()
        if (pets.isEmpty()) return requirePetFirst()
        val body = dialogBody()
        val pet = body.petSpinner(pets, existing?.petId)
        val date = body.dateField("Visit date (YYYY-MM-DD)", existing?.visitDate)
        val veterinarian = body.field("Veterinarian", existing?.veterinarian)
        val diagnosis = body.field("Diagnosis / visit outcome", existing?.diagnosis)
        val treatment = body.field("Treatment", existing?.treatment, lines = 2)
        val notes = body.field("Notes (optional)", existing?.notes, lines = 3)
        showSaveDialog(if (existing == null) "Add medical record" else "Edit medical record", body) {
            if (!isIsoDate(date.text.toString()) || diagnosis.text.toString().isBlank()) return@showSaveDialog "A valid visit date and outcome are required."
            repository.saveMedicalRecord(MedicalRecord(existing?.id ?: 0, petId = pets[pet.selectedItemPosition].id, visitDate = date.text.toString(), veterinarian = veterinarian.text.toString().trim(), diagnosis = diagnosis.text.toString().trim(), treatment = treatment.text.toString().trim(), notes = notes.text.toString().trim()))
            renderHealthScreen()
            null
        }
    }

    private fun showSaveDialog(title: String, body: LinearLayout, save: () -> String?) {
        val dialog = MaterialAlertDialogBuilder(this).setTitle(title).setView(scrollDialog(body)).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val error = save()
                if (error == null) dialog.dismiss() else Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            }
        }
        dialog.show()
    }

    private fun LinearLayout.petSpinner(pets: List<Pet>, selectedId: Long?): Spinner {
        val spinner = spinner("Pet", pets.map { "${it.name} (${it.species})" })
        val selected = pets.indexOfFirst { it.id == selectedId }
        if (selected >= 0) spinner.setSelection(selected)
        return spinner
    }

    private fun LinearLayout.spinner(title: String, options: List<String>, selected: String? = null): Spinner {
        addView(label(title, 12, true, R.color.paw_muted).withTopMargin(if (childCount == 0) 0 else 10))
        return Spinner(this@DashboardActivity).also { spinner ->
            spinner.adapter = ArrayAdapter(this@DashboardActivity, android.R.layout.simple_spinner_dropdown_item, options)
            val index = options.indexOf(selected)
            if (index >= 0) spinner.setSelection(index)
            spinner.setPadding(dp(10), dp(8), dp(10), dp(8))
            addView(spinner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)))
        }
    }

    private fun LinearLayout.field(hint: String, value: String? = null, inputType: Int = InputType.TYPE_CLASS_TEXT, lines: Int = 1): EditText = EditText(this@DashboardActivity).also {
        it.hint = hint
        it.setText(value.orEmpty())
        it.inputType = inputType
        it.maxLines = lines
        it.minLines = lines
        it.setTextColor(color(R.color.paw_text))
        it.setHintTextColor(color(R.color.paw_muted))
        addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(if (childCount == 0) 0 else 8) })
    }

    private fun LinearLayout.dateField(hint: String, value: String? = null): EditText = field(hint, value).apply {
        isFocusable = false
        setOnClickListener {
            val initial = runCatching { LocalDate.parse(text.toString()) }.getOrDefault(LocalDate.now())
            DatePickerDialog(this@DashboardActivity, { _, year, month, day -> setText(LocalDate.of(year, month + 1, day).toString()) }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
        }
    }

    private fun confirmDelete(title: String, message: String, action: () -> Unit) {
        MaterialAlertDialogBuilder(this).setTitle(title).setMessage(message).setNegativeButton("Keep", null).setPositiveButton("Delete") { _, _ -> action() }.show()
    }

    private fun requirePetFirst() {
        MaterialAlertDialogBuilder(this).setTitle("Add a pet first").setMessage("Health records must be linked to a pet profile.").setNegativeButton("Cancel", null).setPositiveButton("Add pet") { _, _ -> showPetDialog() }.show()
    }

    private fun screen(): LinearLayout = vertical(20).apply {
        setPadding(dp(20), dp(18), dp(20), dp(32))
    }

    private fun show(root: LinearLayout) {
        contentContainer.removeAllViews()
        contentContainer.addView(ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        })
    }

    private fun vertical(padding: Int = 0): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        if (padding > 0) setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun dialogBody(): LinearLayout = vertical().apply { setPadding(dp(2), 0, dp(2), dp(4)) }

    private fun scrollDialog(body: View): ScrollView = ScrollView(this).apply {
        setPadding(dp(20), 0, dp(20), 0)
        addView(body)
    }

    private fun label(text: String, size: Int, bold: Boolean, colorRes: Int): TextView = TextView(this).apply {
        this.text = text
        textSize = size.toFloat()
        setTextColor(color(colorRes))
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setLineSpacing(0f, 1.1f)
    }

    private fun sectionTitle(text: String): TextView = label(text, 19, true, R.color.paw_text)

    private fun header(title: String, subtitle: String, action: String, onAction: () -> Unit): LinearLayout = vertical().apply {
        addView(LinearLayout(this@DashboardActivity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(vertical().apply {
                addView(label(title, 28, true, R.color.paw_text))
                addView(label(subtitle, 14, false, R.color.paw_muted).withTopMargin(3))
            }, weighted())
            addView(smallButton(action, onAction))
        })
    }

    private fun moduleHeader(title: String, action: String, onAction: () -> Unit): LinearLayout = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(sectionTitle(title), weighted())
        addView(smallButton(action, onAction))
    }

    private fun card(background: Int): MaterialCardView = MaterialCardView(this).apply {
        radius = dp(20).toFloat()
        cardElevation = dp(1).toFloat()
        setCardBackgroundColor(color(background))
        strokeColor = color(R.color.paw_surface_variant)
        strokeWidth = dp(1)
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun statCard(icon: String, value: String, caption: String): MaterialCardView = card(R.color.white).apply {
        addView(vertical(14).apply {
            addView(label(icon, 22, false, R.color.paw_text))
            addView(label(value, 25, true, R.color.paw_text).withTopMargin(6))
            addView(label(caption, 12, false, R.color.paw_muted).withTopMargin(2))
        })
    }

    private fun emptyCard(title: String, subtitle: String): MaterialCardView = card(R.color.white).apply {
        addView(vertical(18).apply {
            addView(label(title, 16, true, R.color.paw_text))
            addView(label(subtitle, 13, false, R.color.paw_muted).withTopMargin(4))
        })
    }

    private fun infoCard(title: String, date: String, detail: String): MaterialCardView = card(R.color.white).apply {
        addView(vertical(15).apply {
            addView(label(title, 16, true, R.color.paw_text))
            addView(label(date, 13, true, R.color.paw_primary).withTopMargin(4))
            if (detail.isNotBlank()) addView(label(detail, 13, false, R.color.paw_muted).withTopMargin(3))
        })
    }

    private fun actionButton(text: String, click: () -> Unit): MaterialButton = MaterialButton(this).apply {
        this.text = text
        cornerRadius = dp(14)
        setOnClickListener { click() }
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52))
    }

    private fun smallButton(text: String, click: () -> Unit): MaterialButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
        this.text = text
        textSize = 12f
        cornerRadius = dp(12)
        minHeight = 0
        insetTop = 0
        insetBottom = 0
        setOnClickListener { click() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42))
    }

    private fun actionRow(vararg actions: Pair<String, () -> Unit>): LinearLayout = LinearLayout(this).apply {
        gravity = Gravity.END
        actions.forEach { (title, action) -> addView(smallButton(title, action).withLeftMargin(8)) }
    }

    private fun View.withTopMargin(value: Int): View = apply {
        val params = (layoutParams ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        layoutParams = when (params) {
            is LinearLayout.LayoutParams -> params.apply { topMargin = dp(value) }
            else -> LinearLayout.LayoutParams(params.width, params.height).apply { topMargin = dp(value) }
        }
    }

    private fun <T : View> T.withLeftMargin(value: Int): T = apply {
        val params = (layoutParams as? LinearLayout.LayoutParams) ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.leftMargin = dp(value)
        layoutParams = params
    }

    private fun TextView.centered(): TextView = apply { gravity = Gravity.CENTER }

    private fun weighted(left: Int = 0): LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(left) }

    private fun color(resource: Int): Int = ContextCompat.getColor(this, resource)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun Double.clean(): String = if (this % 1.0 == 0.0) toInt().toString() else String.format(Locale.US, "%.1f", this)

    private fun prettyDate(value: String): String {
        if (value.isBlank()) return "Not set"
        return try {
            LocalDate.parse(value).format(DateTimeFormatter.ofPattern("d MMM yyyy"))
        } catch (_: DateTimeParseException) { value }
    }

    private fun isIsoDate(value: String): Boolean = runCatching { LocalDate.parse(value); true }.getOrDefault(false)
    private fun isTime(value: String): Boolean = runCatching { LocalTime.parse(value); true }.getOrDefault(false)

    private fun returnToLogin() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
