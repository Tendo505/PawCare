package com.example.pawcareai.data

import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

//an error returned by laravel, with its http status code and the message for each form field
class ApiRequestException(
    message: String,
    val statusCode: Int,
    val fieldErrors: Map<String, String> = emptyMap()
) : IllegalStateException(message)

//receives a laravel response that has a body (for example a pet or a list of pets)
//T is the type of that body, so the same class works for every request
class ApiCallback<T>(
    private val onSuccess: (T) -> Unit,
    private val onFailure: (Throwable) -> Unit,
    private val breedRecognition: Boolean = false
) : Callback<T>
{
    //3.process

    //laravel answered: pass on the body, or turn the error response into an exception
    override fun onResponse(call: Call<T>, response: Response<T>)
    {
        val body: T? = response.body()
        if (response.isSuccessful && body != null)
        {
            onSuccess(body)
        }
        else
        {
            var fallback: String = "The server could not complete this request (HTTP ${response.code()})."
            if (breedRecognition)
            {
                fallback = "Breed recognition failed with HTTP ${response.code()}."
            }
            val error: ApiRequestException = ApiErrorReader.readError(response.code(), response.errorBody()?.string(), fallback)
            onFailure(error)
        }
    }

    //laravel could not be reached at all, for example when herd is not running
    override fun onFailure(call: Call<T>, throwable: Throwable)
    {
        onFailure(throwable)
    }
}

//receives a laravel response that has no body, used by the delete requests
class EmptyApiCallback(
    private val onSuccess: () -> Unit,
    private val onFailure: (Throwable) -> Unit
) : Callback<Unit>
{
    //3.process

    //laravel answered: report success, or turn the error response into an exception
    override fun onResponse(call: Call<Unit>, response: Response<Unit>)
    {
        if (response.isSuccessful)
        {
            onSuccess()
        }
        else
        {
            val fallback: String = "The server could not complete this request (HTTP ${response.code()})."
            val error: ApiRequestException = ApiErrorReader.readError(response.code(), response.errorBody()?.string(), fallback)
            onFailure(error)
        }
    }

    //laravel could not be reached at all
    override fun onFailure(call: Call<Unit>, throwable: Throwable)
    {
        onFailure(throwable)
    }
}

//reads laravel's json error body: {"message": "...", "errors": {"email": ["..."]}}
object ApiErrorReader
{
    //4.output

    //build an exception with the server message and the first message of each field
    //the fallback is shown when laravel did not send a message
    fun readError(statusCode: Int, responseText: String?, fallback: String): ApiRequestException
    {
        try
        {
            val payload: JSONObject = JSONObject(responseText ?: "")
            val fieldErrors: Map<String, String> = readFieldErrors(payload)

            var message: String = payload.optString("message")
            if (message.isBlank() && fieldErrors.isNotEmpty())
            {
                message = fieldErrors.values.first()
            }
            if (message.isBlank())
            {
                message = fallback
            }
            return ApiRequestException(message, statusCode, fieldErrors)
        }
        catch (error: Exception)
        {
            //the body was empty or not json, so only the general message can be shown
            return ApiRequestException(fallback, statusCode)
        }
    }

    //collect the first validation message for every field, for example "email"
    private fun readFieldErrors(payload: JSONObject): Map<String, String>
    {
        val fieldErrors: MutableMap<String, String> = mutableMapOf()
        val validationErrors: JSONObject? = payload.optJSONObject("errors")
        if (validationErrors == null)
        {
            return fieldErrors
        }

        for (field in validationErrors.keys())
        {
            val messages: JSONArray? = validationErrors.optJSONArray(field)
            if (messages != null)
            {
                val firstMessage: String = messages.optString(0)
                if (firstMessage.isNotBlank())
                {
                    fieldErrors[field] = firstMessage
                }
            }
        }
        return fieldErrors
    }
}

//turn an empty optional form field into null, so laravel clears the saved value
fun blankToNull(value: String): String?
{
    val trimmedValue: String = value.trim()
    if (trimmedValue.isEmpty())
    {
        return null
    }
    return trimmedValue
}
