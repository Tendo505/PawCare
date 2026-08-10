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
import com.example.pawcareai.data.AppRepository
import com.example.pawcareai.validation.AuthInputValidator
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class MainActivity : AppCompatActivity() {
    private lateinit var repository: AppRepository
    private lateinit var nameLayout: TextInputLayout
    private lateinit var emailLayout: TextInputLayout
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var nameInput: TextInputEditText
    private lateinit var emailInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var submitButton: MaterialButton
    private lateinit var toggleText: TextView
    private lateinit var formTitle: TextView
    private var registerMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = AppRepository(this)
        if (repository.currentUser != null) {
            openDashboard()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        nameLayout = findViewById(R.id.nameLayout)
        emailLayout = findViewById(R.id.emailLayout)
        passwordLayout = findViewById(R.id.passwordLayout)
        nameInput = findViewById(R.id.nameInput)
        emailInput = findViewById(R.id.usernameInput)
        passwordInput = findViewById(R.id.passwordInput)
        submitButton = findViewById(R.id.loginButton)
        toggleText = findViewById(R.id.signUpText)
        formTitle = findViewById(R.id.formTitle)

        submitButton.setOnClickListener { submit() }
        toggleText.setOnClickListener {
            registerMode = !registerMode
            renderMode()
        }
        findViewById<MaterialButton>(R.id.demoButton).setOnClickListener {
            repository.loginDemo()
            openDashboard()
        }
    }

    private fun submit() {
        val name = nameInput.text?.toString().orEmpty()
        val email = emailInput.text?.toString().orEmpty()
        val password = passwordInput.text?.toString().orEmpty()
        val validation = AuthInputValidator.validate(name, email, password, registerMode)

        nameLayout.error = validation.nameError
        emailLayout.error = validation.emailError
        passwordLayout.error = validation.passwordError
        if (!validation.isValid) return

        val result = if (registerMode) {
            repository.register(name, email, password)
        } else {
            repository.login(email, password)
        }
        result.onSuccess { openDashboard() }
            .onFailure { Toast.makeText(this, it.message ?: "Unable to continue", Toast.LENGTH_LONG).show() }
    }

    private fun renderMode() {
        nameLayout.error = null
        emailLayout.error = null
        passwordLayout.error = null
        nameLayout.visibility = if (registerMode) View.VISIBLE else View.GONE
        formTitle.text = if (registerMode) "Create your account" else "Welcome back"
        submitButton.text = if (registerMode) getString(R.string.register) else getString(R.string.login)
        toggleText.text = if (registerMode) "Already have an account? Sign in" else getString(R.string.don_t_have_an_account_sign_up)
    }

    private fun openDashboard() {
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }
}
