package com.cognify.data.repository

import kotlinx.coroutines.flow.Flow

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true,
    
    val notifyTasks: Boolean = true,
    val notifyCalendar: Boolean = true,
    val notifyDailySummary: Boolean = true,
    val notifyAiSuggestions: Boolean = true,
    val notifyWeeklyReport: Boolean = true,
    
    val aiDefaultMode: AiMode = AiMode.GENERAL,
    
    val defaultTaskPriority: Int = 1, // 0=Low, 1=Medium, 2=High
    val defaultReminderTime: String = "09:00",
    val defaultStudyDuration: Int = 25, // minutes
    val firstDayOfWeek: Int = 1, // 1=Sunday, 2=Monday
    
    val lastSyncTime: Long = 0L,
    
    val gmailConnectionState: GmailConnectionState = GmailConnectionState.DISCONNECTED,
    val connectedGmailAccount: String? = null,
    val language: String = "" // Empty string means system default
)

enum class ThemeMode { LIGHT, DARK, SYSTEM }
enum class AiMode { GENERAL, TASK, KNOWLEDGE, STUDY }
enum class GmailConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

interface SettingsRepository {
    val appSettingsFlow: Flow<AppSettings>
    
    suspend fun updateThemeMode(mode: ThemeMode)
    suspend fun updateDynamicColor(useDynamic: Boolean)
    
    suspend fun updateNotifications(
        tasks: Boolean? = null,
        calendar: Boolean? = null,
        dailySummary: Boolean? = null,
        aiSuggestions: Boolean? = null,
        weeklyReport: Boolean? = null
    )
    
    suspend fun updateAiDefaultMode(mode: AiMode)
    
    suspend fun updateProductivityDefaults(
        priority: Int? = null,
        reminderTime: String? = null,
        studyDuration: Int? = null,
        firstDayOfWeek: Int? = null
    )
    
    suspend fun updateLastSyncTime(timestamp: Long)
    
    suspend fun updateGmailConnectionState(state: GmailConnectionState, account: String?)
    
    suspend fun updateLanguage(language: String)
    
    suspend fun clearPreferences()
    
    fun isConsentAccepted(uid: String): Flow<Boolean>
    suspend fun setConsentAccepted(uid: String)
}
