package com.example.pawcareai.validation

data class AuthValidationResult(
    val nameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null
) {
    val isValid: Boolean
        get() = nameError == null && emailError == null && passwordError == null
}

object AuthInputValidator {
    private const val MIN_PASSWORD_LENGTH = 8
    private val emailPattern = Regex(
        "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
        RegexOption.IGNORE_CASE
    )

    fun validate(
        name: String,
        email: String,
        password: String,
        registerMode: Boolean
    ): AuthValidationResult {
        val cleanName = name.trim()
        val cleanEmail = email.trim()

        return AuthValidationResult(
            nameError = when {
                registerMode && cleanName.length < 2 -> "Enter your full name."
                else -> null
            },
            emailError = when {
                !emailPattern.matches(cleanEmail) -> "Enter a valid email address."
                else -> null
            },
            passwordError = when {
                password.length < MIN_PASSWORD_LENGTH -> "Password must contain at least 8 characters."
                else -> null
            }
        )
    }
}

