package com.example.pawcareai.data

import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

//error returned by laravel with its status code and field messages
class ApiRequestException(
    message: String,
    val statusCode: Int,
    val fieldErrors: Map<String, String> = emptyMap()
) : IllegalStateException(message)

//3.process

//send a request and return its response body asynchronously
internal fun <T> Call<T>.enqueueResult(onResult: (Result<T>) -> Unit)
{
    enqueue(object : Callback<T>
    {
        override fun onResponse(call: Call<T>, response: Response<T>)
        {
            val body: T? = response.body()
            if (response.isSuccessful && body != null)
            {
                onResult(Result.success(body))
            }
            else
            {
                onResult(Result.failure(response.apiFailure()))
            }
        }

        override fun onFailure(call: Call<T>, throwable: Throwable)
        {
            onResult(Result.failure(throwable))
        }
    })
}

//send a request that does not return a response body
internal fun Call<Unit>.enqueueEmpty(onResult: (Result<Unit>) -> Unit)
{
    enqueue(object : Callback<Unit>
    {
        override fun onResponse(call: Call<Unit>, response: Response<Unit>)
        {
            if (response.isSuccessful)
            {
                onResult(Result.success(Unit))
            }
            else
            {
                onResult(Result.failure(response.apiFailure()))
            }
        }

        override fun onFailure(call: Call<Unit>, throwable: Throwable)
        {
            onResult(Result.failure(throwable))
        }
    })
}

//convert and cache a saved record only after a successful response
internal fun <ApiValue, Record> Call<ApiValue>.saveResult(
    convert: (ApiValue) -> Record,
    updateCache: (Record) -> Unit,
    onResult: (Result<Record>) -> Unit
)
{
    enqueueResult { result ->
        val savedResult = result.map(convert)
        savedResult.onSuccess(updateCache)
        onResult(savedResult)
    }
}

//4.output

//read an error returned by breed recognition
internal fun <T> Response<T>.aiFailure(): Throwable
{
    return apiFailure("Breed recognition failed with HTTP ${code()}.")
}

//read server validation messages and field errors
internal fun <T> Response<T>.apiFailure(
    fallback: String = "The server could not complete this request (HTTP ${code()})."
): Throwable
{
    val responseText: String = errorBody()?.string().orEmpty()

    return runCatching {
        val payload: JSONObject = JSONObject(responseText)
        val validationErrors: JSONObject? = payload.optJSONObject("errors")
        val fieldErrors: MutableMap<String, String> = mutableMapOf()

        validationErrors?.keys()?.forEach { field ->
            val messages: JSONArray? = validationErrors.optJSONArray(field)
            val firstMessage: String = messages?.optString(0).orEmpty()

            if (firstMessage.isNotBlank())
            {
                fieldErrors[field] = firstMessage
            }
        }

        val message: String = payload.optString("message")
            .ifBlank { fieldErrors.values.firstOrNull().orEmpty() }
            .ifBlank { fallback }

        ApiRequestException(message, code(), fieldErrors)
    }.getOrElse {
        ApiRequestException(fallback, code())
    }
}

//send empty optional fields as null
internal fun String.nullIfBlank(): String?
{
    return ifBlank { null }
}

//replace a cached record or append a newly created record
internal fun <T> List<T>.replaceOrAdd(value: T, id: (T) -> Long): List<T>
{
    val recordIndex: Int = indexOfFirst { record -> id(record) == id(value) }

    if (recordIndex < 0)
    {
        return this + value
    }

    return toMutableList().apply {
        this[recordIndex] = value
    }
}
