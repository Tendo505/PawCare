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
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

class DashboardActivity : AppCompatActivity()
{
    // Screen components and active scan state
    private lateinit var repository: AppRepository
    private lateinit var contentContainer: FrameLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var bottomNavigation: BottomNavigationView
    private var selectedImageUri: Uri? = null
    private var selectedAiPetId: Long? = null
    private var aiResult: BreedPrediction? = null
    private var aiError: String? = null
    private var aiLoading: Boolean = false

    // Select a photo and clear the previous scan result
    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null)
        {
            selectedImageUri = uri
            aiResult = null
            aiError = null
            renderAiScreen()
        }
    }

    // Create the dashboard and restore the signed-in account
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

        bottomNavigation = findViewById(R.id.bottomNavigation)
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId)
            {
                R.id.nav_home -> renderHomeScreen()
                R.id.nav_pets -> renderPetsScreen()
                R.id.nav_health -> renderHealthScreen()
                R.id.nav_ai -> renderAiScreen()
            }
            true
        }
        showLoading("Loading your PostgreSQL records…")
        repository.restoreSession { sessionResult ->
            sessionResult.onSuccess {
                repository.refreshData { dataResult ->
                    dataResult.onSuccess {
                        renderHomeScreen()
                        refreshSystemHealth()
                    }
                        .onFailure { showDatabaseError() }
                }
            }.onFailure { returnToLogin() }
        }
    }

    // Display account details, care totals, reminders and quick actions
    private fun renderHomeScreen()
    {
        toolbar.title = "PawCare"
        val root: LinearLayout = screen()
        val user = repository.currentUser ?: return
        root.addView(label("Hello, ${user.name.substringBefore(' ')} 👋", 28, true, R.color.paw_text))
        root.addView(label("Here is your pet care overview.", 15, false, R.color.paw_muted).withTopMargin(4))

        val heroBody: LinearLayout = vertical(18)
        heroBody.addView(label("Healthy routines, happier paws", 21, true, R.color.white))
        heroBody.addView(label("Track visits, vaccines, and reminders in one private place.", 14, false, R.color.white).withTopMargin(8))

        val heroCard: MaterialCardView = card(R.color.paw_primary)
        heroCard.addView(heroBody)
        root.addView(heroCard.withTopMargin(22))

        root.addView(sectionTitle("System connection").withTopMargin(24))
        root.addView(systemHealthCard().withTopMargin(10))

        val stats = repository.stats()
        root.addView(sectionTitle("At a glance").withTopMargin(24))
        val petStatsRow = LinearLayout(this)
        petStatsRow.orientation = LinearLayout.HORIZONTAL
        petStatsRow.addView(statCard("🐾", stats.petCount.toString(), "Pets"), weighted())
        petStatsRow.addView(statCard("💉", stats.upcomingVaccinations.toString(), "Vaccines due"), weighted(left = 10))
        root.addView(petStatsRow.withTopMargin(10))

        val healthStatsRow = LinearLayout(this)
        healthStatsRow.orientation = LinearLayout.HORIZONTAL
        healthStatsRow.addView(statCard("📅", stats.scheduledAppointments.toString(), "Appointments"), weighted())
        healthStatsRow.addView(statCard("🩺", stats.medicalRecordCount.toString(), "Health records"), weighted(left = 10))
        root.addView(healthStatsRow.withTopMargin(10))

        root.addView(sectionTitle("Coming up").withTopMargin(24))
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
            root.addView(emptyCard("No reminders yet", "Add an appointment or vaccination record to see it here.").withTopMargin(10))
        }
        else
        {
            reminders.forEach { (title, date, detail) ->
                root.addView(infoCard(title, date, detail).withTopMargin(10))
            }
        }

        root.addView(sectionTitle("Quick actions").withTopMargin(24))
        val quickActionsRow = LinearLayout(this)
        quickActionsRow.orientation = LinearLayout.HORIZONTAL
        quickActionsRow.addView(actionButton("Add pet") { showPetDialog() }, weighted())
        quickActionsRow.addView(actionButton("Book visit") { showAppointmentDialog() }, weighted(left = 10))
        root.addView(quickActionsRow.withTopMargin(10))

        val recognizeButton: MaterialButton = actionButton("Recognize a breed") {
            findViewById<BottomNavigationView>(R.id.bottomNavigation).selectedItemId = R.id.nav_ai
        }
        root.addView(recognizeButton.withTopMargin(10))
        show(root)
    }

    // Update the connection status shown on the home screen
    private fun refreshSystemHealth()
    {
        repository.checkSystemHealth {
            if (bottomNavigation.selectedItemId == R.id.nav_home)
            {
                renderHomeScreen()
            }
        }
    }

    // Create the Laravel, PostgreSQL and AI connection card
    private fun systemHealthCard(): MaterialCardView
    {
        val health = repository.systemHealth()
        val title: String = when (health?.overall)
        {
            "ok" -> "All services connected"
            "degraded" -> "Core records connected; one service needs attention"
            "unavailable" -> "Laravel connection unavailable"
            else -> "Checking system services…"
        }

        val connectionBody: LinearLayout = vertical(18)
        connectionBody.addView(label(title, 16, true, R.color.paw_text))
        connectionBody.addView(label("Laravel API: ${serviceLabel(health?.laravel)}", 13, false, R.color.paw_muted).withTopMargin(8))
        connectionBody.addView(label("PostgreSQL: ${serviceLabel(health?.database)}", 13, false, R.color.paw_muted).withTopMargin(4))
        connectionBody.addView(label("AI service: ${serviceLabel(health?.ai)}", 13, false, R.color.paw_muted).withTopMargin(4))
        if (!health?.aiModelVersion.isNullOrBlank())
        {
            connectionBody.addView(label("Model: ${health?.aiModelVersion}", 12, false, R.color.paw_muted).withTopMargin(4))
        }
        connectionBody.addView(smallButton("Check again") { refreshSystemHealth() }.withTopMargin(12))

        val connectionCard: MaterialCardView = card(R.color.white)
        connectionCard.addView(connectionBody)
        return connectionCard
    }

    // Display a readable name for each service status
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

    // Display all pet profiles and the add-pet action
    private fun renderPetsScreen()
    {
        toolbar.title = "My pets"
        val root = screen()
        root.addView(header("Pet profiles", "Keep identity and care details together.", "Add pet") { showPetDialog() })
        val pets = repository.pets()
        if (pets.isEmpty())
        {
            root.addView(emptyCard("No pets yet", "Create your first pet profile to start tracking their health.").withTopMargin(18))
        }
        else
        {
            pets.forEach { pet ->
                root.addView(petCard(pet).withTopMargin(14))
            }
        }
        show(root)
    }

    // Display vaccination, appointment and medical record sections
    private fun renderHealthScreen()
    {
        toolbar.title = "Health records"
        val root = screen()
        root.addView(label("Health timeline", 28, true, R.color.paw_text))
        root.addView(label("Vaccinations, visits, and appointments for every pet.", 15, false, R.color.paw_muted).withTopMargin(4))

        root.addView(moduleHeader("💉 Vaccinations", "Add vaccine") { showVaccinationDialog() }.withTopMargin(24))
        val vaccinations = repository.vaccinations()
        if (vaccinations.isEmpty())
        {
            root.addView(emptyCard("No vaccination records", "Add past doses and future due dates.").withTopMargin(10))
        }
        vaccinations.forEach { vaccination ->
            root.addView(vaccinationCard(vaccination).withTopMargin(10))
        }

        root.addView(moduleHeader("📅 Appointments", "Book visit") { showAppointmentDialog() }.withTopMargin(28))
        val appointments = repository.appointments()
        if (appointments.isEmpty())
        {
            root.addView(emptyCard("No appointments", "Schedule a clinic visit and keep the details handy.").withTopMargin(10))
        }
        appointments.forEach { appointment ->
            root.addView(appointmentCard(appointment).withTopMargin(10))
        }

        root.addView(moduleHeader("🩺 Medical records", "Add record") { showMedicalRecordDialog() }.withTopMargin(28))
        val records = repository.medicalRecords()
        if (records.isEmpty())
        {
            root.addView(emptyCard("No medical history", "Record diagnoses and treatments after a clinic visit.").withTopMargin(10))
        }
        records.forEach { medicalRecord ->
            root.addView(medicalCard(medicalRecord).withTopMargin(10))
        }
        show(root)
    }

    // Display photo selection, analysis feedback and saved predictions
    private fun renderAiScreen()
    {
        toolbar.title = "AI breed recognition"
        val root = screen()
        root.addView(label("Recognize a breed", 28, true, R.color.paw_text))
        root.addView(label("Choose a clear, well-lit photo with one cat or dog.", 15, false, R.color.paw_muted).withTopMargin(4))

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
        val linkedPetTitle: String = linkedPet?.let { pet -> "Linked pet: ${pet.name}" }
            ?: "Link to a pet (optional)"
        val linkedPetButton: MaterialButton = actionButton(linkedPetTitle) { choosePredictionPet() }
        root.addView(linkedPetButton.withTopMargin(18))
        root.addView(imagePreviewCard().withTopMargin(22))

        val choosePhotoTitle: String = if (selectedImageUri == null) "Choose photo" else "Choose another photo"
        val choosePhotoButton: MaterialButton = actionButton(choosePhotoTitle) { imagePicker.launch("image/*") }
        root.addView(choosePhotoButton.withTopMargin(14))

        val analyzeButton: MaterialButton = actionButton("Analyze breed") { analyzeImage() }
        analyzeButton.isEnabled = selectedImageUri != null && !aiLoading
        root.addView(analyzeButton.withTopMargin(10))

        if (aiLoading)
        {
            val progressRow = LinearLayout(this)
            progressRow.gravity = Gravity.CENTER_VERTICAL
            progressRow.addView(ProgressBar(this), LinearLayout.LayoutParams(dp(28), dp(28)))
            progressRow.addView(label("Analyzing image…", 15, true, R.color.paw_text).withLeftMargin(12))
            root.addView(progressRow.withTopMargin(20))
        }
        aiResult?.let { result ->
            root.addView(predictionResultCard(result).withTopMargin(18))
        }
        aiError?.let { message ->
            root.addView(emptyCard("Could not analyze the photo", message).withTopMargin(18))
        }

        root.addView(label("AI results are guidance only. Breed predictions can be wrong, especially for mixed breeds. They do not provide a medical diagnosis.", 12, false, R.color.paw_muted).withTopMargin(18))

        root.addView(sectionTitle("Saved prediction history").withTopMargin(26))
        val history = repository.predictions()
        if (history.isEmpty())
        {
            root.addView(emptyCard("No saved predictions", "A successful result will be stored in PostgreSQL and listed here.").withTopMargin(10))
        }
        else
        {
            history.take(5).forEach { prediction ->
                root.addView(predictionCard(prediction).withTopMargin(10))
            }
        }
        show(root)
    }

    // Create the selected photo preview or the empty photo placeholder
    private fun imagePreviewCard(): MaterialCardView
    {
        val previewCard = MaterialCardView(this)
        previewCard.radius = dp(22).toFloat()
        previewCard.setCardBackgroundColor(color(R.color.paw_surface_variant))
        previewCard.strokeColor = color(R.color.paw_primary)
        previewCard.strokeWidth = dp(1)

        val imageUri: Uri? = selectedImageUri
        if (imageUri == null)
        {
            val placeholder: LinearLayout = vertical(26)
            placeholder.gravity = Gravity.CENTER
            placeholder.addView(label("📷", 46, false, R.color.paw_text).centered())
            placeholder.addView(label("No photo selected", 18, true, R.color.paw_text).centered().withTopMargin(8))
            placeholder.addView(label("JPG, PNG, or WebP · maximum 10 MB", 13, false, R.color.paw_muted).centered().withTopMargin(4))
            previewCard.addView(placeholder)
        }
        else
        {
            val photoView = ImageView(this)
            photoView.setImageURI(imageUri)
            photoView.scaleType = ImageView.ScaleType.CENTER_CROP
            photoView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260))
            previewCard.addView(photoView)
        }
        return previewCard
    }

    // Create the latest breed match and alternative predictions
    private fun predictionResultCard(result: BreedPrediction): MaterialCardView
    {
        val resultBody: LinearLayout = vertical(18)
        resultBody.addView(label("Best match", 13, true, R.color.paw_primary))
        resultBody.addView(label(result.breed.replace('_', ' '), 25, true, R.color.paw_text).withTopMargin(4))

        val speciesName: String = result.species.replaceFirstChar { character -> character.uppercase() }
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

    // Validate the photo and request breed analysis through Laravel
    private fun analyzeImage()
    {
        val uri = selectedImageUri ?: return
        val imageReadResult = runCatching {
            contentResolver.openInputStream(uri)?.use { imageStream -> imageStream.readBytes() }
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
            renderAiScreen()
        }
    }

    // Link a new prediction to a selected pet profile
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

    // Create a saved prediction history card
    private fun predictionCard(prediction: BreedPrediction): MaterialCardView
    {
        val predictionBody: LinearLayout = vertical(16)
        predictionBody.addView(label(prediction.breed.replace('_', ' '), 18, true, R.color.paw_text))
        val speciesName: String = prediction.species.replaceFirstChar { character -> character.uppercase() }
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

    // Create a pet profile card with edit and delete actions
    private fun petCard(pet: Pet): MaterialCardView
    {
        val petIdentity: LinearLayout = vertical()
        petIdentity.addView(label(pet.name, 21, true, R.color.paw_text))
        petIdentity.addView(label("${pet.breed.ifBlank { "Unknown breed" }} · ${pet.species}", 14, false, R.color.paw_muted).withTopMargin(2))

        val identityRow = LinearLayout(this)
        identityRow.gravity = Gravity.CENTER_VERTICAL
        val petIcon: String = if (pet.species == "Cat") "🐈" else "🐕"
        val iconSize = LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.WRAP_CONTENT)
        identityRow.addView(label(petIcon, 34, false, R.color.paw_text), iconSize)
        identityRow.addView(petIdentity, weighted())

        val profileBody: LinearLayout = vertical(18)
        profileBody.addView(identityRow)
        profileBody.addView(label("${pet.sex}  •  ${pet.weightKg.clean()} kg  •  Born ${prettyDate(pet.birthDate)}", 14, false, R.color.paw_muted).withTopMargin(13))
        if (pet.microchipNumber.isNotBlank())
        {
            profileBody.addView(label("Microchip: ${pet.microchipNumber}", 13, false, R.color.paw_muted).withTopMargin(5))
        }

        val profileActions: LinearLayout = actionRow(
            "Edit" to { showPetDialog(pet) },
            "Delete" to {
                confirmDelete("Delete ${pet.name}?", "All linked health records will also be removed.") {
                    repository.deletePet(pet.id) { result ->
                        handleDatabaseResult(result) { renderPetsScreen() }
                    }
                }
            }
        )
        profileBody.addView(profileActions.withTopMargin(12))

        val profileCard: MaterialCardView = card(R.color.white)
        profileCard.addView(profileBody)
        return profileCard
    }

    // Create a vaccination card with its saved care details
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

    // Create an appointment card with its reminder and status
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

    // Create a medical record card with the visit outcome
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

    // Create the common health card layout and record actions
    private fun recordCard(title: String, subtitle: String, detail: String, onEdit: () -> Unit, onDelete: () -> Unit): MaterialCardView
    {
        val recordBody: LinearLayout = vertical(16)
        recordBody.addView(label(title, 17, true, R.color.paw_text))
        recordBody.addView(label(subtitle, 14, false, R.color.paw_primary).withTopMargin(4))
        if (detail.isNotBlank())
        {
            recordBody.addView(label(detail, 13, false, R.color.paw_muted).withTopMargin(5))
        }
        recordBody.addView(actionRow("Edit" to onEdit, "Delete" to onDelete).withTopMargin(8))

        val healthCard: MaterialCardView = card(R.color.white)
        healthCard.addView(recordBody)
        return healthCard
    }

    // Create or update a pet profile after validating the form
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

    // Create or update a vaccination linked to the selected pet
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

    // Create or update a clinic appointment after checking its date and time
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

    // Create or update a medical visit record for the selected pet
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

    // Save a validated form while preventing duplicate button submissions
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

    //create the content area with the existing screen spacing
    private fun screen(): LinearLayout
    {
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
            repository.refreshData { result ->
                result.onSuccess { renderHomeScreen() }
                    .onFailure { showDatabaseError() }
            }
        }.withTopMargin(12))
        show(root)
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

    //create a vertical group of views
    private fun vertical(padding: Int = 0): LinearLayout
    {
        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        if (padding > 0) column.setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
        column.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return column
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

    //create a section heading
    private fun sectionTitle(text: String): TextView = label(text, 19, true, R.color.paw_text)

    //create the page heading and its action button
    private fun header(title: String, subtitle: String, action: String, onAction: () -> Unit): LinearLayout
    {
        val heading = vertical()
        heading.addView(label(title, 28, true, R.color.paw_text))
        heading.addView(label(subtitle, 14, false, R.color.paw_muted).withTopMargin(3))

        val headingRow = LinearLayout(this)
        headingRow.gravity = Gravity.CENTER_VERTICAL
        headingRow.addView(heading, weighted())
        headingRow.addView(smallButton(action, onAction))

        val headerContainer = vertical()
        headerContainer.addView(headingRow)
        return headerContainer
    }

    //create a module heading and its action button
    private fun moduleHeader(title: String, action: String, onAction: () -> Unit): LinearLayout
    {
        val headingRow = LinearLayout(this)
        headingRow.gravity = Gravity.CENTER_VERTICAL
        headingRow.addView(sectionTitle(title), weighted())
        headingRow.addView(smallButton(action, onAction))
        return headingRow
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

    //display one dashboard total
    private fun statCard(icon: String, value: String, caption: String): MaterialCardView
    {
        val content = vertical(14)
        content.addView(label(icon, 22, false, R.color.paw_text))
        content.addView(label(value, 25, true, R.color.paw_text).withTopMargin(6))
        content.addView(label(caption, 12, false, R.color.paw_muted).withTopMargin(2))

        val container = card(R.color.white)
        container.addView(content)
        return container
    }

    //display a message when a section has no records
    private fun emptyCard(title: String, subtitle: String): MaterialCardView
    {
        val content = vertical(18)
        content.addView(label(title, 16, true, R.color.paw_text))
        content.addView(label(subtitle, 13, false, R.color.paw_muted).withTopMargin(4))

        val container = card(R.color.white)
        container.addView(content)
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

    //create a compact outlined action button
    private fun smallButton(text: String, click: () -> Unit): MaterialButton
    {
        val button = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        button.text = text
        button.textSize = 12f
        button.cornerRadius = dp(12)
        button.minHeight = 0
        button.insetTop = 0
        button.insetBottom = 0
        button.setOnClickListener { click() }
        button.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42))
        return button
    }

    //group the actions for a record
    private fun actionRow(vararg actions: Pair<String, () -> Unit>): LinearLayout
    {
        val buttonRow = LinearLayout(this)
        buttonRow.gravity = Gravity.END
        actions.forEach { (title, action) -> buttonRow.addView(smallButton(title, action).withLeftMargin(8)) }
        return buttonRow
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

    //set the left spacing for an action
    private fun <T : View> T.withLeftMargin(value: Int): T
    {
        val marginLayout = layoutParams as? LinearLayout.LayoutParams
            ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        marginLayout.leftMargin = dp(value)
        layoutParams = marginLayout
        return this
    }

    //centre text inside its view
    private fun TextView.centered(): TextView = apply { gravity = Gravity.CENTER }

    //share the row width evenly
    private fun weighted(left: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(left) }

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

    //check the stored date format
    private fun isIsoDate(value: String): Boolean = runCatching { LocalDate.parse(value); true }.getOrDefault(false)

    //check the stored time format
    private fun isTime(value: String): Boolean = runCatching { LocalTime.parse(value); true }.getOrDefault(false)

    //return to authentication after logout
    private fun returnToLogin()
    {
        val loginIntent = Intent(this, MainActivity::class.java)
        startActivity(loginIntent)
        finish()
    }
}
