package com.cognify.data.repository

import com.cognify.data.local.TaskEntity
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun getAllTasks(): Flow<List<TaskEntity>>
    fun getPendingTasks(): Flow<List<TaskEntity>>
    fun getCompletedTasks(): Flow<List<TaskEntity>>
    fun getTasksForToday(): Flow<List<TaskEntity>>
    
    suspend fun getTaskById(id: String): TaskEntity?
    suspend fun addTask(task: TaskEntity)
    suspend fun updateTask(task: TaskEntity)
    suspend fun deleteTask(taskId: String)
    suspend fun completeTask(taskId: String)
    
    suspend fun syncPendingTasksToFirestore()
    suspend fun syncFromFirestore()
}
