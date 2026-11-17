package com.cognify.data.repository

import android.net.Uri
import com.cognify.data.model.Resource
import com.cognify.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ProfileRepository {

    private var localDisplayName: String = ""

    override suspend fun updateDisplayName(displayName: String): Resource<Unit> {
        localDisplayName = displayName
        val user = auth.currentUser
        if (user != null) {
            return try {
                val profileUpdates = userProfileChangeRequest {
                    this.displayName = displayName
                }
                user.updateProfile(profileUpdates).await()
                
                try {
                    firestore.collection("users").document(user.uid)
                        .update("name", displayName).await()
                } catch (e: Exception) {
                    // Ignore firestore offline error
                }
                Resource.Success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Error updating display name")
                Resource.Success(Unit) // Local display name updated
            }
        } else {
            return Resource.Success(Unit)
        }
    }

    override suspend fun updateProfilePhoto(photoUri: Uri?): Resource<Unit> {
        val user = auth.currentUser ?: return Resource.Success(Unit)
        return try {
            val profileUpdates = userProfileChangeRequest {
                this.photoUri = photoUri
            }
            user.updateProfile(profileUpdates).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error updating profile photo")
            Resource.Success(Unit)
        }
    }

    override suspend fun deleteAccount(): Resource<Unit> {
        val user = auth.currentUser
            ?: return Resource.Error("User session is missing or expired. Please log in again.")
        val uid = user.uid
        val userRef = firestore.collection("users").document(uid)

        // 1. Verify session freshness before deleting cloud data
        try {
            val lastSignIn = user.metadata?.lastSignInTimestamp ?: 0L
            val elapsedSeconds = if (lastSignIn > 0) (System.currentTimeMillis() - lastSignIn) / 1000 else 0L
            // If authenticated more than 4 minutes (240s) ago, Firebase Auth will require recent login
            if (elapsedSeconds > 240) {
                val providers = user.providerData.map { it.providerId }
                val provider = if (providers.contains(com.google.firebase.auth.GoogleAuthProvider.PROVIDER_ID)) {
                    com.google.firebase.auth.GoogleAuthProvider.PROVIDER_ID
                } else {
                    com.google.firebase.auth.EmailAuthProvider.PROVIDER_ID
                }
                return Resource.Error(
                    "RECENT_LOGIN_REQUIRED:$provider:${user.email ?: ""}",
                    com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException("ERROR_REQUIRES_RECENT_LOGIN", "Recent login required")
                )
            }
        } catch (e: Exception) {
            Timber.w(e, "Could not check login freshness, proceeding with cloud deletion")
        }

        // 2. Delete all subcollections using batched operations
        val subcollections = listOf("tasks", "knowledgeVault", "reminders", "usage")
        for (subCol in subcollections) {
            try {
                deleteCollectionBatched(userRef.collection(subCol))
            } catch (e: Exception) {
                Timber.e(e, "deleteAccount: Failed to delete '$subCol' subcollection for user $uid")
                return Resource.Error("Failed to delete cloud data ($subCol): ${e.message}", e)
            }
        }

        // 3. Delete root user document
        try {
            userRef.delete().await()
        } catch (e: Exception) {
            Timber.e(e, "deleteAccount: Failed to delete root user document for user $uid")
            return Resource.Error("Failed to delete user document: ${e.message}", e)
        }

        // 4. Delete Firebase Authentication account
        return try {
            user.delete().await()
            Resource.Success(Unit)
        } catch (e: com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
            Timber.e(e, "Recent login required for account deletion")
            val providers = user.providerData.map { it.providerId }
            val provider = if (providers.contains(com.google.firebase.auth.GoogleAuthProvider.PROVIDER_ID)) {
                com.google.firebase.auth.GoogleAuthProvider.PROVIDER_ID
            } else {
                com.google.firebase.auth.EmailAuthProvider.PROVIDER_ID
            }
            Resource.Error("RECENT_LOGIN_REQUIRED:$provider:${user.email ?: ""}", e)
        } catch (e: Exception) {
            Timber.e(e, "deleteAccount: Error deleting Firebase Auth account for user $uid")
            Resource.Error(e.message ?: "Failed to delete authentication account", e)
        }
    }

    private suspend fun deleteCollectionBatched(collectionRef: com.google.firebase.firestore.CollectionReference) {
        val batchSize = 100
        while (true) {
            val snapshot = collectionRef.limit(batchSize.toLong()).get().await()
            if (snapshot.isEmpty) {
                break
            }
            val batch = firestore.batch()
            for (doc in snapshot.documents) {
                batch.delete(doc.reference)
            }
            batch.commit().await()
            if (snapshot.size() < batchSize) {
                break
            }
        }
    }

    override fun getCurrentUserFlow(): Flow<User?> = callbackFlow {
        val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val firebaseUser = firebaseAuth.currentUser
            if (firebaseUser != null) {
                val name = if (!firebaseUser.displayName.isNullOrBlank()) {
                    firebaseUser.displayName!!
                } else if (localDisplayName.isNotBlank()) {
                    localDisplayName
                } else {
                    firebaseUser.email?.substringBefore("@") ?: "Cognify User"
                }
                val user = User(
                    uid = firebaseUser.uid,
                    displayName = name,
                    email = firebaseUser.email ?: "user@cognify.app",
                    photoUrl = firebaseUser.photoUrl?.toString()
                )
                trySend(user)
            } else {
                trySend(null)
            }
        }
        
        auth.addAuthStateListener(authStateListener)
        
        awaitClose {
            auth.removeAuthStateListener(authStateListener)
        }
    }
}
