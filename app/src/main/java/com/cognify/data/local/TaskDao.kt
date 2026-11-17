package com.cognify.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks WHERE userId = :userId ORDER BY createdAt DESC")
    fun getAllTasks(userId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE userId = :userId AND (status = 'PENDING' OR status = 'IN_PROGRESS') ORDER BY priority DESC, dueDate ASC")
    fun getPendingTasks(userId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE userId = :userId AND status = 'COMPLETED' ORDER BY updatedAt DESC")
    fun getCompletedTasks(userId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE userId = :userId AND dueDate >= :startOfDay AND dueDate <= :endOfDay ORDER BY dueDate ASC")
    fun getTasksForToday(userId: String, startOfDay: Long, endOfDay: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE userId = :userId AND syncStatus = 'PENDING'")
    suspend fun getUnsyncedTasks(userId: String): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id AND userId = :userId")
    suspend fun getTaskById(id: String, userId: String): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id AND userId = :userId")
    suspend fun deleteTaskById(id: String, userId: String)
    
    @Query("DELETE FROM tasks WHERE userId = :userId")
    suspend fun deleteAllTasks(userId: String)
}
