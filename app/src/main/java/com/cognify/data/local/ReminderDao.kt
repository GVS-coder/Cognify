package com.cognify.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders WHERE userId = :userId ORDER BY dateTime ASC")
    fun getAllReminders(userId: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE userId = :userId AND dateTime >= :startOfDay AND dateTime <= :endOfDay ORDER BY dateTime ASC")
    fun getRemindersForDate(startOfDay: Long, endOfDay: Long, userId: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE userId = :userId AND dateTime >= :startTime AND dateTime <= :endTime ORDER BY dateTime ASC")
    fun getRemindersBetween(startTime: Long, endTime: Long, userId: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE userId = :userId AND type = :type ORDER BY dateTime ASC")
    fun getByType(type: String, userId: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE userId = :userId AND (syncStatus = 'PENDING' OR syncStatus = 'FAILED')")
    suspend fun getUnsyncedReminders(userId: String): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE userId = :userId AND status = :status")
    suspend fun getByStatusList(status: String, userId: String): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id AND userId = :userId")
    suspend fun getReminderById(id: String, userId: String): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reminders: List<ReminderEntity>)

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Query("UPDATE reminders SET status = :status, updatedAt = :updatedAt, syncStatus = 'PENDING' WHERE id = :id AND userId = :userId")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long, userId: String)

    @Query("DELETE FROM reminders WHERE id = :id AND userId = :userId")
    suspend fun deleteReminderById(id: String, userId: String)

    @Query("DELETE FROM reminders WHERE userId = :userId")
    suspend fun deleteAllReminders(userId: String)
}
