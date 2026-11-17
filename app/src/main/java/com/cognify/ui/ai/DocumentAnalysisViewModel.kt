package com.cognify.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cognify.ai.DocumentAnalysisService
import com.cognify.data.repository.AiUsageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.net.Uri
import javax.inject.Inject
import java.util.UUID

@HiltViewModel
class DocumentAnalysisViewModel @Inject constructor(
    private val documentAnalysisService: DocumentAnalysisService,
    private val aiRepository: com.cognify.data.repository.AIRepository,
    private val aiUsageRepository: AiUsageRepository,
    private val conversationDao: com.cognify.data.local.ConversationDao
) : ViewModel() {

    private val _uiState = MutableStateFlow<DocumentState>(DocumentState.Idle)
    val uiState: StateFlow<DocumentState> = _uiState

    private val _aiUsage = MutableStateFlow<com.cognify.data.model.AiUsage?>(null)
    val aiUsage: StateFlow<com.cognify.data.model.AiUsage?> = _aiUsage

    init {
        fetchAiUsage()
    }

    fun fetchAiUsage() {
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            _aiUsage.value = null
            return
        }
        viewModelScope.launch {
            val entity = aiUsageRepository.getTodayUsage()
            val usage = com.cognify.data.model.AiUsage(
                aiChatUsed = entity.aiAssistantRequests,
                aiChatLimit = AiUsageRepository.AI_ASSISTANT_DAILY_LIMIT,
                documentAnalysisUsed = entity.documentAnalyses,
                documentAnalysisLimit = AiUsageRepository.DOCUMENT_ANALYSIS_DAILY_LIMIT
            )
            timber.log.Timber.d("Daily usage fetched: AiChat=${usage.aiChatUsed}/${usage.aiChatLimit}, DocAnalysis=${usage.documentAnalysisUsed}/${usage.documentAnalysisLimit}")
            _aiUsage.value = usage
        }
    }

    fun parseDocument(uri: Uri) {
        try {
            val (name, size, type) = documentAnalysisService.getFileInfo(uri)
            val maxSizeBytes = 5L * 1024 * 1024
            if (size > maxSizeBytes) {
                _uiState.value = DocumentState.Error(
                    com.cognify.ai.DocumentAnalysisErrorDetails(
                        type = com.cognify.ai.DocumentAnalysisErrorType.FILE_TOO_LARGE,
                        currentValue = "${size / (1024 * 1024)} MB",
                        maximumValue = "5 MB",
                        detailMessage = "This document is too large."
                    )
                )
                return
            }
            if (type != "application/pdf" && !name.lowercase().endsWith(".pdf")) {
                _uiState.value = DocumentState.Error(
                    com.cognify.ai.DocumentAnalysisErrorDetails(
                        type = com.cognify.ai.DocumentAnalysisErrorType.UNSUPPORTED_FILE,
                        detailMessage = "Only PDF files are supported."
                    )
                )
                return
            }
            _uiState.value = DocumentState.Success(DocumentInfo(uri, name, type, size))
        } catch (e: Exception) {
            _uiState.value = DocumentState.Error(
                com.cognify.ai.DocumentAnalysisErrorDetails(
                    type = com.cognify.ai.DocumentAnalysisErrorType.UNKNOWN,
                    detailMessage = e.message ?: "Unknown error occurred."
                )
            )
        }
    }

    fun analyzeDocument(action: String) {
        val currentState = _uiState.value
        val documentInfo = when (currentState) {
            is DocumentState.Success -> currentState.document
            is DocumentState.Error -> currentState.document ?: return
            else -> return
        }

        _uiState.value = DocumentState.Analyzing(documentInfo)

        viewModelScope.launch {
            val usageEntity = aiUsageRepository.getTodayUsage()
            if (usageEntity.documentAnalyses >= AiUsageRepository.DOCUMENT_ANALYSIS_DAILY_LIMIT) {
                timber.log.Timber.w("Local quota limit reached: ${usageEntity.documentAnalyses}/${AiUsageRepository.DOCUMENT_ANALYSIS_DAILY_LIMIT}")
                _uiState.value = DocumentState.Error(
                    com.cognify.ai.DocumentAnalysisErrorDetails(
                        type = com.cognify.ai.DocumentAnalysisErrorType.RATE_LIMIT,
                        currentValue = usageEntity.documentAnalyses.toString(),
                        maximumValue = AiUsageRepository.DOCUMENT_ANALYSIS_DAILY_LIMIT.toString(),
                        detailMessage = "You have reached your daily Document Analysis limit. Please try again tomorrow."
                    ),
                    documentInfo
                )
                return@launch
            }

            try {
                val response = documentAnalysisService.analyzeDocument(documentInfo.uri, action)
                
                // Save to conversation
                saveToConversation(documentInfo, action, response)
                
                _uiState.value = DocumentState.AnalysisSuccess(documentInfo, response)
                
                aiUsageRepository.incrementDocumentAnalysisUsage()
                fetchAiUsage()
            } catch (e: com.cognify.ai.DocumentAnalysisException) {
                if (e.errorDetails.type == com.cognify.ai.DocumentAnalysisErrorType.RATE_LIMIT) {
                    timber.log.Timber.w("Server returned RATE_LIMIT: ${e.errorDetails.detailMessage}")
                } else {
                    timber.log.Timber.e("Analysis failed: ${e.errorDetails.type} - ${e.errorDetails.detailMessage}")
                }
                _uiState.value = DocumentState.Error(e.errorDetails, documentInfo)
            } catch (e: Exception) {
                _uiState.value = DocumentState.Error(
                    com.cognify.ai.DocumentAnalysisErrorDetails(
                        type = com.cognify.ai.DocumentAnalysisErrorType.SERVER_ERROR,
                        detailMessage = e.message ?: "The document analysis service is temporarily unavailable."
                    ), 
                    documentInfo
                )
            }
        }
    }
    
    private suspend fun saveToConversation(document: DocumentInfo, action: String, response: String) {
        val convId = UUID.randomUUID().toString()
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val title = if (document.name.length > 25) document.name.take(22) + "..." else document.name
        conversationDao.insertConversation(
            com.cognify.data.local.ConversationEntity(
                id = convId,
                title = "Analyze: $title",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                userId = userId
            )
        )
        
        val userMsg = com.cognify.data.local.ConversationMessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = convId,
            role = "USER",
            content = action,
            timestamp = System.currentTimeMillis(),
            attachmentName = document.name,
            attachmentType = document.type,
            attachmentSize = document.formatSize(),
            userId = userId
        )
        conversationDao.insertMessage(userMsg)
        
        val aiMsg = com.cognify.data.local.ConversationMessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = convId,
            role = "AI",
            content = response,
            timestamp = System.currentTimeMillis(),
            userId = userId
        )
        conversationDao.insertMessage(aiMsg)
        conversationDao.updateConversationTimestamp(convId, System.currentTimeMillis(), userId)
    }

    fun discardDocument() {
        _uiState.value = DocumentState.Idle
    }
}

data class DocumentInfo(val uri: Uri, val name: String, val type: String, val sizeBytes: Long) {
    fun formatSize(): String {
        if (sizeBytes < 1024) return "$sizeBytes B"
        val z = (63 - java.lang.Long.numberOfLeadingZeros(sizeBytes)) / 10
        return String.format("%.1f %sB", sizeBytes.toDouble() / (1L shl (z * 10)), " KMGTPE"[z])
    }
}

sealed class DocumentState {
    object Idle : DocumentState()
    object Loading : DocumentState()
    data class Success(val document: DocumentInfo) : DocumentState()
    data class Analyzing(val document: DocumentInfo) : DocumentState()
    data class AnalysisSuccess(val document: DocumentInfo, val response: String) : DocumentState()
    data class Error(val errorDetails: com.cognify.ai.DocumentAnalysisErrorDetails, val document: DocumentInfo? = null) : DocumentState()
}
