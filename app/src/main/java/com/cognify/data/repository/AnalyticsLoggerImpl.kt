package com.cognify.data.repository

import com.cognify.data.local.AnalyticsEventDao
import com.cognify.data.local.AnalyticsEventEntity
import com.cognify.data.local.AnalyticsEventType
import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsLoggerImpl @Inject constructor(
    private val analyticsEventDao: AnalyticsEventDao,
    private val auth: FirebaseAuth
) : AnalyticsLogger {

    override suspend fun logEvent(
        type: AnalyticsEventType,
        referenceId: String?,
        metadata: String?
    ) {
        val now = System.currentTimeMillis()
        val event = AnalyticsEventEntity(
            type = type,
            eventTime = now,
            createdAt = now,
            referenceId = referenceId,
            metadata = metadata,
            userId = auth.currentUser?.uid ?: ""
        )
        analyticsEventDao.insertEvent(event)
    }
}
