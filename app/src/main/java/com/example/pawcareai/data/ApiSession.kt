package com.example.pawcareai.data

import android.content.Context
import android.content.SharedPreferences
import com.example.pawcareai.network.NetworkModule
import com.example.pawcareai.network.PawCareApi

//the one shared place for the saved token, signed-in account and api connection
class ApiSession(context: Context)
{
    //authentication settings and api connection
    private val preferences: SharedPreferences = context.applicationContext
        .getSharedPreferences(AUTH_PREFERENCES, Context.MODE_PRIVATE)
    val api: PawCareApi = NetworkModule.backend

    //account returned by laravel for the current session
    var activeUser: UserAccount? = null

    //1.screen setup

    init
    {
        NetworkModule.setAuthToken(preferences.getString(KEY_TOKEN, null))
    }

    //3.process

    //check whether an authentication token has been saved
    val hasSession: Boolean
        get()
        {
            val token: String? = preferences.getString(KEY_TOKEN, null)
            return !token.isNullOrBlank()
        }

    //save the session token and active account
    fun saveToken(token: String, user: UserAccount)
    {
        val sessionEditor: SharedPreferences.Editor = preferences.edit()
        sessionEditor.putString(KEY_TOKEN, token)
        sessionEditor.apply()

        NetworkModule.setAuthToken(token)
        activeUser = user
    }

    //remove the token and the active account
    fun clearToken()
    {
        val sessionEditor: SharedPreferences.Editor = preferences.edit()
        sessionEditor.remove(KEY_TOKEN)
        sessionEditor.apply()

        NetworkModule.setAuthToken(null)
        activeUser = null
    }

    //preference names used by the authentication session
    companion object
    {
        private const val AUTH_PREFERENCES = "pawcare_auth"
        private const val KEY_TOKEN = "sanctum_token"
    }
}
