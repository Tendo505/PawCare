package com.example.pawcareai.dashboard

import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.example.pawcareai.R
import com.example.pawcareai.data.BreedPrediction
import com.example.pawcareai.data.Pet
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

//the AI Scan tab (layout: screen_ai.xml): pick a photo, optionally link a pet with the pop-up list,
//analyze, then see the result and the history
class AiScanScreen(private val page: DashboardPage)
{
    private val photoReader: PhotoReader = PhotoReader(page.dashboard.contentResolver)
    private val predictionCards: PredictionCards = PredictionCards(page)

    //what the screen remembers between redraws
    private var selectedPhoto: Uri? = null
    private var linkedPetId: Long? = null
    private var latestResult: BreedPrediction? = null
    private var errorMessage: String? = null
    private var isAnalyzing: Boolean = false

    //1.screen setup

    //draw the ai scan screen from the values above
    fun render()
    {
        page.setTitle(R.string.title_ai)
        val root: LinearLayout = page.inflate(R.layout.screen_ai)
        checkLinkedPet()
        setButtons(root)
        showPhotoPreview(root)

        val progress: View = root.findViewById(R.id.aiProgress)
        if (isAnalyzing)
        {
            progress.visibility = View.VISIBLE
        }
        else
        {
            progress.visibility = View.GONE
        }

        showResultOrError(root.findViewById(R.id.aiFeedback))
        showHistory(root.findViewById(R.id.aiHistory))
        page.show(root)
    }

    //2.input

    //called by the dashboard after the user picks a photo; the old result is cleared
    fun onPhotoSelected(photo: Uri)
    {
        selectedPhoto = photo
        latestResult = null
        errorMessage = null
        render()
    }

    //the "Link to a pet" pop-up list; the next scan is saved for the chosen pet
    private fun choosePet()
    {
        val pets: List<Pet> = page.repository.pets()
        val choices: MutableList<String> = mutableListOf()
        choices.add(page.dashboard.getString(R.string.ai_do_not_link))
        for (pet in pets)
        {
            choices.add(pet.name)
        }

        val builder: MaterialAlertDialogBuilder = MaterialAlertDialogBuilder(page.dashboard)
        builder.setTitle(R.string.ai_link_pet_title)
        builder.setItems(choices.toTypedArray()) { dialog, position ->
            //position 0 is "Do not link to a pet"; position 1 is the first pet
            if (position == 0)
            {
                linkedPetId = null
            }
            else
            {
                linkedPetId = pets[position - 1].id
            }
            render()
        }
        builder.setNegativeButton(R.string.action_cancel, null)
        builder.show()
    }

    //3.process

    //forget a linked pet that was deleted; with exactly one pet, link it automatically
    private fun checkLinkedPet()
    {
        val currentPetId: Long? = linkedPetId
        if (currentPetId != null && page.repository.pet(currentPetId) == null)
        {
            linkedPetId = null
        }
        val pets: List<Pet> = page.repository.pets()
        if (linkedPetId == null && pets.size == 1)
        {
            linkedPetId = pets[0].id
        }
    }

    //check the photo, then upload it to laravel (POST ai-predictions)
    private fun analyzePhoto()
    {
        val photo: Uri? = selectedPhoto
        if (photo == null)
        {
            return
        }

        val bytes: ByteArray? = photoReader.readBytes(photo)
        if (bytes == null)
        {
            showError(page.dashboard.getString(R.string.ai_error_unreadable))
            return
        }
        if (bytes.size > photoReader.uploadLimit)
        {
            showError(page.dashboard.getString(R.string.ai_error_too_large))
            return
        }
        val mimeType: String = photoReader.mimeType(photo)
        val extension: String? = photoReader.extensionFor(mimeType)
        if (extension == null)
        {
            showError(page.dashboard.getString(R.string.ai_error_type))
            return
        }

        //show the spinner and disable the buttons while laravel and the ai service work
        isAnalyzing = true
        errorMessage = null
        render()
        page.repository.analyzeBreed(bytes, mimeType, "pet-photo.$extension", linkedPetId) { result ->
            isAnalyzing = false
            if (result.isSuccess)
            {
                latestResult = result.getOrNull()
            }
            else
            {
                errorMessage = readErrorMessage(result.exceptionOrNull())
            }
            //the user may have moved to another tab while waiting
            if (page.isTabSelected(R.id.nav_ai))
            {
                render()
            }
        }
    }

    //the message from laravel, or a general message when there is none
    private fun readErrorMessage(error: Throwable?): String
    {
        if (error != null)
        {
            val message: String? = error.localizedMessage
            if (message != null)
            {
                return message
            }
        }
        return page.dashboard.getString(R.string.ai_error_generic)
    }

    //4.output

    //show a problem with the photo below the buttons
    private fun showError(message: String)
    {
        errorMessage = message
        render()
    }

    //set the text of each button, and disable them while a scan is running
    private fun setButtons(root: LinearLayout)
    {
        val linkPetButton: MaterialButton = root.findViewById(R.id.aiLinkedPet)
        linkPetButton.text = linkButtonText()
        linkPetButton.isEnabled = !isAnalyzing
        linkPetButton.setOnClickListener {
            choosePet()
        }

        val choosePhotoButton: MaterialButton = root.findViewById(R.id.aiChoosePhoto)
        if (selectedPhoto == null)
        {
            choosePhotoButton.text = page.dashboard.getString(R.string.ai_choose_photo)
        }
        else
        {
            choosePhotoButton.text = page.dashboard.getString(R.string.ai_choose_another_photo)
        }
        choosePhotoButton.isEnabled = !isAnalyzing
        choosePhotoButton.setOnClickListener {
            page.dashboard.choosePhoto()
        }

        val analyzeButton: MaterialButton = root.findViewById(R.id.aiAnalyze)
        analyzeButton.isEnabled = selectedPhoto != null && !isAnalyzing
        analyzeButton.setOnClickListener {
            analyzePhoto()
        }
    }

    //"Linked pet: Milo", or "Link to a pet (optional)"
    private fun linkButtonText(): String
    {
        val currentPetId: Long? = linkedPetId
        if (currentPetId != null)
        {
            val pet: Pet? = page.repository.pet(currentPetId)
            if (pet != null)
            {
                return page.dashboard.getString(R.string.ai_linked_pet, pet.name)
            }
        }
        return page.dashboard.getString(R.string.ai_link_pet_optional)
    }

    //replace the placeholder with the chosen photo
    private fun showPhotoPreview(root: LinearLayout)
    {
        val photo: Uri? = selectedPhoto
        if (photo == null)
        {
            return
        }
        val bitmap: Bitmap? = photoReader.previewBitmap(photo)
        if (bitmap == null)
        {
            return
        }
        val photoView: ImageView = root.findViewById(R.id.aiPhoto)
        photoView.setImageBitmap(bitmap)
        photoView.visibility = View.VISIBLE
        root.findViewById<View>(R.id.aiPlaceholder).visibility = View.GONE
    }

    //the result card after a successful scan, or the error card after a failed one
    private fun showResultOrError(container: LinearLayout)
    {
        val result: BreedPrediction? = latestResult
        if (result != null)
        {
            page.views.addWithTopMargin(container, predictionCards.resultCard(result), 18)
        }
        val message: String? = errorMessage
        if (message != null)
        {
            val title: String = page.dashboard.getString(R.string.ai_error_title)
            page.views.addWithTopMargin(container, page.views.messageCard(title, message), 18)
        }
    }

    //the five newest saved scans
    private fun showHistory(container: LinearLayout)
    {
        val history: List<BreedPrediction> = page.repository.predictions()
        if (history.isEmpty())
        {
            val title: String = page.dashboard.getString(R.string.ai_history_empty_title)
            val message: String = page.dashboard.getString(R.string.ai_history_empty_message)
            page.views.addWithTopMargin(container, page.views.messageCard(title, message), 10)
            return
        }
        var shown: Int = 0
        for (prediction in history)
        {
            if (shown < 5)
            {
                page.views.addWithTopMargin(container, predictionCards.historyCard(prediction), 10)
                shown = shown + 1
            }
        }
    }
}
