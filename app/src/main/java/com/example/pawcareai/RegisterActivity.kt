package com.example.pawcareai

class RegisterActivity : AuthActivity()
{
    //1.screen setup
    override val layoutResource: Int = R.layout.activity_register
    override val registerMode: Boolean = true
}
