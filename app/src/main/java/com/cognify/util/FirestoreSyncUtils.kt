package com.cognify.util

import com.google.firebase.firestore.CollectionReference
import kotlinx.coroutines.tasks.await
import timber.log.Timber

object FirestoreSyncUtils {

    suspend fun syncItemToFirestore(
        collection: CollectionReference,
        documentId: String,
        data: Any,
        onSuccess: suspend () -> Unit,
        onFailure: suspend (Exception) -> Unit
    ) {
        try {
            collection.document(documentId).set(data).await()
            onSuccess()
        } catch (e: Exception) {
            Timber.e(e, "Error syncing item to Firestore: $documentId")
            onFailure(e)
        }
    }
}
