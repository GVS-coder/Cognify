package com.cognify.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cognify.data.local.AiDailyUsageEntity
import com.cognify.data.repository.AiUsageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AISettingsViewModel @Inject constructor(
    private val aiUsageRepository: AiUsageRepository
) : ViewModel() {

    private val _usageState = MutableStateFlow<AiDailyUsageEntity?>(null)
    val usageState: StateFlow<AiDailyUsageEntity?> = _usageState.asStateFlow()

    init {
        loadUsage()
    }

    private fun loadUsage() {
        viewModelScope.launch {
            _usageState.value = aiUsageRepository.getTodayUsage()
        }
    }
}
