package com.example.pawcareai.data

import com.example.pawcareai.network.ApiSystemHealth
import com.example.pawcareai.network.PawCareApi

//laravel, database and ai connection checks
class SystemHealthRepository(private val api: PawCareApi)
{
    //the most recent connection check
    private var systemHealthCache: SystemHealth? = null

    //3.process

    //check laravel, database and ai connections
    fun checkSystemHealth(onResult: (Result<SystemHealth>) -> Unit)
    {
        api.systemHealth().enqueueResult { result ->
            val healthResult: Result<SystemHealth> = result.map { health -> health.toSystemHealth() }
            healthResult.onSuccess { health ->
                systemHealthCache = health
            }
            healthResult.onFailure {
                systemHealthCache = SystemHealth(
                    overall = "unavailable",
                    laravel = "unavailable",
                    database = "unknown",
                    ai = "unknown"
                )
            }
            onResult(healthResult)
        }
    }

    //4.output

    //read the most recent connection check
    fun systemHealth(): SystemHealth?
    {
        return systemHealthCache
    }

    //convert service connection details for the dashboard
    private fun ApiSystemHealth.toSystemHealth(): SystemHealth
    {
        return SystemHealth(
            overall = status,
            laravel = services.laravel.status,
            database = services.database.status,
            ai = services.ai.status,
            aiModelVersion = services.ai.modelVersion.orEmpty(),
            checkedAt = checkedAt
        )
    }
}
