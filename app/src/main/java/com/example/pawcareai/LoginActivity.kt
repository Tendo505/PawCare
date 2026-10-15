package com.example.pawcareai

class LoginActivity : AuthActivity()
{
    //1.screen setup
    override val layoutResource: Int = R.layout.activity_login
    override val registerMode: Boolean = false
}
