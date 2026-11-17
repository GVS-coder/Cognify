package com.cognify.data.model

import com.cognify.data.local.CalculatorHistoryEntity
import com.cognify.data.local.KnowledgeEntity
import com.cognify.data.local.ReminderEntity
import com.cognify.data.local.TaskEntity
import com.cognify.data.repository.AppSettings

data class BackupUser(
    val uid: String
)

data class CognifyBackup(
    val format: String = "cognify_backup",
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val appVersion: String,
    val user: BackupUser?,
    val tasks: List<TaskEntity>,
    val knowledge: List<KnowledgeEntity>,
    val reminders: List<ReminderEntity>,
    val calculatorHistory: List<CalculatorHistoryEntity>,
    val settings: AppSettings?
)
