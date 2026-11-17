package com.cognify.data.repository

import com.cognify.data.local.KnowledgeDao
import com.cognify.data.local.KnowledgeEntity
import com.cognify.data.local.ReminderDao
import com.cognify.data.local.ReminderEntity
import com.cognify.data.local.TaskDao
import com.cognify.data.local.TaskEntity
import com.cognify.data.model.Resource
import com.cognify.data.model.SyncStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import androidx.room.withTransaction
import com.cognify.data.local.AppDatabase
import com.cognify.data.local.CalculatorHistoryDao
import com.cognify.data.model.BackupUser
import com.cognify.data.model.CognifyBackup
import javax.inject.Inject
import javax.inject.Singleton
import com.google.gson.Gson

@Singleton
class BackupRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val taskDao: TaskDao,
    private val knowledgeDao: KnowledgeDao,
    private val reminderDao: ReminderDao,
    private val calculatorHistoryDao: CalculatorHistoryDao,
    private val settingsRepository: SettingsRepository,
    private val appDatabase: AppDatabase
) : BackupRepository {

    private val gson = Gson()

    override suspend fun performManualBackup(): Resource<BackupMetadata> {
        val user = auth.currentUser ?: return Resource.Error("Not authenticated")
        val userId = user.uid
        
        return try {
            val tasks = taskDao.getAllTasks(userId).first()
            val knowledge = knowledgeDao.getAllKnowledge(userId).first()
            val reminders = reminderDao.getAllReminders(userId).first()
            
            var totalItems = 0
            var totalSize = 0L
            
            val db = firestore
            val batch = db.batch()
            
            val userDoc = db.collection("users").document(userId)
            
            tasks.forEach { task ->
                val ref = userDoc.collection("tasks").document(task.id)
                batch.set(ref, task)
                totalItems++
                totalSize += gson.toJson(task).toByteArray().size
            }
            
            knowledge.forEach { k ->
                val ref = userDoc.collection("knowledgeVault").document(k.id)
                batch.set(ref, k)
                totalItems++
                totalSize += gson.toJson(k).toByteArray().size
            }
            
            reminders.forEach { r ->
                val ref = userDoc.collection("reminders").document(r.id)
                batch.set(ref, r)
                totalItems++
                totalSize += gson.toJson(r).toByteArray().size
            }
            
            // Execute batch
            batch.commit().await()
            
            val backupTime = System.currentTimeMillis()
            settingsRepository.updateLastSyncTime(backupTime)
            
            val metadata = BackupMetadata(
                lastBackupTime = backupTime,
                itemsSynced = totalItems,
                backupSizeBytes = totalSize,
                cloudSyncStatus = "Success"
            )
            
            Resource.Success(metadata)
        } catch (e: Exception) {
            Timber.e(e, "Error performing backup")
            Resource.Error(e.message ?: "Unknown error", e)
        }
    }

    override suspend fun performManualRestore(): Resource<BackupMetadata> {
        val user = auth.currentUser ?: return Resource.Error("Not authenticated")
        val userId = user.uid
        
        return try {
            val db = firestore
            val userDoc = db.collection("users").document(userId)
            
            // Download Tasks
            val remoteTasks = userDoc.collection("tasks").get().await().toObjects(TaskEntity::class.java)
            // Download Knowledge
            val remoteKnowledge = userDoc.collection("knowledgeVault").get().await().toObjects(KnowledgeEntity::class.java)
            // Download Reminders
            val remoteReminders = userDoc.collection("reminders").get().await().toObjects(ReminderEntity::class.java)
            
            var totalRestored = 0
            var totalSize = 0L
            
            remoteTasks.forEach { remote ->
                val local = taskDao.getTaskById(remote.id, userId)
                if (local == null || remote.updatedAt > local.updatedAt) {
                    taskDao.insertTask(remote.copy(syncStatus = SyncStatus.SYNCED, userId = userId))
                    totalRestored++
                    totalSize += gson.toJson(remote).toByteArray().size
                }
            }
            
            remoteKnowledge.forEach { remote ->
                val local = knowledgeDao.getKnowledgeById(remote.id, userId)
                if (local == null || remote.updatedAt > local.updatedAt) {
                    val isPrivate = remote.isPrivate || (local?.isPrivate == true)
                    knowledgeDao.insertKnowledge(remote.copy(
                        syncStatus = SyncStatus.SYNCED,
                        userId = userId,
                        isPrivate = isPrivate
                    ))
                    totalRestored++
                    totalSize += gson.toJson(remote).toByteArray().size
                }
            }
            
            remoteReminders.forEach { remote ->
                val local = reminderDao.getReminderById(remote.id, userId)
                if (local == null || remote.updatedAt > local.updatedAt) {
                    reminderDao.insertReminder(remote.copy(syncStatus = SyncStatus.SYNCED, userId = userId))
                    totalRestored++
                    totalSize += gson.toJson(remote).toByteArray().size
                }
            }
            
            val restoreTime = System.currentTimeMillis()
            settingsRepository.updateLastSyncTime(restoreTime)
            
            val metadata = BackupMetadata(
                lastBackupTime = restoreTime,
                itemsSynced = totalRestored,
                backupSizeBytes = totalSize,
                cloudSyncStatus = "Restored"
            )
            
            Resource.Success(metadata)
        } catch (e: Exception) {
            Timber.e(e, "Error performing restore")
            Resource.Error(e.message ?: "Unknown error", e)
        }
    }

    override suspend fun getLastBackupMetadata(): Resource<BackupMetadata> {
        // Mocking sizing metadata here for display, real implementations might store sizes in DataStore
        val lastSync = settingsRepository.appSettingsFlow.first().lastSyncTime
        return Resource.Success(
            BackupMetadata(
                lastBackupTime = lastSync,
                itemsSynced = 0, // Usually obtained from prior logs or DataStore
                backupSizeBytes = 0L,
                cloudSyncStatus = if (lastSync > 0) "Synced" else "Never Synced"
            )
        )
    }

    override suspend fun createLocalBackup(appVersion: String): Resource<CognifyBackup> {
        return try {
            val user = auth.currentUser
            val backupUser = user?.let { BackupUser(it.uid) }
            
            val backupUserId = backupUser?.uid ?: ""
            val tasks = taskDao.getAllTasks(backupUserId).first()
            val knowledge = knowledgeDao.getAllKnowledge(backupUserId).first()
            val reminders = reminderDao.getAllReminders(backupUserId).first()
            val calcHistory = calculatorHistoryDao.getAllHistory(backupUserId).first()
            val settings = settingsRepository.appSettingsFlow.first()

            val backup = CognifyBackup(
                appVersion = appVersion,
                user = backupUser,
                tasks = tasks,
                knowledge = knowledge,
                reminders = reminders,
                calculatorHistory = calcHistory,
                settings = settings
            )
            
            Resource.Success(backup)
        } catch (e: Exception) {
            Timber.e(e, "Error creating local backup")
            Resource.Error(e.message ?: "Unknown error creating backup", e)
        }
    }

    override suspend fun restoreLocalBackup(backup: CognifyBackup): Resource<Unit> {
        return try {
            val currentUser = auth.currentUser
            if (backup.user != null) {
                if (currentUser == null) {
                    return Resource.Error("You must be logged in to restore a backup.")
                }
                if (backup.user.uid != currentUser.uid) {
                    return Resource.Error("This backup belongs to a different Cognify account.")
                }
            }

            if (backup.version > 1) {
                return Resource.Error("This backup was created by a newer version of Cognify and cannot be restored.")
            }

            appDatabase.withTransaction {
                val currentUserId = currentUser?.uid ?: ""
                // Clear only affected tables selectively
                taskDao.deleteAllTasks(currentUserId)
                knowledgeDao.deleteAll(currentUserId)
                reminderDao.deleteAllReminders(currentUserId)
                calculatorHistoryDao.clearHistory(currentUserId)
                
                // Insert from backup
                if (backup.tasks.isNotEmpty()) taskDao.insertTasks(backup.tasks)
                if (backup.knowledge.isNotEmpty()) knowledgeDao.insertAll(backup.knowledge)
                if (backup.reminders.isNotEmpty()) reminderDao.insertAll(backup.reminders)
                if (backup.calculatorHistory.isNotEmpty()) calculatorHistoryDao.insertAll(backup.calculatorHistory)
            }

            // Restore settings outside the Room transaction
            backup.settings?.let { s ->
                settingsRepository.updateThemeMode(s.themeMode)
                settingsRepository.updateDynamicColor(s.useDynamicColor)
                settingsRepository.updateNotifications(
                    s.notifyTasks, s.notifyCalendar, s.notifyDailySummary, s.notifyAiSuggestions, s.notifyWeeklyReport
                )
                settingsRepository.updateAiDefaultMode(s.aiDefaultMode)
                settingsRepository.updateProductivityDefaults(
                    s.defaultTaskPriority, s.defaultReminderTime, s.defaultStudyDuration, s.firstDayOfWeek
                )
            }

            Resource.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error restoring local backup")
            Resource.Error("Failed to restore backup: ${e.message}", e)
        }
    }
}
