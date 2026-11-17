package com.cognify.data.repository

import android.util.Log
import com.cognify.data.local.TaskDao
import com.cognify.data.local.TaskEntity
import com.cognify.data.model.SyncStatus
import com.cognify.data.model.TaskStatus
import com.cognify.util.FirestoreSyncUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Calendar
import javax.inject.Inject

class TaskRepositoryImpl @Inject constructor(
    private val taskDao: TaskDao,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : TaskRepository {

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    private fun getTasksCollection() = currentUserId?.let { uid ->
        firestore.collection("users").document(uid).collection("tasks")
    }

    override fun getAllTasks(): Flow<List<TaskEntity>> = taskDao.getAllTasks(currentUserId ?: "")

    override fun getPendingTasks(): Flow<List<TaskEntity>> = taskDao.getPendingTasks(currentUserId ?: "")

    override fun getCompletedTasks(): Flow<List<TaskEntity>> = taskDao.getCompletedTasks(currentUserId ?: "")

    override fun getTasksForToday(): Flow<List<TaskEntity>> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis

        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfDay = calendar.timeInMillis

        return taskDao.getTasksForToday(currentUserId ?: "", startOfDay, endOfDay)
    }

    override suspend fun getTaskById(id: String): TaskEntity? = taskDao.getTaskById(id, currentUserId ?: "")

    override suspend fun addTask(task: TaskEntity) {
        // Save locally first
        val pendingTask = task.copy(
            syncStatus = SyncStatus.PENDING,
            userId = currentUserId ?: ""
        )
        taskDao.insertTask(pendingTask)
        // Try sync
        syncTaskToFirestore(pendingTask)
    }

    override suspend fun updateTask(task: TaskEntity) {
        val pendingTask = task.copy(
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING,
            userId = currentUserId ?: ""
        )
        taskDao.updateTask(pendingTask)
        syncTaskToFirestore(pendingTask)
    }

    override suspend fun deleteTask(taskId: String) {
        taskDao.deleteTaskById(taskId, currentUserId ?: "")
        val collection = getTasksCollection() ?: return
        try {
            withContext(Dispatchers.IO) {
                collection.document(taskId).delete().await()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error deleting task from Firestore")
        }
    }

    override suspend fun completeTask(taskId: String) {
        val task = taskDao.getTaskById(taskId, currentUserId ?: "") ?: return
        val updatedTask = task.copy(
            status = TaskStatus.COMPLETED,
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING,
            userId = currentUserId ?: ""
        )
        taskDao.updateTask(updatedTask)
        syncTaskToFirestore(updatedTask)
    }

    override suspend fun syncPendingTasksToFirestore() {
        val collection = getTasksCollection() ?: return
        val pendingTasks = taskDao.getUnsyncedTasks(currentUserId ?: "")
        
        withContext(Dispatchers.IO) {
            for (task in pendingTasks) {
                syncTaskToFirestore(task)
            }
        }
    }

    override suspend fun syncFromFirestore() {
        val collection = getTasksCollection() ?: return
        
        try {
            val snapshot = withContext(Dispatchers.IO) {
                collection.get().await()
            }
            
            val remoteTasks = snapshot.documents.mapNotNull { doc ->
                doc.toObject(TaskEntity::class.java)?.copy(
                    syncStatus = SyncStatus.SYNCED,
                    lastSyncedAt = System.currentTimeMillis(),
                    userId = currentUserId ?: ""
                )
            }
            
            if (remoteTasks.isNotEmpty()) {
                taskDao.insertTasks(remoteTasks)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync from Firestore")
        }
    }

    private suspend fun syncTaskToFirestore(task: TaskEntity) {
        val collection = getTasksCollection() ?: return
        if (task.syncStatus == SyncStatus.SYNCED) return
        
        withContext(Dispatchers.IO) {
            FirestoreSyncUtils.syncItemToFirestore(
                collection = collection,
                documentId = task.id,
                data = task,
                onSuccess = {
                    taskDao.updateTask(task.copy(
                        syncStatus = SyncStatus.SYNCED,
                        lastSyncedAt = System.currentTimeMillis()
                    ))
                },
                onFailure = {
                    taskDao.updateTask(task.copy(syncStatus = SyncStatus.FAILED))
                }
            )
        }
    }
}
