package com.cognify.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cognify.data.model.LinkedItemType
import com.cognify.data.model.ReminderPriority
import com.cognify.data.model.ReminderStatus
import com.cognify.data.model.ReminderType
import com.cognify.data.model.RepeatInterval
import com.cognify.data.model.SyncStatus

@Entity(
    tableName = "reminders",
    indices = [
        Index("status"),
        Index("dateTime"),
        Index("syncStatus")
    ]
)
data class ReminderEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String,
    val dateTime: Long,
    val type: ReminderType,
    val status: ReminderStatus,
    val priority: ReminderPriority,
    val repeatInterval: RepeatInterval,
    val notificationId: Int,
    val linkedItemId: String?,
    val linkedItemType: LinkedItemType,
    val aiSuggested: Boolean?,
    val lastReminderAnalysis: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: SyncStatus,
    val lastSyncedAt: Long?,
    
    // New Alarm Fields
    val isEnabled: Boolean = true,
    val selectedWeekdays: String = "",
    val soundId: String = "alarm_default",
    val customSoundUri: String? = null,
    val isVibrationEnabled: Boolean = true,
    val snoozeDurationMinutes: Int = 15,
    val skipHolidays: Boolean = false,
    val userId: String = ""
)
