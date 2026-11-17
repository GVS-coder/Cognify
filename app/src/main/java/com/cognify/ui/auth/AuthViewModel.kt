package com.cognify.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cognify.data.model.Resource
import com.cognify.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableLiveData(AuthUiState())
    val uiState: LiveData<AuthUiState> = _uiState

    // ─────────────────────────────────────────────────────────────
    // Session check
    // ─────────────────────────────────────────────────────────────

    fun isUserLoggedIn(): Boolean = authRepository.isUserLoggedIn()

    // ─────────────────────────────────────────────────────────────
    // Login
    // ─────────────────────────────────────────────────────────────

    fun login(email: String, password: String) {
        if (!validateLoginInputs(email, password)) return

        viewModelScope.launch {
            _uiState.value = _uiState.value?.copy(
                isLoading = true,
                errorMessage = null,
                emailError = null,
                passwordError = null
            )

            when (val result = authRepository.login(email.trim(), password)) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value?.copy(
                        isLoading = false,
                        loginSuccess = result.data
                    )
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value?.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
                is Resource.Loading -> { /* handled above */ }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Google Login
    // ─────────────────────────────────────────────────────────────

    fun loginWithGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value?.copy(
                isLoading = true,
                errorMessage = null
            )

            when (val result = authRepository.signInWithGoogle(idToken)) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value?.copy(
                        isLoading = false,
                        loginSuccess = result.data
                    )
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value?.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
                is Resource.Loading -> { /* handled above */ }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Signup
    // ─────────────────────────────────────────────────────────────

    fun signup(displayName: String, email: String, password: String, confirmPassword: String) {
        if (!validateSignupInputs(displayName, email, password, confirmPassword)) return

        viewModelScope.launch {
            _uiState.value = _uiState.value?.copy(
                isLoading = true,
                errorMessage = null,
                emailError = null,
                passwordError = null,
                confirmPasswordError = null,
                displayNameError = null
            )

            when (val result = authRepository.signup(email.trim(), password, displayName.trim())) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value?.copy(
                        isLoading = false,
                        signupSuccess = result.data
                    )
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value?.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
                is Resource.Loading -> { /* handled above */ }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Forgot Password
    // ─────────────────────────────────────────────────────────────

    fun sendPasswordReset(email: String) {
        if (email.isBlank()) {
            _uiState.value = _uiState.value?.copy(emailError = "Email is required")
            return
        }
        if (!isValidEmail(email)) {
            _uiState.value = _uiState.value?.copy(emailError = "Enter a valid email address")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value?.copy(
                isLoading = true,
                errorMessage = null,
                emailError = null
            )

            when (val result = authRepository.resetPassword(email.trim())) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value?.copy(
                        isLoading = false,
                        passwordResetEmailSent = true,
                        successMessage = "Password reset email sent. Please check your inbox."
                    )
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value?.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
                is Resource.Loading -> { /* handled above */ }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Logout
    // ─────────────────────────────────────────────────────────────

    fun logout() {
        authRepository.logout()
        _uiState.value = AuthUiState()
    }

    // ─────────────────────────────────────────────────────────────
    // UI helpers
    // ─────────────────────────────────────────────────────────────

    fun clearErrors() {
        _uiState.value = _uiState.value?.copy(
            errorMessage = null,
            emailError = null,
            passwordError = null,
            confirmPasswordError = null,
            displayNameError = null
        )
    }

    fun clearSuccess() {
        _uiState.value = _uiState.value?.copy(
            successMessage = null,
            passwordResetEmailSent = false
        )
    }

    // ─────────────────────────────────────────────────────────────
    // Validation
    // ─────────────────────────────────────────────────────────────

    private fun validateLoginInputs(email: String, password: String): Boolean {
        var isValid = true
        var state = _uiState.value ?: AuthUiState()

        if (email.isBlank()) {
            state = state.copy(emailError = "Email is required")
            isValid = false
        } else if (!isValidEmail(email)) {
            state = state.copy(emailError = "Enter a valid email address")
            isValid = false
        } else {
            state = state.copy(emailError = null)
        }

        if (password.isBlank()) {
            state = state.copy(passwordError = "Password is required")
            isValid = false
        } else if (password.length < 6) {
            state = state.copy(passwordError = "Password must be at least 6 characters")
            isValid = false
        } else {
            state = state.copy(passwordError = null)
        }

        _uiState.value = state
        return isValid
    }

    private fun validateSignupInputs(
        displayName: String,
        email: String,
        password: String,
        confirmPassword: String
    ): Boolean {
        var isValid = true
        var state = _uiState.value ?: AuthUiState()

        if (displayName.isBlank()) {
            state = state.copy(displayNameError = "Full name is required")
            isValid = false
        } else if (displayName.trim().length < 2) {
            state = state.copy(displayNameError = "Name must be at least 2 characters")
            isValid = false
        } else {
            state = state.copy(displayNameError = null)
        }

        if (email.isBlank()) {
            state = state.copy(emailError = "Email is required")
            isValid = false
        } else if (!isValidEmail(email)) {
            state = state.copy(emailError = "Enter a valid email address")
            isValid = false
        } else {
            state = state.copy(emailError = null)
        }

        if (password.isBlank()) {
            state = state.copy(passwordError = "Password is required")
            isValid = false
        } else if (password.length < 6) {
            state = state.copy(passwordError = "Password must be at least 6 characters")
            isValid = false
        } else {
            state = state.copy(passwordError = null)
        }

        if (confirmPassword.isBlank()) {
            state = state.copy(confirmPasswordError = "Please confirm your password")
            isValid = false
        } else if (password != confirmPassword) {
            state = state.copy(confirmPasswordError = "Passwords do not match")
            isValid = false
        } else {
            state = state.copy(confirmPasswordError = null)
        }

        _uiState.value = state
        return isValid
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}
