package com.cognify.data.repository

import com.cognify.data.model.Resource
import com.cognify.data.model.User

interface AuthRepository {

    /**
     * Sign in with email and password.
     */
    suspend fun login(email: String, password: String): Resource<User>

    /**
     * Register a new account with email and password.
     */
    suspend fun signup(email: String, password: String, displayName: String): Resource<User>

    /**
     * Sign in with Google using an ID Token.
     */
    suspend fun signInWithGoogle(idToken: String): Resource<User>

    /**
     * Send password reset email.
     */
    suspend fun resetPassword(email: String): Resource<Unit>

    /**
     * Get current authenticated user, or null if not logged in.
     */
    fun getCurrentUser(): User?

    /**
     * Returns true if a user session is active.
     */
    fun isUserLoggedIn(): Boolean

    /**
     * Sign out the current user.
     */
    fun logout()

    /**
     * Reauthenticate current user using email and password.
     */
    suspend fun reauthenticate(password: String): Resource<Unit>

    /**
     * Reauthenticate current user using Google ID token.
     */
    suspend fun reauthenticateWithGoogle(idToken: String): Resource<Unit>

    /**
     * Returns list of authentication provider IDs linked to current account (e.g. "password", "google.com").
     */
    fun getAuthProviders(): List<String>
}
