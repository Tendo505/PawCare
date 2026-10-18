package com.example.pawcareai.data

import com.example.pawcareai.network.ApiAiPrediction
import com.example.pawcareai.network.PawCareApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

//breed scans uploaded to laravel and their saved history
class AiScanRepository(
    private val api: PawCareApi,
    private val petName: (Long) -> String
)
{
    //temporary screen data loaded through laravel
    private var predictionCache: List<BreedPrediction> = emptyList()

    //2.input

    //prepare the photo and optional pet as multipart form parts
    private fun imagePart(imageBytes: ByteArray, mimeType: String, fileName: String): MultipartBody.Part
    {
        val imageBody: RequestBody = imageBytes.toRequestBody(mimeType.toMediaType())
        return MultipartBody.Part.createFormData("image", fileName, imageBody)
    }

    //3.process

    //upload a photo and cache its saved prediction
    fun analyzeBreed(
        imageBytes: ByteArray,
        mimeType: String,
        fileName: String,
        petId: Long?,
        onResult: (Result<BreedPrediction>) -> Unit
    )
    {
        val petPart: RequestBody? = petId?.toString()?.toRequestBody("text/plain".toMediaType())

        api.createAiPrediction(imagePart(imageBytes, mimeType, fileName), petPart).enqueue(object : Callback<ApiAiPrediction>
        {
            override fun onResponse(call: Call<ApiAiPrediction>, response: Response<ApiAiPrediction>)
            {
                val prediction: ApiAiPrediction? = response.body()
                if (response.isSuccessful && prediction != null)
                {
                    onResult(Result.success(cachePrediction(prediction)))
                }
                else
                {
                    onResult(Result.failure(response.aiFailure()))
                }
            }

            override fun onFailure(call: Call<ApiAiPrediction>, throwable: Throwable)
            {
                onResult(Result.failure(throwable))
            }
        })
    }

    //convert a new prediction and keep it in the history cache
    private fun cachePrediction(prediction: ApiAiPrediction): BreedPrediction
    {
        val savedPrediction: BreedPrediction = prediction.toBreedPrediction()
        predictionCache = predictionCache.replaceOrAdd(savedPrediction) { cachedPrediction ->
            cachedPrediction.id
        }
        return savedPrediction
    }

    //replace cached predictions after a full refresh
    fun replaceCache(predictions: List<ApiAiPrediction>)
    {
        predictionCache = predictions.map { prediction -> prediction.toBreedPrediction() }
    }

    //remove predictions belonging to a cleared session
    fun clearCache()
    {
        predictionCache = emptyList()
    }

    //4.output

    //read the latest predictions first
    fun predictions(): List<BreedPrediction>
    {
        return predictionCache.sortedByDescending { prediction -> prediction.createdAt }
    }

    //convert a saved api prediction for the app
    private fun ApiAiPrediction.toBreedPrediction(): BreedPrediction
    {
        return BreedPrediction(
            id = id,
            petId = petId,
            petName = pet?.name ?: petId?.let(petName).orEmpty(),
            species = species,
            breed = breed,
            confidence = confidence,
            createdAt = createdAt,
            imageUrl = imageUrl.orEmpty(),
            modelVersion = modelVersion,
            topPredictions = topPredictions.map { prediction ->
                BreedPredictionOption(prediction.breed, prediction.confidence)
            },
            disclaimer = disclaimer
        )
    }
}
