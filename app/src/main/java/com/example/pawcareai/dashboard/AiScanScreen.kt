package com.example.pawcareai.dashboard

import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.example.pawcareai.R
import com.example.pawcareai.data.BreedPrediction
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

//the ai scan tab: photo selection, analysis feedback and saved predictions
class AiScanScreen(
    private val page: DashboardPage,
    private val choosePhoto: () -> Unit
)
{
    private val context = page.activity
    private val photoReader = PhotoReader(context.contentResolver)

    //active scan state
    private var selectedImageUri: Uri? = null
    private var selectedAiPetId: Long? = null
    private var aiResult: BreedPrediction? = null
    private var aiError: String? = null
    private var aiLoading: Boolean = false

    //1.screen setup

    //display photo selection, analysis feedback and saved predictions
    fun render()
    {
        page.setTitle(context.getString(R.string.title_ai))
        val root = page.inflate(R.layout.screen_ai)
        keepLinkedPetValid()
        bindButtons(root)
        bindImagePreview(root)
        root.findViewById<View>(R.id.aiProgress).visibility = if (aiLoading) View.VISIBLE else View.GONE
        showFeedback(root.findViewById(R.id.aiFeedback))
        showHistory(root.findViewById(R.id.aiHistory))
        page.show(root)
    }

    //2.input

    //select a photo and clear the previous scan result
    fun onPhotoSelected(uri: Uri)
    {
        selectedImageUri = uri
        aiResult = null
        aiError = null
        render()
    }

    //link a new prediction to a selected pet profile
    private fun choosePredictionPet()
    {
        val pets = page.repository.pets()
        val petOptions: List<String> = listOf(context.getString(R.string.ai_do_not_link)) + pets.map { pet -> pet.name }
        val petDialog = MaterialAlertDialogBuilder(context)
        petDialog.setTitle(R.string.ai_link_pet_title)
        petDialog.setItems(petOptions.toTypedArray()) { _, position ->
            selectedAiPetId = if (position == 0) null else pets[position - 1].id
            render()
        }
        petDialog.setNegativeButton(R.string.action_cancel, null)
        petDialog.show()
    }

    //3.process

    //forget a deleted pet, and pick the only pet automatically
    private fun keepLinkedPetValid()
    {
        val pets = page.repository.pets()
        val currentPetId = selectedAiPetId
        if (currentPetId != null && page.repository.pet(currentPetId) == null)
        {
            selectedAiPetId = null
        }
        if (selectedAiPetId == null && pets.size == 1)
        {
            selectedAiPetId = pets.first().id
        }
    }

    //validate the photo and request breed analysis through laravel
    private fun analyzeImage()
    {
        val uri = selectedImageUri ?: return
        val bytes: ByteArray? = photoReader.readBytes(uri)
        if (bytes == null)
        {
            return showError(context.getString(R.string.ai_error_unreadable))
        }
        if (bytes.size > photoReader.uploadLimit)
        {
            return showError(context.getString(R.string.ai_error_too_large))
        }
        val mime = photoReader.mimeType(uri)
        val extension = photoReader.extensionFor(mime) ?: return showError(context.getString(R.string.ai_error_type))
        aiLoading = true
        aiError = null
        render()
        page.repository.analyzeBreed(bytes, mime, "pet-photo.$extension", selectedAiPetId, ::showAnalysisResult)
    }

    //4.output

    //keep the result and redraw only if the ai tab is still on screen
    private fun showAnalysisResult(result: Result<BreedPrediction>)
    {
        aiLoading = false
        result.onSuccess { prediction -> aiResult = prediction }
            .onFailure { error ->
                aiError = error.localizedMessage ?: context.getString(R.string.ai_error_generic)
            }
        if (page.isShowing(R.id.nav_ai))
        {
            render()
        }
    }

    //show a photo problem on the screen
    private fun showError(message: String)
    {
        aiError = message
        render()
    }

    //set the text and state of the pet, photo and analyze buttons
    private fun bindButtons(root: LinearLayout)
    {
        val linkedPet = selectedAiPetId?.let(page.repository::pet)
        val linkedPetButton = root.findViewById<MaterialButton>(R.id.aiLinkedPet)
        linkedPetButton.text = linkedPet?.let { pet -> context.getString(R.string.ai_linked_pet, pet.name) }
            ?: context.getString(R.string.ai_link_pet_optional)
        linkedPetButton.isEnabled = !aiLoading
        linkedPetButton.setOnClickListener { choosePredictionPet() }

        val choosePhotoButton = root.findViewById<MaterialButton>(R.id.aiChoosePhoto)
        choosePhotoButton.text = context.getString(if (selectedImageUri == null) R.string.ai_choose_photo else R.string.ai_choose_another_photo)
        choosePhotoButton.isEnabled = !aiLoading
        choosePhotoButton.setOnClickListener { choosePhoto() }
        val analyzeButton = root.findViewById<MaterialButton>(R.id.aiAnalyze)
        analyzeButton.isEnabled = selectedImageUri != null && !aiLoading
        analyzeButton.setOnClickListener { analyzeImage() }
    }

    //show the selected photo in place of the placeholder
    private fun bindImagePreview(root: View)
    {
        val uri = selectedImageUri ?: return
        val bitmap = photoReader.previewBitmap(uri) ?: return
        val photo = root.findViewById<ImageView>(R.id.aiPhoto)
        photo.setImageBitmap(bitmap)
        photo.visibility = View.VISIBLE
        root.findViewById<View>(R.id.aiPlaceholder).visibility = View.GONE
    }

    //show the latest result or error
    private fun showFeedback(feedback: LinearLayout)
    {
        aiResult?.let { result -> feedback.addView(context.predictionResultCard(result).withTopMargin(18)) }
        aiError?.let { message ->
            feedback.addView(context.emptyCard(context.getString(R.string.ai_error_title), message).withTopMargin(18))
        }
    }

    //show the five latest saved predictions
    private fun showHistory(historyList: LinearLayout)
    {
        val history = page.repository.predictions()
        if (history.isEmpty())
        {
            val emptyTitle = context.getString(R.string.ai_history_empty_title)
            val emptyMessage = context.getString(R.string.ai_history_empty_message)
            historyList.addView(context.emptyCard(emptyTitle, emptyMessage).withTopMargin(10))
        }
        else
        {
            history.take(5).forEach { prediction -> historyList.addView(context.predictionCard(prediction).withTopMargin(10)) }
        }
    }
}
