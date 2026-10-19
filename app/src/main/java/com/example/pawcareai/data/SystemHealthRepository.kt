package com.example.pawcareai.data

import com.example.pawcareai.network.ApiSystemHealth
import com.example.pawcareai.network.PawCareApi

//asks laravel whether laravel itself, the database and the ai service are working
class SystemHealthRepository(private val api: PawCareApi)
{
    //the result of the most recent check, or null before the first check
    private var latestHealth: SystemHealth? = null

    //3.process

    //check the services (GET health); if laravel cannot be reached, everything is shown as unavailable
    fun checkSystemHealth(onResult: (Result<SystemHealth>) -> Unit)
    {
        api.systemHealth().enqueue(ApiCallback<ApiSystemHealth>(
            onSuccess = { apiHealth ->
                val health: SystemHealth = toSystemHealth(apiHealth)
                latestHealth = health
                onResult(Result.success(health))
            },
            onFailure = { error ->
                latestHealth = SystemHealth(
                    overall = "unavailable",
                    laravel = "unavailable",
                    database = "unknown",
                    ai = "unknown"
                )
                onResult(Result.failure(error))
            }
        ))
    }

    //4.output

    //read the most recent check
    fun systemHealth(): SystemHealth?
    {
        return latestHealth
    }

    //copy the status of each service into the app's own class
    private fun toSystemHealth(apiHealth: ApiSystemHealth): SystemHealth
    {
        return SystemHealth(
            overall = apiHealth.status,
            laravel = apiHealth.services.laravel.status,
            database = apiHealth.services.database.status,
            ai = apiHealth.services.ai.status,
            aiModelVersion = apiHealth.services.ai.modelVersion ?: "",
            checkedAt = apiHealth.checkedAt
        )
    }
}
