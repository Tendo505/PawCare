package com.example.pawcareai.data

import android.content.Context
import android.content.SharedPreferences
import com.example.pawcareai.network.NetworkModule
import com.example.pawcareai.network.PawCareApi

//the one shared place for the login token, the signed-in account and the api connection
class ApiSession(context: Context)
{
    //the phone storage that keeps the token after the app is closed
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences("pawcare_auth", Context.MODE_PRIVATE)

    //the retrofit api used by every repository
    val api: PawCareApi = NetworkModule.backend

    //the account returned by laravel, or null when nobody is signed in
    var activeUser: UserAccount? = null

    //1.screen setup

    init
    {
        //send the saved token with every request from the start
        val savedToken: String? = preferences.getString(TOKEN_KEY, null)
        NetworkModule.setAuthToken(savedToken)
    }

    //3.process

    //check whether a login token has been saved on this phone
    fun hasSession(): Boolean
    {
        val savedToken: String? = preferences.getString(TOKEN_KEY, null)
        if (savedToken == null || savedToken.isBlank())
        {
            return false
        }
        return true
    }

    //remember the token and the account after logging in or registering
    fun saveToken(token: String, user: UserAccount)
    {
        val editor: SharedPreferences.Editor = preferences.edit()
        editor.putString(TOKEN_KEY, token)
        editor.apply()

        NetworkModule.setAuthToken(token)
        activeUser = user
    }

    //forget the token and the account after logging out or when the token has expired
    fun clearToken()
    {
        val editor: SharedPreferences.Editor = preferences.edit()
        editor.remove(TOKEN_KEY)
        editor.apply()

        NetworkModule.setAuthToken(null)
        activeUser = null
    }

    //the name the token is saved under
    companion object
    {
        private const val TOKEN_KEY = "sanctum_token"
    }
}
