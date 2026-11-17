package com.cognify.data.local

import androidx.room.Entity

@Entity(tableName = "ai_daily_usage", primaryKeys = ["date", "userId"])
data class AiDailyUsageEntity(
    val date: String, // Format: YYYY-MM-DD
    val aiAssistantRequests: Int = 0,
    val documentAnalyses: Int = 0,
    val emailAnalyses: Int = 0,
    val userId: String
)
