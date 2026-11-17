package com.cognify.data.repository

import android.content.Context
import com.cognify.data.local.ReminderDao
import com.cognify.data.local.ReminderEntity
import com.cognify.data.model.LinkedItemType
import com.cognify.data.model.ReminderPriority
import com.cognify.data.model.ReminderStatus
import com.cognify.data.model.ReminderType
import com.cognify.data.model.SyncStatus
import com.cognify.notification.ReminderScheduler
import com.cognify.util.FirestoreSyncUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: ReminderDao,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ReminderRepository {

    private val scope = CoroutineScope(Dispatchers.IO)

    private fun userCollection() = auth.currentUser?.uid?.let { uid ->
        firestore.collection("users").document(uid).collection("reminders")
    }

    override fun getAllReminders(): Flow<List<ReminderEntity>> = dao.getAllReminders(auth.currentUser?.uid ?: "")

    override fun getRemindersForDate(startOfDay: Long, endOfDay: Long): Flow<List<ReminderEntity>> =
        dao.getRemindersForDate(startOfDay, endOfDay, auth.currentUser?.uid ?: "")

    override fun getRemindersBetween(startTime: Long, endTime: Long): Flow<List<ReminderEntity>> =
        dao.getRemindersBetween(startTime, endTime, auth.currentUser?.uid ?: "")

    override fun getByType(type: String): Flow<List<ReminderEntity>> = dao.getByType(type, auth.currentUser?.uid ?: "")

    override suspend fun getById(id: String): ReminderEntity? = dao.getReminderById(id, auth.currentUser?.uid ?: "")

    override suspend fun saveReminder(reminder: ReminderEntity) {
        val localItem = reminder.copy(syncStatus = SyncStatus.PENDING, userId = auth.currentUser?.uid ?: "")
        dao.insertReminder(localItem)

        // Schedule notification if scheduled or pending
        if (localItem.status == ReminderStatus.SCHEDULED || localItem.status == ReminderStatus.PENDING) {
            ReminderScheduler.scheduleReminder(context, localItem)
        } else {
            ReminderScheduler.cancelReminder(context, localItem.id, localItem.notificationId)
        }

        scope.launch { syncItemToFirestore(localItem) }
    }

    override suspend fun updateReminderStatus(id: String, status: ReminderStatus) {
        val reminder = dao.getReminderById(id, auth.currentUser?.uid ?: "") ?: return
        val updated = reminder.copy(
            status = status,
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING,
            userId = auth.currentUser?.uid ?: ""
        )
        dao.insertReminder(updated)

        if (status == ReminderStatus.SCHEDULED || status == ReminderStatus.PENDING) {
            ReminderScheduler.scheduleReminder(context, updated)
        } else {
            ReminderScheduler.cancelReminder(context, updated.id, updated.notificationId)
        }

        scope.launch { syncItemToFirestore(updated) }
    }

    override suspend fun deleteReminder(id: String) {
        val reminder = dao.getReminderById(id, auth.currentUser?.uid ?: "")
        if (reminder != null) {
            ReminderScheduler.cancelReminder(context, reminder.id, reminder.notificationId)
        }
        dao.deleteReminderById(id, auth.currentUser?.uid ?: "")
        scope.launch {
            try {
                userCollection()?.document(id)?.delete()?.await()
            } catch (e: Exception) {
                Timber.e(e, "Error deleting reminder from Firestore")
            }
        }
    }

    override suspend fun syncPendingToFirestore() {
        val pending = dao.getUnsyncedReminders(auth.currentUser?.uid ?: "")
        pending.forEach { item -> syncItemToFirestore(item) }
    }

    override suspend fun syncFromFirestore() {
        val col = userCollection() ?: return
        try {
            val snapshot = col.get().await()
            val remoteItems = snapshot.documents.mapNotNull { doc ->
                try {
                    ReminderEntity(
                        id = doc.id,
                        title = doc.getString("title") ?: "",
                        description = doc.getString("description") ?: "",
                        dateTime = doc.getLong("dateTime") ?: 0L,
                        type = enumValueOrDefault(doc.getString("type"), ReminderType.GENERAL),
                        status = enumValueOrDefault(doc.getString("status"), ReminderStatus.PENDING),
                        priority = enumValueOrDefault(doc.getString("priority"), ReminderPriority.MEDIUM),
                        repeatInterval = enumValueOrDefault(doc.getString("repeatInterval"), com.cognify.data.model.RepeatInterval.NONE),
                        notificationId = doc.getLong("notificationId")?.toInt() ?: 0,
                        linkedItemId = doc.getString("linkedItemId"),
                        linkedItemType = enumValueOrDefault(doc.getString("linkedItemType"), LinkedItemType.NONE),
                        aiSuggested = doc.getBoolean("aiSuggested"),
                        lastReminderAnalysis = doc.getString("lastReminderAnalysis"),
                        createdAt = doc.getLong("createdAt") ?: 0L,
                        updatedAt = doc.getLong("updatedAt") ?: 0L,
                        syncStatus = SyncStatus.SYNCED,
                        lastSyncedAt = System.currentTimeMillis(),
                        isEnabled = doc.getBoolean("isEnabled") ?: true,
                        selectedWeekdays = doc.getString("selectedWeekdays") ?: "",
                        soundId = doc.getString("soundId") ?: "alarm_default",
                        isVibrationEnabled = doc.getBoolean("isVibrationEnabled") ?: true,
                        snoozeDurationMinutes = doc.getLong("snoozeDurationMinutes")?.toInt() ?: 15,
                        skipHolidays = doc.getBoolean("skipHolidays") ?: false,
                        userId = auth.currentUser?.uid ?: ""
                    )
                } catch (e: Exception) { 
                    Timber.e(e, "Failed to parse remote item \${doc.id}")
                    null 
                }
            }
            if (remoteItems.isNotEmpty()) {
                dao.insertAll(remoteItems)
                // Schedule downloaded remote reminders
                remoteItems.forEach { reminder ->
                    if (reminder.status == ReminderStatus.SCHEDULED || reminder.status == ReminderStatus.PENDING) {
                        ReminderScheduler.scheduleReminder(context, reminder)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync reminders from Firestore")
        }
    }

    private suspend fun syncItemToFirestore(item: ReminderEntity) {
        val col = userCollection() ?: return
        if (item.syncStatus == SyncStatus.SYNCED) return
        
        val data = mapOf(
            "title" to item.title,
            "description" to item.description,
            "dateTime" to item.dateTime,
            "type" to item.type.name,
            "status" to item.status.name,
            "priority" to item.priority.name,
            "repeatInterval" to item.repeatInterval.name,
            "notificationId" to item.notificationId,
            "linkedItemId" to item.linkedItemId,
            "linkedItemType" to item.linkedItemType.name,
            "aiSuggested" to item.aiSuggested,
            "lastReminderAnalysis" to item.lastReminderAnalysis,
            "createdAt" to item.createdAt,
            "updatedAt" to item.updatedAt,
            "isEnabled" to item.isEnabled,
            "selectedWeekdays" to item.selectedWeekdays,
            "soundId" to item.soundId,
            "isVibrationEnabled" to item.isVibrationEnabled,
            "snoozeDurationMinutes" to item.snoozeDurationMinutes,
            "skipHolidays" to item.skipHolidays
        )

        FirestoreSyncUtils.syncItemToFirestore(
            collection = col,
            documentId = item.id,
            data = data,
            onSuccess = {
                dao.insertReminder(item.copy(syncStatus = SyncStatus.SYNCED, lastSyncedAt = System.currentTimeMillis()))
            },
            onFailure = {
                dao.insertReminder(item.copy(syncStatus = SyncStatus.FAILED))
            }
        )
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T {
        if (value == null) return default
        return try { enumValueOf(value) } catch (_: Exception) { default }
    }
}
