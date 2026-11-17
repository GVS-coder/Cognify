package com.cognify.data.repository

import com.cognify.data.model.Resource
import com.cognify.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import com.google.firebase.auth.GoogleAuthProvider

class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) : AuthRepository {

    override suspend fun login(email: String, password: String): Resource<User> {
        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user
            if (firebaseUser != null) {
                Resource.Success(
                    User(
                        uid = firebaseUser.uid,
                        email = firebaseUser.email ?: "",
                        displayName = firebaseUser.displayName ?: ""
                    )
                )
            } else {
                Resource.Error("Login failed. Please try again.")
            }
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Resource.Error("Invalid email or password. Please check your credentials.", e)
        } catch (e: FirebaseAuthInvalidUserException) {
            Resource.Error("No account found with this email. Please sign up.", e)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "An unexpected error occurred.", e)
        }
    }

    override suspend fun signup(
        email: String,
        password: String,
        displayName: String
    ): Resource<User> {
        return try {
            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user

            if (firebaseUser != null) {
                // Update display name
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName)
                    .build()
                firebaseUser.updateProfile(profileUpdates).await()

                Resource.Success(
                    User(
                        uid = firebaseUser.uid,
                        email = firebaseUser.email ?: "",
                        displayName = displayName
                    )
                )
            } else {
                Resource.Error("Sign up failed. Please try again.")
            }
        } catch (e: FirebaseAuthWeakPasswordException) {
            Resource.Error("Password is too weak. Please use at least 6 characters.", e)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Resource.Error("Invalid email address format.", e)
        } catch (e: FirebaseAuthUserCollisionException) {
            Resource.Error("An account already exists with this email. Please log in.", e)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "An unexpected error occurred.", e)
        }
    }

    override suspend fun signInWithGoogle(idToken: String): Resource<User> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = firebaseAuth.signInWithCredential(credential).await()
            val firebaseUser = result.user

            if (firebaseUser != null) {
                Resource.Success(
                    User(
                        uid = firebaseUser.uid,
                        email = firebaseUser.email ?: "",
                        displayName = firebaseUser.displayName ?: ""
                    )
                )
            } else {
                Resource.Error("Google Sign In failed. Please try again.")
            }
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Resource.Error("Invalid Google credentials. Please try again.", e)
        } catch (e: FirebaseAuthUserCollisionException) {
            Resource.Error("An account already exists with the same email address but different sign-in credentials. Sign in using a provider associated with this email address.", e)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "An unexpected error occurred during Google Sign In.", e)
        }
    }

    override suspend fun resetPassword(email: String): Resource<Unit> {
        return try {
            firebaseAuth.sendPasswordResetEmail(email).await()
            Resource.Success(Unit)
        } catch (e: FirebaseAuthInvalidUserException) {
            Resource.Error("No account found with this email address.", e)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to send reset email.", e)
        }
    }

    override fun getCurrentUser(): User? {
        val firebaseUser = firebaseAuth.currentUser ?: return null
        return User(
            uid = firebaseUser.uid,
            email = firebaseUser.email ?: "",
            displayName = firebaseUser.displayName ?: ""
        )
    }

    override fun isUserLoggedIn(): Boolean {
        return firebaseAuth.currentUser != null
    }

    override fun logout() {
        firebaseAuth.signOut()
    }

    override suspend fun reauthenticate(password: String): Resource<Unit> {
        val user = firebaseAuth.currentUser ?: return Resource.Error("No user session active.")
        val email = user.email ?: return Resource.Error("User email not available.")
        return try {
            val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, password)
            user.reauthenticate(credential).await()
            Resource.Success(Unit)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Resource.Error("Incorrect password. Please verify and try again.", e)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Reauthentication failed.", e)
        }
    }

    override suspend fun reauthenticateWithGoogle(idToken: String): Resource<Unit> {
        val user = firebaseAuth.currentUser ?: return Resource.Error("No user session active.")
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            user.reauthenticate(credential).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Google reauthentication failed.", e)
        }
    }

    override fun getAuthProviders(): List<String> {
        return firebaseAuth.currentUser?.providerData?.map { it.providerId } ?: emptyList()
    }
}
