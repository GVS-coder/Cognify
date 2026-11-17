package com.cognify.ui.smartinbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cognify.data.model.AiUsage
import com.cognify.data.model.EmailAnalysisResult
import com.cognify.data.model.Resource
import com.cognify.data.repository.AIRepository
import com.cognify.data.repository.AiUsageRepository
import com.cognify.data.repository.GmailRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EmailDetailsUiState(
    val isLoadingContent: Boolean = false,
    val contentError: String? = null,
    val content: String? = null,
    val aiUsage: AiUsage? = null,
    val isAnalyzing: Boolean = false,
    val analyzeError: String? = null,
    val analysisResult: EmailAnalysisResult? = null
)

@HiltViewModel
class GmailEmailDetailsViewModel @Inject constructor(
    private val gmailRepository: GmailRepository,
    private val aiRepository: AIRepository,
    private val aiUsageRepository: AiUsageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmailDetailsUiState())
    val uiState: StateFlow<EmailDetailsUiState> = _uiState

    fun loadContent(messageId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingContent = true, contentError = null) }
            val result = gmailRepository.getMessageContent(messageId)
            result.onSuccess { text ->
                _uiState.update { it.copy(isLoadingContent = false, content = text) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoadingContent = false, contentError = e.message ?: "Failed to load message.") }
            }
        }
    }

    fun fetchAiUsage() {
        viewModelScope.launch {
            val usageEntity = aiUsageRepository.getTodayUsage()
            val usage = AiUsage(
                aiChatUsed = usageEntity.aiAssistantRequests,
                aiChatLimit = AiUsageRepository.AI_ASSISTANT_DAILY_LIMIT,
                documentAnalysisUsed = usageEntity.documentAnalyses,
                documentAnalysisLimit = AiUsageRepository.DOCUMENT_ANALYSIS_DAILY_LIMIT,
                emailAnalysisUsed = usageEntity.emailAnalyses,
                emailAnalysisLimit = AiUsageRepository.EMAIL_ANALYSIS_DAILY_LIMIT
            )
            _uiState.update { it.copy(aiUsage = usage) }
        }
    }

    fun analyzeEmail(content: String) {
        viewModelScope.launch {
            if (!aiUsageRepository.canUseEmailAnalysis()) {
                _uiState.update { it.copy(analyzeError = "You have reached your daily limit for email analysis.") }
                return@launch
            }

            aiRepository.analyzeEmail(content).collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        _uiState.update { it.copy(isAnalyzing = true, analyzeError = null) }
                    }
                    is Resource.Success -> {
                        aiUsageRepository.incrementEmailAnalysisUsage()
                        _uiState.update { 
                            it.copy(
                                isAnalyzing = false, 
                                analysisResult = resource.data,
                                analyzeError = null
                            ) 
                        }
                        fetchAiUsage()
                    }
                    is Resource.Error -> {
                        _uiState.update { it.copy(isAnalyzing = false, analyzeError = resource.message) }
                    }
                }
            }
        }
    }
}
