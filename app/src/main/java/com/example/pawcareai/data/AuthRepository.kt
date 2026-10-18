package com.example.pawcareai.data

import com.example.pawcareai.network.ApiMessage
import com.example.pawcareai.network.ApiUser
import com.example.pawcareai.network.AuthRequest
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

//account actions: restore, register, login and logout
class AuthRepository(
    private val session: ApiSession,
    private val onSessionCleared: () -> Unit
)
{
    //2.input

    //prepare account details for laravel
    private fun authRequest(email: String, password: String, name: String? = null): AuthRequest
    {
        return AuthRequest(email.trim().lowercase(), password, name?.trim())
    }

    //3.process

    //restore the account using its saved token
    fun restoreSession(onResult: (Result<UserAccount>) -> Unit)
    {
        if (!session.hasSession)
        {
            onResult(Result.failure(IllegalStateException("Please sign in.")))
            return
        }

        session.api.me().enqueueResult { result ->
            result.onSuccess { user ->
                session.activeUser = user.toUserAccount()
            }
            result.onFailure { error ->
                //a lost connection does not mean the saved token is invalid
                if (error is ApiRequestException && error.statusCode == 401)
                {
                    clearSession()
                }
            }

            val accountResult: Result<UserAccount> = result.map { user -> user.toUserAccount() }
            onResult(accountResult)
        }
    }

    //create a new account through laravel
    fun register(name: String, email: String, password: String, onResult: (Result<UserAccount>) -> Unit)
    {
        session.api.register(authRequest(email, password, name)).enqueueResult { result ->
            result.onSuccess { authentication ->
                session.saveToken(authentication.token, authentication.user.toUserAccount())
            }

            val accountResult: Result<UserAccount> = result.map { authentication ->
                authentication.user.toUserAccount()
            }
            onResult(accountResult)
        }
    }

    //sign in and save the returned token
    fun login(email: String, password: String, onResult: (Result<UserAccount>) -> Unit)
    {
        session.api.login(authRequest(email, password)).enqueueResult { result ->
            result.onSuccess { authentication ->
                session.saveToken(authentication.token, authentication.user.toUserAccount())
            }

            val accountResult: Result<UserAccount> = result.map { authentication ->
                authentication.user.toUserAccount()
            }
            onResult(accountResult)
        }
    }

    //sign out and clear the local session
    fun logout(onComplete: () -> Unit)
    {
        if (!session.hasSession)
        {
            clearSession()
            onComplete()
            return
        }
        session.api.logout().enqueue(object : Callback<ApiMessage>
        {
            override fun onResponse(call: Call<ApiMessage>, response: Response<ApiMessage>)
            {
                clearSession()
                onComplete()
            }

            override fun onFailure(call: Call<ApiMessage>, throwable: Throwable)
            {
                clearSession()
                onComplete()
            }
        })
    }

    //remove the token and records belonging to the current session
    private fun clearSession()
    {
        session.clearToken()
        onSessionCleared()
    }

    //4.output

    //convert api account details for the app
    private fun ApiUser.toUserAccount(): UserAccount
    {
        return UserAccount(id = id, name = name, email = email)
    }
}
