package com.example.pawcareai.data

import com.example.pawcareai.network.ApiAiPrediction
import com.example.pawcareai.network.PawCareApi
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

//uploads a pet photo to laravel, which asks the ai service for the breed and saves the result
class AiScanRepository(
    private val api: PawCareApi,
    private val cache: RecordCache
)
{
    //2.input

    //put the photo into the "image" field of a multipart form, like a file upload on a website
    private fun createImagePart(imageBytes: ByteArray, mimeType: String, fileName: String): MultipartBody.Part
    {
        val imageType: MediaType = mimeType.toMediaType()
        val imageBody: RequestBody = imageBytes.toRequestBody(imageType)
        return MultipartBody.Part.createFormData("image", fileName, imageBody)
    }

    //put the linked pet id into the "pet_id" field, or send nothing when no pet is linked
    private fun createPetPart(petId: Long?): RequestBody?
    {
        if (petId == null)
        {
            return null
        }
        val textType: MediaType = "text/plain".toMediaType()
        return petId.toString().toRequestBody(textType)
    }

    //3.process

    //upload the photo (POST ai-predictions) and add the saved result to the history
    fun analyzeBreed(
        imageBytes: ByteArray,
        mimeType: String,
        fileName: String,
        petId: Long?,
        onResult: (Result<BreedPrediction>) -> Unit
    )
    {
        val imagePart: MultipartBody.Part = createImagePart(imageBytes, mimeType, fileName)
        val petPart: RequestBody? = createPetPart(petId)

        api.createAiPrediction(imagePart, petPart).enqueue(ApiCallback<ApiAiPrediction>(
            onSuccess = { apiPrediction ->
                val prediction: BreedPrediction = RecordConverter.toBreedPrediction(apiPrediction, cache)
                putInCache(prediction)
                onResult(Result.success(prediction))
            },
            onFailure = { error ->
                onResult(Result.failure(error))
            },
            breedRecognition = true
        ))
    }

    //replace the scan with the same id, or add it at the end when it is new
    private fun putInCache(prediction: BreedPrediction)
    {
        for (index in cache.predictions.indices)
        {
            if (cache.predictions[index].id == prediction.id)
            {
                cache.predictions[index] = prediction
                return
            }
        }
        cache.predictions.add(prediction)
    }

    //4.output

    //read the newest scans first
    fun predictions(): List<BreedPrediction>
    {
        return cache.predictions.sortedByDescending { prediction -> prediction.createdAt }
    }
}
