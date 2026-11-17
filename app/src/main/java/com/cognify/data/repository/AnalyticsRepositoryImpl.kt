package com.cognify.data.repository

import com.cognify.data.local.AnalyticsEventDao
import com.cognify.data.local.AnalyticsEventEntity
import com.cognify.data.local.KnowledgeDao
import com.cognify.data.local.KnowledgeEntity
import com.cognify.data.local.ReminderDao
import com.cognify.data.local.ReminderEntity
import com.cognify.data.local.TaskDao
import com.cognify.data.local.TaskEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import com.google.firebase.auth.FirebaseAuth
import com.cognify.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsRepositoryImpl @Inject constructor(
    taskDao: TaskDao,
    knowledgeDao: KnowledgeDao,
    reminderDao: ReminderDao,
    analyticsEventDao: AnalyticsEventDao,
    @ApplicationScope externalScope: CoroutineScope,
    auth: FirebaseAuth
) : AnalyticsRepository {

    override val allTasks: Flow<List<TaskEntity>> = taskDao.getAllTasks(auth.currentUser?.uid ?: "")
        .shareIn(externalScope, SharingStarted.WhileSubscribed(5000), 1)

    override val allKnowledge: Flow<List<KnowledgeEntity>> = knowledgeDao.getAllKnowledge(auth.currentUser?.uid ?: "")
        .shareIn(externalScope, SharingStarted.WhileSubscribed(5000), 1)

    override val allReminders: Flow<List<ReminderEntity>> = reminderDao.getAllReminders(auth.currentUser?.uid ?: "")
        .shareIn(externalScope, SharingStarted.WhileSubscribed(5000), 1)

    override val allEvents: Flow<List<AnalyticsEventEntity>> = analyticsEventDao.getAllEvents(auth.currentUser?.uid ?: "")
        .shareIn(externalScope, SharingStarted.WhileSubscribed(5000), 1)
}
