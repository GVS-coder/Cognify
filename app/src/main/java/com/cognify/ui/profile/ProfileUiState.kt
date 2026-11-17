package com.cognify.ui.profile

import android.net.Uri
import com.cognify.data.model.User

data class ProfileUiState(
    val isLoading: Boolean = true,
    val user: User? = null,
    val totalTasks: Int = 0,
    val totalKnowledgeEntries: Int = 0,
    val studyStreakDays: Int = 0,
    val error: String? = null,
    val isSaving: Boolean = false,
    val isAccountDeleting: Boolean = false
)
