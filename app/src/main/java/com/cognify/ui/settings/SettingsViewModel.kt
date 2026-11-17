package com.cognify.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cognify.data.local.AppDatabase
import com.cognify.data.model.Resource
import com.cognify.data.repository.AiMode
import com.cognify.data.repository.AppSettings
import com.cognify.data.repository.AuthRepository
import com.cognify.data.repository.GmailConnectionState
import com.cognify.data.repository.ProfileRepository
import com.cognify.data.repository.SettingsRepository
import com.cognify.data.repository.ThemeMode
import com.cognify.di.IoDispatcher
import com.cognify.notification.ReminderScheduler
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

sealed class DeleteAccountState {
    object Idle : DeleteAccountState()
    object Loading : DeleteAccountState()
    object Success : DeleteAccountState()
    data class RequiresReauth(val providerId: String, val email: String) : DeleteAccountState()
    data class Error(val message: String) : DeleteAccountState()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
    private val appDatabase: AppDatabase,
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    val appSettings: StateFlow<AppSettings> = settingsRepository.appSettingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )

    private val _deleteState = MutableStateFlow<DeleteAccountState>(DeleteAccountState.Idle)
    val deleteState: StateFlow<DeleteAccountState> = _deleteState.asStateFlow()

    fun updateThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.updateThemeMode(mode)
        }
    }

    fun updateDynamicColor(useDynamicColor: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateDynamicColor(useDynamicColor)
        }
    }

    fun updateNotifications(
        tasks: Boolean? = null,
        calendar: Boolean? = null,
        dailySummary: Boolean? = null,
        aiSuggestions: Boolean? = null,
        weeklyReport: Boolean? = null
    ) {
        viewModelScope.launch {
            settingsRepository.updateNotifications(tasks, calendar, dailySummary, aiSuggestions, weeklyReport)
        }
    }

    fun updateAiDefaultMode(mode: AiMode) {
        viewModelScope.launch {
            settingsRepository.updateAiDefaultMode(mode)
        }
    }

    fun updateProductivityDefaults(
        priority: Int? = null,
        reminderTime: String? = null,
        studyDuration: Int? = null,
        firstDayOfWeek: Int? = null
    ) {
        viewModelScope.launch {
            settingsRepository.updateProductivityDefaults(priority, reminderTime, studyDuration, firstDayOfWeek)
        }
    }

    fun updateGmailConnectionState(state: GmailConnectionState, account: String? = null) {
        viewModelScope.launch {
            settingsRepository.updateGmailConnectionState(state, account)
        }
    }

    fun updateLanguage(language: String) {
        viewModelScope.launch {
            settingsRepository.updateLanguage(language)
        }
    }

    fun clearLocalCache(onComplete: () -> Unit) {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                appDatabase.clearAllTables()
            }
            onComplete()
        }
    }

    fun deleteAccount(onComplete: ((Boolean, String?) -> Unit)? = null) {
        // Prevent concurrent or repeated delete requests
        if (_deleteState.value is DeleteAccountState.Loading) {
            Timber.w("deleteAccount called while deletion already in progress")
            return
        }

        val currentUserId = authRepository.getCurrentUser()?.uid ?: ""
        if (currentUserId.isBlank()) {
            val errMsg = "No active user session found. Please log in again."
            _deleteState.value = DeleteAccountState.Error(errMsg)
            onComplete?.invoke(false, errMsg)
            return
        }

        _deleteState.value = DeleteAccountState.Loading
        viewModelScope.launch {
            executeAccountDeletion(currentUserId, onComplete)
        }
    }

    fun reauthenticateWithPassword(password: String, onComplete: ((Boolean, String?) -> Unit)? = null) {
        if (_deleteState.value is DeleteAccountState.Loading) return

        val currentUserId = authRepository.getCurrentUser()?.uid ?: ""
        _deleteState.value = DeleteAccountState.Loading
        viewModelScope.launch {
            when (val reauthResult = authRepository.reauthenticate(password)) {
                is Resource.Success -> {
                    executeAccountDeletion(currentUserId, onComplete)
                }
                is Resource.Error -> {
                    _deleteState.value = DeleteAccountState.Error(reauthResult.message)
                    onComplete?.invoke(false, reauthResult.message)
                }
                else -> {}
            }
        }
    }

    fun reauthenticateWithGoogle(idToken: String, onComplete: ((Boolean, String?) -> Unit)? = null) {
        if (_deleteState.value is DeleteAccountState.Loading) return

        val currentUserId = authRepository.getCurrentUser()?.uid ?: ""
        _deleteState.value = DeleteAccountState.Loading
        viewModelScope.launch {
            when (val reauthResult = authRepository.reauthenticateWithGoogle(idToken)) {
                is Resource.Success -> {
                    executeAccountDeletion(currentUserId, onComplete)
                }
                is Resource.Error -> {
                    _deleteState.value = DeleteAccountState.Error(reauthResult.message)
                    onComplete?.invoke(false, reauthResult.message)
                }
                else -> {}
            }
        }
    }

    private suspend fun executeAccountDeletion(
        userId: String,
        onComplete: ((Boolean, String?) -> Unit)? = null
    ) {
        val result = profileRepository.deleteAccount()

        when (result) {
            is Resource.Success -> {
                // Cloud data and auth deleted successfully. Now wipe local data.
                cleanUpLocalUserData(userId)
                _deleteState.value = DeleteAccountState.Success
                onComplete?.invoke(true, null)
            }
            is Resource.Error -> {
                val msg = result.message
                if (msg.startsWith("RECENT_LOGIN_REQUIRED") ||
                    result.exception is com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
                    val parts = msg.split(":")
                    val providerId = if (parts.size >= 2) parts[1] else "password"
                    val email = if (parts.size >= 3) parts[2] else (authRepository.getCurrentUser()?.email ?: "")
                    _deleteState.value = DeleteAccountState.RequiresReauth(providerId, email)
                    onComplete?.invoke(false, "Security requirement: Recent login required.")
                } else {
                    _deleteState.value = DeleteAccountState.Error(msg)
                    onComplete?.invoke(false, msg)
                }
            }
            is Resource.Loading -> {
                // Handled
            }
        }
    }

    private suspend fun cleanUpLocalUserData(userId: String) {
        withContext(ioDispatcher) {
            try {
                // 1. Cancel active alarms for the deleted user's reminders
                val pendingReminders = appDatabase.reminderDao().getByStatusList("PENDING", userId) ?: emptyList()
                val activeReminders = appDatabase.reminderDao().getByStatusList("ACTIVE", userId) ?: emptyList()
                (pendingReminders + activeReminders).forEach { r ->
                    ReminderScheduler.cancelReminder(context, r.id, r.notificationId)
                }
            } catch (e: Throwable) {
                Timber.w(e, "Error cancelling reminder alarms during account deletion")
            }

            try {
                // 2. Clear all tables for the deleted user
                if (userId.isNotBlank()) {
                    appDatabase.clearUserData(userId)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error clearing Room user data for $userId")
            }

            try {
                // 3. Clear settings DataStore
                settingsRepository.clearPreferences()
            } catch (e: Exception) {
                Timber.e(e, "Error clearing settings preferences")
            }

            try {
                // 4. Remove cached profile photo file
                val photoFile = File(context.filesDir, "profile_photo.jpg")
                if (photoFile.exists()) {
                    photoFile.delete()
                }
            } catch (e: Exception) {
                Timber.w(e, "Error deleting profile photo")
            }

            try {
                // 5. Clear application SharedPreferences
                context.getSharedPreferences("timer_prefs", Context.MODE_PRIVATE).edit().clear().apply()
                context.getSharedPreferences("cognify_prefs", Context.MODE_PRIVATE).edit().clear().apply()
            } catch (e: Exception) {
                Timber.w(e, "Error clearing SharedPreferences")
            }

            try {
                // 6. Sign out Google Sign In Client
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                GoogleSignIn.getClient(context, gso).signOut()
            } catch (e: Throwable) {
                Timber.w(e, "Error signing out Google Sign-In client")
            }

            try {
                // 7. Sign out Firebase Auth
                authRepository.logout()
            } catch (e: Exception) {
                Timber.e(e, "Error logging out Auth")
            }
        }
    }

    fun resetDeleteState() {
        _deleteState.value = DeleteAccountState.Idle
    }
}
