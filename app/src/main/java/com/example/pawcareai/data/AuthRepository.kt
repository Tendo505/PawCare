package com.example.pawcareai.data

import com.example.pawcareai.network.ApiMessage
import com.example.pawcareai.network.ApiUser
import com.example.pawcareai.network.AuthRequest
import com.example.pawcareai.network.AuthResponse

//account actions: restore the saved login, register, log in and log out
class AuthRepository(
    private val session: ApiSession,
    private val records: RecordCache
)
{
    //3.process

    //ask laravel who owns the saved token (GET me)
    fun restoreSession(onResult: (Result<UserAccount>) -> Unit)
    {
        if (!session.hasSession())
        {
            onResult(Result.failure(IllegalStateException("Please sign in.")))
            return
        }

        session.api.me().enqueue(ApiCallback<ApiUser>(
            onSuccess = { apiUser ->
                val account: UserAccount = toUserAccount(apiUser)
                session.activeUser = account
                onResult(Result.success(account))
            },
            onFailure = { error ->
                //only a 401 means the token is invalid; a lost connection keeps the token
                if (error is ApiRequestException && error.statusCode == 401)
                {
                    clearSession()
                }
                onResult(Result.failure(error))
            }
        ))
    }

    //create a new account (POST register)
    fun register(name: String, email: String, password: String, onResult: (Result<UserAccount>) -> Unit)
    {
        val request: AuthRequest = AuthRequest(email.trim().lowercase(), password, name.trim())
        session.api.register(request).enqueue(createLoginCallback(onResult))
    }

    //sign in with an existing account (POST login)
    fun login(email: String, password: String, onResult: (Result<UserAccount>) -> Unit)
    {
        val request: AuthRequest = AuthRequest(email.trim().lowercase(), password)
        session.api.login(request).enqueue(createLoginCallback(onResult))
    }

    //register and login both answer with a token and the account, so they share this callback
    private fun createLoginCallback(onResult: (Result<UserAccount>) -> Unit): ApiCallback<AuthResponse>
    {
        return ApiCallback<AuthResponse>(
            onSuccess = { authentication ->
                val account: UserAccount = toUserAccount(authentication.user)
                session.saveToken(authentication.token, account)
                onResult(Result.success(account))
            },
            onFailure = { error ->
                onResult(Result.failure(error))
            }
        )
    }

    //sign out (POST logout); the phone forgets the session even if laravel cannot be reached
    fun logout(onComplete: () -> Unit)
    {
        if (!session.hasSession())
        {
            clearSession()
            onComplete()
            return
        }

        session.api.logout().enqueue(ApiCallback<ApiMessage>(
            onSuccess = { message ->
                clearSession()
                onComplete()
            },
            onFailure = { error ->
                clearSession()
                onComplete()
            }
        ))
    }

    //remove the token and every record of this account from memory
    private fun clearSession()
    {
        session.clearToken()
        records.clearAll()
    }

    //4.output

    //copy the account details sent by laravel into the app's own class
    private fun toUserAccount(apiUser: ApiUser): UserAccount
    {
        return UserAccount(id = apiUser.id, name = apiUser.name, email = apiUser.email)
    }
}
