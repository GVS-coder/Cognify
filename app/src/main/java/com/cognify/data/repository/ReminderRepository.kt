package com.cognify.data.repository

import com.cognify.data.local.ReminderEntity
import com.cognify.data.model.ReminderStatus
import kotlinx.coroutines.flow.Flow

interface ReminderRepository {
    fun getAllReminders(): Flow<List<ReminderEntity>>
    fun getRemindersForDate(startOfDay: Long, endOfDay: Long): Flow<List<ReminderEntity>>
    fun getRemindersBetween(startTime: Long, endTime: Long): Flow<List<ReminderEntity>>
    fun getByType(type: String): Flow<List<ReminderEntity>>
    suspend fun getById(id: String): ReminderEntity?
    suspend fun saveReminder(reminder: ReminderEntity)
    suspend fun updateReminderStatus(id: String, status: ReminderStatus)
    suspend fun deleteReminder(id: String)
    suspend fun syncPendingToFirestore()
    suspend fun syncFromFirestore()
}
