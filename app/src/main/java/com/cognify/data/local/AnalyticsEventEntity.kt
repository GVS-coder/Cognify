package com.cognify.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "analytics_events")
data class AnalyticsEventEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val type: AnalyticsEventType,
    val eventTime: Long,
    val createdAt: Long, // Often same as eventTime, but added per requirement
    val referenceId: String? = null,
    val metadata: String? = null,
    val userId: String = ""
)
