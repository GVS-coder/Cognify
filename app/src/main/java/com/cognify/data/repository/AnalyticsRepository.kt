package com.cognify.data.repository

import com.cognify.data.local.AnalyticsEventEntity
import com.cognify.data.local.KnowledgeEntity
import com.cognify.data.local.ReminderEntity
import com.cognify.data.local.TaskEntity
import kotlinx.coroutines.flow.Flow

interface AnalyticsRepository {
    val allTasks: Flow<List<TaskEntity>>
    val allKnowledge: Flow<List<KnowledgeEntity>>
    val allReminders: Flow<List<ReminderEntity>>
    val allEvents: Flow<List<AnalyticsEventEntity>>
}
