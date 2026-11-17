package com.cognify.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cognify.data.model.SyncStatus
import com.cognify.data.model.TaskPriority
import com.cognify.data.model.TaskStatus

@Entity(
    tableName = "tasks",
    indices = [
        Index("status"),
        Index("dueDate"),
        Index("syncStatus")
    ]
)
data class TaskEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String,
    val priority: TaskPriority,
    val status: TaskStatus,
    val category: String,
    val dueDate: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: SyncStatus,
    val lastSyncedAt: Long?,
    val isPrivate: Boolean = false,
    val userId: String = ""
)
