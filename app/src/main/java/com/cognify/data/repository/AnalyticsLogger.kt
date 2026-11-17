package com.cognify.data.repository

import com.cognify.data.local.AnalyticsEventType

interface AnalyticsLogger {
    suspend fun logEvent(
        type: AnalyticsEventType,
        referenceId: String? = null,
        metadata: String? = null
    )
}
