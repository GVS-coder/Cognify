package com.cognify.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    private val dataStore = context.dataStore

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        
        val NOTIFY_TASKS = booleanPreferencesKey("notify_tasks")
        val NOTIFY_CALENDAR = booleanPreferencesKey("notify_calendar")
        val NOTIFY_DAILY_SUMMARY = booleanPreferencesKey("notify_daily_summary")
        val NOTIFY_AI_SUGGESTIONS = booleanPreferencesKey("notify_ai_suggestions")
        val NOTIFY_WEEKLY_REPORT = booleanPreferencesKey("notify_weekly_report")
        
        val AI_DEFAULT_MODE = stringPreferencesKey("ai_default_mode")
        
        val DEFAULT_TASK_PRIORITY = intPreferencesKey("default_task_priority")
        val DEFAULT_REMINDER_TIME = stringPreferencesKey("default_reminder_time")
        val DEFAULT_STUDY_DURATION = intPreferencesKey("default_study_duration")
        val FIRST_DAY_OF_WEEK = intPreferencesKey("first_day_of_week")
        
        val LAST_SYNC_TIME = longPreferencesKey("last_sync_time")
        
        val GMAIL_CONNECTION_STATE = stringPreferencesKey("gmail_connection_state")
        val CONNECTED_GMAIL_ACCOUNT = stringPreferencesKey("connected_gmail_account")
        val LANGUAGE = stringPreferencesKey("language")
    }

    override val appSettingsFlow: Flow<AppSettings> = dataStore.data
        .map { preferences ->
            AppSettings(
                themeMode = ThemeMode.valueOf(preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name),
                useDynamicColor = preferences[PreferencesKeys.USE_DYNAMIC_COLOR] ?: true,
                
                notifyTasks = preferences[PreferencesKeys.NOTIFY_TASKS] ?: true,
                notifyCalendar = preferences[PreferencesKeys.NOTIFY_CALENDAR] ?: true,
                notifyDailySummary = preferences[PreferencesKeys.NOTIFY_DAILY_SUMMARY] ?: true,
                notifyAiSuggestions = preferences[PreferencesKeys.NOTIFY_AI_SUGGESTIONS] ?: true,
                notifyWeeklyReport = preferences[PreferencesKeys.NOTIFY_WEEKLY_REPORT] ?: true,
                
                aiDefaultMode = AiMode.valueOf(preferences[PreferencesKeys.AI_DEFAULT_MODE] ?: AiMode.GENERAL.name),
                
                defaultTaskPriority = preferences[PreferencesKeys.DEFAULT_TASK_PRIORITY] ?: 1,
                defaultReminderTime = preferences[PreferencesKeys.DEFAULT_REMINDER_TIME] ?: "09:00",
                defaultStudyDuration = preferences[PreferencesKeys.DEFAULT_STUDY_DURATION] ?: 25,
                firstDayOfWeek = preferences[PreferencesKeys.FIRST_DAY_OF_WEEK] ?: 1,
                
                lastSyncTime = preferences[PreferencesKeys.LAST_SYNC_TIME] ?: 0L,
                
                gmailConnectionState = GmailConnectionState.valueOf(preferences[PreferencesKeys.GMAIL_CONNECTION_STATE] ?: GmailConnectionState.DISCONNECTED.name),
                connectedGmailAccount = preferences[PreferencesKeys.CONNECTED_GMAIL_ACCOUNT],
                language = preferences[PreferencesKeys.LANGUAGE] ?: ""
            )
        }

    override suspend fun updateThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name
        }
    }

    override suspend fun updateDynamicColor(useDynamic: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_DYNAMIC_COLOR] = useDynamic
        }
    }

    override suspend fun updateNotifications(
        tasks: Boolean?,
        calendar: Boolean?,
        dailySummary: Boolean?,
        aiSuggestions: Boolean?,
        weeklyReport: Boolean?
    ) {
        dataStore.edit { preferences ->
            tasks?.let { preferences[PreferencesKeys.NOTIFY_TASKS] = it }
            calendar?.let { preferences[PreferencesKeys.NOTIFY_CALENDAR] = it }
            dailySummary?.let { preferences[PreferencesKeys.NOTIFY_DAILY_SUMMARY] = it }
            aiSuggestions?.let { preferences[PreferencesKeys.NOTIFY_AI_SUGGESTIONS] = it }
            weeklyReport?.let { preferences[PreferencesKeys.NOTIFY_WEEKLY_REPORT] = it }
        }
    }

    override suspend fun updateAiDefaultMode(mode: AiMode) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.AI_DEFAULT_MODE] = mode.name
        }
    }

    override suspend fun updateProductivityDefaults(
        priority: Int?,
        reminderTime: String?,
        studyDuration: Int?,
        firstDayOfWeek: Int?
    ) {
        dataStore.edit { preferences ->
            priority?.let { preferences[PreferencesKeys.DEFAULT_TASK_PRIORITY] = it }
            reminderTime?.let { preferences[PreferencesKeys.DEFAULT_REMINDER_TIME] = it }
            studyDuration?.let { preferences[PreferencesKeys.DEFAULT_STUDY_DURATION] = it }
            firstDayOfWeek?.let { preferences[PreferencesKeys.FIRST_DAY_OF_WEEK] = it }
        }
    }

    override suspend fun updateLastSyncTime(timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_SYNC_TIME] = timestamp
        }
    }

    override suspend fun updateGmailConnectionState(state: GmailConnectionState, account: String?) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.GMAIL_CONNECTION_STATE] = state.name
            if (account != null) {
                preferences[PreferencesKeys.CONNECTED_GMAIL_ACCOUNT] = account
            } else {
                preferences.remove(PreferencesKeys.CONNECTED_GMAIL_ACCOUNT)
            }
        }
    }

    override suspend fun updateLanguage(language: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LANGUAGE] = language
        }
    }

    override suspend fun clearPreferences() {
        dataStore.edit { it.clear() }
    }
    
    override fun isConsentAccepted(uid: String): Flow<Boolean> {
        val key = booleanPreferencesKey("consent_accepted_$uid")
        return dataStore.data.map { preferences ->
            preferences[key] ?: false
        }
    }

    override suspend fun setConsentAccepted(uid: String) {
        val key = booleanPreferencesKey("consent_accepted_$uid")
        dataStore.edit { preferences ->
            preferences[key] = true
        }
    }
}
