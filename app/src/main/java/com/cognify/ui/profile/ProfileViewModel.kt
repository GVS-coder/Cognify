package com.cognify.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cognify.data.model.Resource
import com.cognify.data.repository.ProfileRepository
import com.cognify.data.repository.TaskRepository
import com.cognify.data.repository.KnowledgeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val taskRepository: TaskRepository,
    private val knowledgeRepository: KnowledgeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfileData()
    }

    private fun loadProfileData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            // Collect user stream
            launch {
                profileRepository.getCurrentUserFlow().collect { user ->
                    _uiState.update { it.copy(user = user, isLoading = false) }
                }
            }

            // Collect stats
            launch {
                combine(
                    taskRepository.getAllTasks(),
                    knowledgeRepository.getAllKnowledge()
                ) { tasks, knowledge ->
                    Pair(tasks.size, knowledge.size)
                }.collect { (taskCount, knowledgeCount) ->
                    _uiState.update { 
                        it.copy(
                            totalTasks = taskCount,
                            totalKnowledgeEntries = knowledgeCount
                            // studyStreakDays can be added later via AnalyticsRepository
                        ) 
                    }
                }
            }
        }
    }

    fun updateDisplayName(name: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            when (val result = profileRepository.updateDisplayName(name)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isSaving = false) }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isSaving = false, error = result.message) }
                }
                else -> {}
            }
        }
    }

    fun updateProfilePhoto(uri: Uri?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            when (val result = profileRepository.updateProfilePhoto(uri)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isSaving = false) }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isSaving = false, error = result.message) }
                }
                else -> {}
            }
        }
    }

    fun deleteAccount(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAccountDeleting = true) }
            when (val result = profileRepository.deleteAccount()) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isAccountDeleting = false) }
                    onSuccess()
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isAccountDeleting = false, error = result.message) }
                }
                else -> {}
            }
        }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
