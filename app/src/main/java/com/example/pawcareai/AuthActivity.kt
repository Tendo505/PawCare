package com.example.pawcareai

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.pawcareai.data.ApiRequestException
import com.example.pawcareai.data.AppRepository
import com.example.pawcareai.data.UserAccount
import com.example.pawcareai.validation.AuthInputValidator
import com.example.pawcareai.validation.AuthValidationResult
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

//shared validation and api handling for the two account screens
abstract class AuthActivity : AppCompatActivity(), View.OnClickListener
{
    protected abstract val layoutResource: Int
    protected abstract val registerMode: Boolean

    private data class FormDraft(val name: String, val email: String, val password: String)

    companion object
    {
        //pass the form once in memory, never through intents or stored preferences
        private var pendingDraft: FormDraft? = null
    }

    //account data
    private lateinit var repository: AppRepository

    //input fields and messages
    private lateinit var nameLayout: TextInputLayout
    private lateinit var emailLayout: TextInputLayout
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var nameInput: TextInputEditText
    private lateinit var emailInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText

    //form buttons and labels
    private lateinit var submitButton: MaterialButton
    private lateinit var toggleText: TextView

    //1.screen setup

    //create the selected account screen
    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        repository = AppRepository(this)
        enableEdgeToEdge()
        setContentView(layoutResource)

        setScreenInsets()
        initializeForm()
        pendingDraft?.let { draft ->
            nameInput.setText(draft.name)
            emailInput.setText(draft.email)
            passwordInput.setText(draft.password)
        }
        pendingDraft = null
        setFormListeners()
        restoreAccount()
    }

    //keep the form clear of the system bars
    private fun setScreenInsets()
    {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    //connect the form to its xml views
    private fun initializeForm()
    {
        nameLayout = findViewById(R.id.nameLayout)
        emailLayout = findViewById(R.id.emailLayout)
        passwordLayout = findViewById(R.id.passwordLayout)
        nameInput = findViewById(R.id.nameInput)
        emailInput = findViewById(R.id.usernameInput)
        passwordInput = findViewById(R.id.passwordInput)
        submitButton = findViewById(R.id.loginButton)
        toggleText = findViewById(R.id.signUpText)
    }

    //register button actions
    private fun setFormListeners()
    {
        submitButton.setOnClickListener(this)
        toggleText.setOnClickListener(this)
    }

    //2.input

    //handle login and registration buttons
    override fun onClick(view: View?)
    {
        when (view?.id)
        {
            R.id.loginButton -> submit()
            R.id.signUpText -> changeFormMode()
        }
    }

    //validate the form before sending it
    private fun submit()
    {
        val name: String = nameInput.text?.toString().orEmpty()
        val email: String = emailInput.text?.toString().orEmpty()
        val password: String = passwordInput.text?.toString().orEmpty()

        val validation = AuthInputValidator.validate(name, email, password, registerMode)
        displayValidation(validation)
        if (!validation.isValid) return

        authenticate(name, email, password)
    }

    //3.process

    //restore the saved account session
    private fun restoreAccount()
    {
        if (!repository.hasSession) return

        setFormBusy(true)
        repository.restoreSession { result ->
            if (isFinishing || isDestroyed) return@restoreSession
            setFormBusy(false)
            result.onSuccess { openDashboard() }
        }
    }

    //send the account details to laravel
    private fun authenticate(name: String, email: String, password: String)
    {
        setFormBusy(true)
        val onResult: (Result<UserAccount>) -> Unit = { result ->
            if (!isFinishing && !isDestroyed)
            {
                setFormBusy(false)
                result
                    .onSuccess { openDashboard() }
                    .onFailure(::displayAuthenticationError)
            }
        }

        if (registerMode)
        {
            repository.register(name, email, password, onResult)
        }
        else
        {
            repository.login(email, password, onResult)
        }
    }

    //switch between login and registration
    private fun changeFormMode()
    {
        val screen = if (registerMode) LoginActivity::class.java else RegisterActivity::class.java
        val screenIntent = Intent(this, screen)
        //keep entered details when switching, just like the original form toggle
        pendingDraft = FormDraft(
            nameInput.text?.toString().orEmpty(),
            emailInput.text?.toString().orEmpty(),
            passwordInput.text?.toString().orEmpty()
        )
        startActivity(screenIntent)
        finish()
    }

    //4.output

    //display input validation messages
    private fun displayValidation(validation: AuthValidationResult)
    {
        nameLayout.error = validation.nameError
        emailLayout.error = validation.emailError
        passwordLayout.error = validation.passwordError
    }

    //display errors returned by laravel
    private fun displayAuthenticationError(error: Throwable)
    {
        if (error is ApiRequestException)
        {
            nameLayout.error = error.fieldErrors["name"]
            emailLayout.error = error.fieldErrors["email"]
            passwordLayout.error = error.fieldErrors["password"]
        }

        val message = error.message
            ?.takeIf { it.isNotBlank() }
            ?: "Unable to sign in. Check the details and make sure Laravel is running."
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    //prevent another submission while waiting for the server
    private fun setFormBusy(busy: Boolean)
    {
        submitButton.isEnabled = !busy
        toggleText.isEnabled = !busy
        submitButton.text = when
        {
            busy -> "Please wait…"
            registerMode -> getString(R.string.register)
            else -> getString(R.string.login)
        }
    }

    //5.navigation

    //open the dashboard after authentication
    private fun openDashboard()
    {
        val dashboardIntent = Intent(this, DashboardActivity::class.java)
        startActivity(dashboardIntent)
        finish()
    }
}
