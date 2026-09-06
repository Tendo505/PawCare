package com.example.pawcareai.validation

data class AuthValidationResult(
    val nameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null
)
{
    val isValid: Boolean
        get() = nameError == null && emailError == null && passwordError == null
}

object AuthInputValidator
{
    private const val MIN_PASSWORD_LENGTH = 8
    private const val MAX_EMAIL_LENGTH = 254
    private val emailPattern = Regex(
        "^[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?(?:\\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+$",
        RegexOption.IGNORE_CASE
    )

    // Validate the sign-in or registration form before sending the request.
    fun validate(
        name: String,
        email: String,
        password: String,
        registerMode: Boolean
    ): AuthValidationResult
    {
        val cleanName = name.trim()
        val cleanEmail = email.trim()

        return AuthValidationResult(
            nameError = validateName(cleanName, registerMode),
            emailError = validateEmail(cleanEmail),
            passwordError = validatePassword(password, registerMode)
        )
    }

    // Validate the name when the user creates an account.
    private fun validateName(name: String, registerMode: Boolean): String?
    {
        return when
        {
            registerMode && name.length < 2 -> "Enter your full name."
            else -> null
        }
    }

    // Validate the email format, including spaces and misplaced dots.
    private fun validateEmail(email: String): String?
    {
        val localPart = email.substringBefore('@')
        val hasInvalidDots = localPart.startsWith('.') || localPart.endsWith('.') || localPart.contains("..")

        return when
        {
            email.isEmpty() -> "Enter your email address."
            email.length > MAX_EMAIL_LENGTH -> "Email address is too long."
            email.any(Char::isWhitespace) -> "Email address cannot contain spaces."
            hasInvalidDots -> "Enter a valid email address."
            !emailPattern.matches(email) -> "Enter a valid email address."
            else -> null
        }
    }

    // Validate password strength during registration and required input during sign-in.
    private fun validatePassword(password: String, registerMode: Boolean): String?
    {
        return when
        {
            password.isBlank() -> "Enter your password."
            registerMode && password.length < MIN_PASSWORD_LENGTH -> "Password must contain at least 8 characters."
            registerMode && password.none(Char::isLetter) -> "Password must contain at least one letter."
            registerMode && password.none(Char::isDigit) -> "Password must contain at least one number."
            else -> null
        }
    }
}
