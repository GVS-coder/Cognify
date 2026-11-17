package com.cognify.data.repository

import com.cognify.data.local.KnowledgeDao
import com.cognify.data.local.KnowledgeEntity
import com.cognify.data.model.Difficulty
import com.cognify.data.model.KnowledgeType
import com.cognify.data.model.ProgrammingLanguage
import com.cognify.data.model.SyncStatus
import com.cognify.util.FirestoreSyncUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KnowledgeRepositoryImpl @Inject constructor(
    private val dao: KnowledgeDao,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : KnowledgeRepository {

    private val scope = CoroutineScope(Dispatchers.IO)

    private fun userCollection() = auth.currentUser?.uid?.let { uid ->
        firestore.collection("users").document(uid).collection("knowledgeVault")
    }

    // ── Observe ────────────────────────────────────────────────────────────────
    override fun getAllKnowledge(): Flow<List<KnowledgeEntity>> = dao.getAllKnowledge(auth.currentUser?.uid ?: "")
    override fun getRecentKnowledge(): Flow<List<KnowledgeEntity>> = dao.getRecentKnowledge(auth.currentUser?.uid ?: "")
    override fun getFavorites(): Flow<List<KnowledgeEntity>> = dao.getFavorites(auth.currentUser?.uid ?: "")
    override fun getByType(type: String): Flow<List<KnowledgeEntity>> = dao.getByType(type, auth.currentUser?.uid ?: "")
    override fun search(query: String): Flow<List<KnowledgeEntity>> = dao.search(query, auth.currentUser?.uid ?: "")
    override suspend fun getById(id: String): KnowledgeEntity? = dao.getKnowledgeById(id, auth.currentUser?.uid ?: "")

    // ── Write ──────────────────────────────────────────────────────────────────
    override suspend fun saveKnowledge(item: KnowledgeEntity) {
        val localItem = item.copy(syncStatus = SyncStatus.PENDING, userId = auth.currentUser?.uid ?: "")
        dao.insertKnowledge(localItem)
        scope.launch { syncItemToFirestore(localItem) }
    }

    override suspend fun deleteKnowledge(id: String) {
        dao.deleteKnowledgeById(id, auth.currentUser?.uid ?: "")
        scope.launch {
            try {
                userCollection()?.document(id)?.delete()?.await()
            } catch (e: Exception) {
                Timber.e(e, "Error deleting knowledge from Firestore")
            }
        }
    }

    // ── Sync ───────────────────────────────────────────────────────────────────
    override suspend fun syncPendingToFirestore() {
        val pending = dao.getUnsyncedKnowledge(auth.currentUser?.uid ?: "")
        pending.forEach { item -> syncItemToFirestore(item) }
    }

    override suspend fun syncFromFirestore() {
        val col = userCollection() ?: return
        try {
            val snapshot = col.get().await()
            val remoteItems = snapshot.documents.mapNotNull { doc ->
                try {
                    val existingLocal = dao.getKnowledgeById(doc.id, auth.currentUser?.uid ?: "")
                    val isPrivate = doc.getBoolean("isPrivate") ?: (existingLocal?.isPrivate ?: false)
                    KnowledgeEntity(
                        id = doc.id,
                        title = doc.getString("title") ?: "",
                        description = doc.getString("description") ?: "",
                        type = enumValueOrDefault(doc.getString("type"), KnowledgeType.NOTE),
                        tags = (doc.get("tags") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                        language = enumValueOrDefault(doc.getString("language"), ProgrammingLanguage.NONE),
                        category = doc.getString("category") ?: "",
                        isFavorite = doc.getBoolean("isFavorite") ?: false,
                        codeBlock = doc.getString("codeBlock"),
                        difficulty = enumValueOrDefault(doc.getString("difficulty"), Difficulty.NONE),
                        source = doc.getString("source"),
                        pinned = doc.getBoolean("pinned") ?: false,
                        folderId = doc.getString("folderId"),
                        embeddingStatus = doc.getString("embeddingStatus"),
                        aiSummary = doc.getString("aiSummary"),
                        lastIndexed = doc.getLong("lastIndexed"),
                        createdAt = doc.getLong("createdAt") ?: 0L,
                        updatedAt = doc.getLong("updatedAt") ?: 0L,
                        syncStatus = SyncStatus.SYNCED,
                        lastSyncedAt = System.currentTimeMillis(),
                        isPrivate = isPrivate,
                        userId = auth.currentUser?.uid ?: ""
                    )
                } catch (e: Exception) { 
                    Timber.e(e, "Failed to parse remote item ${doc.id}")
                    null 
                }
            }
            if (remoteItems.isNotEmpty()) dao.insertAll(remoteItems)
        } catch (e: Exception) { 
            Timber.e(e, "Failed to sync from Firestore")
        }
    }

    private suspend fun syncItemToFirestore(item: KnowledgeEntity) {
        val col = userCollection() ?: return
        if (item.syncStatus == SyncStatus.SYNCED) return
        
        val data = mapOf(
            "title" to item.title,
            "description" to item.description,
            "type" to item.type.name,
            "tags" to item.tags,
            "language" to item.language.name,
            "category" to item.category,
            "isFavorite" to item.isFavorite,
            "codeBlock" to item.codeBlock,
            "difficulty" to item.difficulty.name,
            "source" to item.source,
            "pinned" to item.pinned,
            "folderId" to item.folderId,
            "embeddingStatus" to item.embeddingStatus,
            "aiSummary" to item.aiSummary,
            "lastIndexed" to item.lastIndexed,
            "createdAt" to item.createdAt,
            "updatedAt" to item.updatedAt,
            "isPrivate" to item.isPrivate
        )

        FirestoreSyncUtils.syncItemToFirestore(
            collection = col,
            documentId = item.id,
            data = data,
            onSuccess = {
                dao.insertKnowledge(item.copy(syncStatus = SyncStatus.SYNCED, lastSyncedAt = System.currentTimeMillis()))
            },
            onFailure = {
                dao.insertKnowledge(item.copy(syncStatus = SyncStatus.FAILED))
            }
        )
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T {
        if (value == null) return default
        return try { enumValueOf(value) } catch (_: Exception) { default }
    }
}
