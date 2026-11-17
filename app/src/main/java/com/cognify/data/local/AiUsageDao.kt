package com.cognify.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AiUsageDao {
    @Query("SELECT * FROM ai_daily_usage WHERE date = :date AND userId = :userId LIMIT 1")
    suspend fun getUsageForDate(date: String, userId: String): AiDailyUsageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsage(usage: AiDailyUsageEntity)

    @Query("UPDATE ai_daily_usage SET aiAssistantRequests = aiAssistantRequests + 1 WHERE date = :date AND userId = :userId")
    suspend fun incrementAiAssistantUsage(date: String, userId: String)

    @Query("UPDATE ai_daily_usage SET documentAnalyses = documentAnalyses + 1 WHERE date = :date AND userId = :userId")
    suspend fun incrementDocumentAnalysisUsage(date: String, userId: String)

    @Query("UPDATE ai_daily_usage SET emailAnalyses = emailAnalyses + 1 WHERE date = :date AND userId = :userId")
    suspend fun incrementEmailAnalysisUsage(date: String, userId: String)

    @Query("DELETE FROM ai_daily_usage WHERE userId = :userId")
    suspend fun deleteUsageForUser(userId: String)
}
