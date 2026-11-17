package com.cognify.ui.auth

import com.cognify.data.model.User

/**
 * Represents the complete UI state for authentication screens.
 */
data class AuthUiState(
    // Shared
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,

    // Login
    val loginSuccess: User? = null,

    // Signup
    val signupSuccess: User? = null,

    // Forgot Password
    val passwordResetEmailSent: Boolean = false,

    // Validation errors
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val displayNameError: String? = null
)
