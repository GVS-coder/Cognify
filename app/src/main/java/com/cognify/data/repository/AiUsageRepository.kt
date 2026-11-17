package com.cognify.data.repository

import com.cognify.data.local.AiDailyUsageEntity
import com.cognify.data.local.AiUsageDao
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AiUsageRepository @Inject constructor(
    private val aiUsageDao: AiUsageDao,
    private val authRepository: AuthRepository
) {
    companion object {
        const val AI_ASSISTANT_DAILY_LIMIT = 10
        const val DOCUMENT_ANALYSIS_DAILY_LIMIT = 3
        const val EMAIL_ANALYSIS_DAILY_LIMIT = 5
    }

    private val _currentUsage = MutableStateFlow<AiDailyUsageEntity?>(null)
    val currentUsage: StateFlow<AiDailyUsageEntity?> = _currentUsage.asStateFlow()

    private fun getTodayDateString(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return formatter.format(Date())
    }

    suspend fun getTodayUsage(): AiDailyUsageEntity {
        val today = getTodayDateString()
        val userId = authRepository.getCurrentUser()?.uid ?: ""
        var usage = aiUsageDao.getUsageForDate(today, userId)
        
        if (usage == null) {
            usage = AiDailyUsageEntity(date = today, userId = userId)
            aiUsageDao.insertUsage(usage)
        }
        
        _currentUsage.value = usage
        return usage
    }

    suspend fun canUseAiAssistant(): Boolean {
        val usage = getTodayUsage()
        return usage.aiAssistantRequests < AI_ASSISTANT_DAILY_LIMIT
    }

    suspend fun canUseDocumentAnalysis(): Boolean {
        val usage = getTodayUsage()
        return usage.documentAnalyses < DOCUMENT_ANALYSIS_DAILY_LIMIT
    }

    suspend fun canUseEmailAnalysis(): Boolean {
        val usage = getTodayUsage()
        return usage.emailAnalyses < EMAIL_ANALYSIS_DAILY_LIMIT
    }

    suspend fun incrementAiAssistantUsage() {
        val today = getTodayDateString()
        val userId = authRepository.getCurrentUser()?.uid ?: ""
        aiUsageDao.incrementAiAssistantUsage(today, userId)
        _currentUsage.value = aiUsageDao.getUsageForDate(today, userId)
    }

    suspend fun incrementDocumentAnalysisUsage() {
        val today = getTodayDateString()
        val userId = authRepository.getCurrentUser()?.uid ?: ""
        aiUsageDao.incrementDocumentAnalysisUsage(today, userId)
        _currentUsage.value = aiUsageDao.getUsageForDate(today, userId)
    }

    suspend fun incrementEmailAnalysisUsage() {
        val today = getTodayDateString()
        val userId = authRepository.getCurrentUser()?.uid ?: ""
        aiUsageDao.incrementEmailAnalysisUsage(today, userId)
        _currentUsage.value = aiUsageDao.getUsageForDate(today, userId)
    }
}
