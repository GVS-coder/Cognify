package com.cognify.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cognify.data.local.AnalyticsEventType
import com.cognify.data.model.ChatMessage
import com.cognify.data.model.Resource
import com.cognify.data.model.Role
import com.cognify.data.repository.AIRepository
import com.cognify.data.repository.AiUsageRepository
import com.cognify.data.repository.AnalyticsLogger
import com.cognify.data.local.ConversationDao
import com.cognify.data.local.ConversationEntity
import com.cognify.data.local.ConversationMessageEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class AIUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val currentMode: AiMode = AiMode.GENERAL,
    val suggestedPrompts: List<String> = emptyList(),
    val aiUsage: com.cognify.data.model.AiUsage? = null,
    val currentConversationId: String? = null,
    val pendingCommand: com.cognify.data.model.AiCommand? = null,
    val pendingConfirmationDetails: com.cognify.ai.ConfirmationDetails? = null
)

@HiltViewModel
class AIViewModel @Inject constructor(
    private val aiRepository: AIRepository,
    private val aiUsageRepository: AiUsageRepository,
    private val analyticsLogger: AnalyticsLogger,
    private val conversationDao: ConversationDao,
    private val aiCommandExecutor: com.cognify.ai.AiCommandExecutor
) : ViewModel() {

    private val _uiState = MutableStateFlow(AIUiState())
    val uiState: StateFlow<AIUiState> = _uiState.asStateFlow()
    
    private var messagesJob: Job? = null

    init {
        updateSuggestions(AiMode.GENERAL)
        fetchAiUsage()
    }

    fun fetchAiUsage() {
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            _uiState.update { it.copy(aiUsage = null) }
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
            _uiState.update { it.copy(aiUsage = usage) }
        }
    }

    fun setMode(mode: AiMode) {
        _uiState.update { it.copy(currentMode = mode) }
        updateSuggestions(mode)
    }

    private fun updateSuggestions(mode: AiMode) {
        val prompts = listOf(
            com.cognify.CognifyApplication.appContext.getString(com.cognify.R.string.ui_ai_sugg_tasks),
            com.cognify.CognifyApplication.appContext.getString(com.cognify.R.string.ui_ai_sugg_email),
            com.cognify.CognifyApplication.appContext.getString(com.cognify.R.string.ui_ai_sugg_concept)
        )
        _uiState.update { it.copy(suggestedPrompts = prompts) }
    }

    fun startNewChat() {
        messagesJob?.cancel()
        _uiState.update { it.copy(messages = emptyList(), currentConversationId = null, isLoading = false) }
    }
    
    fun loadConversation(conversationId: String) {
        if (_uiState.value.currentConversationId == conversationId) return
        startNewChat()
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
        _uiState.update { it.copy(currentConversationId = conversationId) }
        messagesJob = viewModelScope.launch {
            conversationDao.getMessagesForConversation(conversationId, userId).collect { entities ->
                val chatMessages = entities.map { entity ->
                    val attachment = if (entity.attachmentName != null && entity.attachmentType != null) {
                        com.cognify.data.model.DocumentAttachment(
                            fileName = entity.attachmentName,
                            fileType = entity.attachmentType,
                            fileSize = entity.attachmentSize ?: "",
                            processingStatus = "Analyzed"
                        )
                    } else null

                    ChatMessage(
                        content = entity.content,
                        role = if (entity.role == "USER") Role.USER else Role.AI,
                        documentAttachment = attachment
                    )
                }
                _uiState.update { it.copy(messages = chatMessages) }
            }
        }
    }

    fun sendMessage(content: String, requestType: String = "AI_CHAT") {
        if (content.isBlank()) return
        if (_uiState.value.isLoading) return

        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            val errorMessage = ChatMessage(content = "Please sign in to use Cognify AI.", role = Role.ERROR)
            _uiState.update { it.copy(messages = it.messages + errorMessage) }
            return
        }

        viewModelScope.launch {
            if (!aiUsageRepository.canUseAiAssistant()) {
                val errorMessage = ChatMessage(content = "You have reached your daily AI Assistant limit (${AiUsageRepository.AI_ASSISTANT_DAILY_LIMIT} requests). Please try again tomorrow.", role = Role.ERROR)
                _uiState.update { it.copy(messages = it.messages + errorMessage) }
                return@launch
            }

            analyticsLogger.logEvent(AnalyticsEventType.AI_CHAT_MESSAGE)
            
            var convId = _uiState.value.currentConversationId
            if (convId == null) {
                convId = UUID.randomUUID().toString()
                val title = if (content.length > 30) content.take(27) + "..." else content
                conversationDao.insertConversation(
                    ConversationEntity(
                        id = convId,
                        title = title,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        userId = currentUser.uid
                    )
                )
                loadConversation(convId)
            }
            
            val recentMessages = conversationDao.getRecentMessagesList(convId, 20, currentUser.uid)
            val history = recentMessages.joinToString("\n") { "${it.role}: ${it.content}" }
            
            val userMsgEntity = ConversationMessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = convId,
                role = "USER",
                content = content,
                timestamp = System.currentTimeMillis(),
                userId = currentUser.uid
            )
            conversationDao.insertMessage(userMsgEntity)
            _uiState.update { it.copy(isLoading = true) }
            
            aiRepository.sendMessage(content, _uiState.value.currentMode, requestType, history).collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        _uiState.update { it.copy(isLoading = true) }
                    }
                    is Resource.Success -> {
                        val aiCommand = resource.data
                        
                        var executionMessage: String? = null
                        var requireConfirmation = false
                        var confDetails: com.cognify.ai.ConfirmationDetails? = null
                        
                        if (aiCommand != null && aiCommand.type == com.cognify.data.model.AiCommandType.ACTION) {
                            val execResult = aiCommandExecutor.execute(aiCommand)
                            if (execResult.status == com.cognify.ai.AiCommandExecutionResult.Status.REQUIRES_CONFIRMATION) {
                                requireConfirmation = true
                                confDetails = execResult.confirmationDetails
                            } else {
                                executionMessage = execResult.message
                            }
                        }
                        
                        if (requireConfirmation) {
                            _uiState.update { it.copy(
                                isLoading = false,
                                pendingCommand = aiCommand,
                                pendingConfirmationDetails = confDetails
                            ) }
                            
                            val aiMsgEntity = ConversationMessageEntity(
                                id = UUID.randomUUID().toString(),
                                conversationId = convId,
                                role = "AI",
                                content = "I need your confirmation to proceed.",
                                timestamp = System.currentTimeMillis(),
                                userId = currentUser.uid
                            )
                            conversationDao.insertMessage(aiMsgEntity)
                            conversationDao.updateConversationTimestamp(convId, System.currentTimeMillis(), currentUser.uid)
                        } else {
                            val aiContent = executionMessage ?: aiCommand?.confirmationMessage ?: "Action completed."
                            
                            val aiMsgEntity = ConversationMessageEntity(
                                id = UUID.randomUUID().toString(),
                                conversationId = convId,
                                role = "AI",
                                content = aiContent,
                                timestamp = System.currentTimeMillis(),
                                userId = currentUser.uid
                            )
                            conversationDao.insertMessage(aiMsgEntity)
                            conversationDao.updateConversationTimestamp(convId, System.currentTimeMillis(), currentUser.uid)
                            _uiState.update { it.copy(isLoading = false) }
                        }
                        
                        aiUsageRepository.incrementAiAssistantUsage()
                        fetchAiUsage()
                    }
                    is Resource.Error -> {
                        val errorMessage = ChatMessage(content = resource.message ?: "An error occurred", role = Role.ERROR)
                        _uiState.update { it.copy(messages = it.messages + errorMessage, isLoading = false) }
                        fetchAiUsage()
                    }
                }
            }
        }
    }

    fun sendDocumentMessage(action: String, text: String, attachment: com.cognify.data.model.DocumentAttachment) {
        if (action.isBlank() || text.isBlank()) return
        if (_uiState.value.isLoading) return

        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            val errorMessage = ChatMessage(content = "Please sign in to use Cognify AI.", role = Role.ERROR)
            _uiState.update { it.copy(messages = it.messages + errorMessage) }
            return
        }

        viewModelScope.launch {
            if (!aiUsageRepository.canUseAiAssistant()) {
                val errorMessage = ChatMessage(content = "You have reached your daily AI Assistant limit (${AiUsageRepository.AI_ASSISTANT_DAILY_LIMIT} requests). Please try again tomorrow.", role = Role.ERROR)
                _uiState.update { it.copy(messages = it.messages + errorMessage) }
                return@launch
            }

            val displayContent = "$action document"
            val userMessage = ChatMessage(
                content = displayContent,
                role = Role.USER,
                documentAttachment = attachment
            )
            _uiState.update { it.copy(messages = it.messages + userMessage, isLoading = true) }

            analyticsLogger.logEvent(AnalyticsEventType.AI_CHAT_MESSAGE)
            
            var convId = _uiState.value.currentConversationId
            if (convId == null) {
                convId = UUID.randomUUID().toString()
                val title = if (attachment.fileName.length > 25) attachment.fileName.take(22) + "..." else attachment.fileName
                conversationDao.insertConversation(
                    ConversationEntity(
                        id = convId,
                        title = "Analyze: $title",
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        userId = currentUser.uid
                    )
                )
                _uiState.update { it.copy(currentConversationId = convId) }
            }
            
            val recentMessages = conversationDao.getRecentMessagesList(convId, 20, currentUser.uid)
            val history = recentMessages.joinToString("\n") { "${it.role}: ${it.content}" }
            
            val userMsgEntity = ConversationMessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = convId,
                role = "USER",
                content = displayContent,
                timestamp = System.currentTimeMillis(),
                attachmentName = attachment.fileName,
                attachmentType = attachment.fileType,
                attachmentSize = attachment.fileSize,
                userId = currentUser.uid
            )
            conversationDao.insertMessage(userMsgEntity)
            
            val apiPrompt = "Please $action the following document: ${attachment.fileName}\n\nContent:\n$text"
            
            aiRepository.sendMessage(apiPrompt, _uiState.value.currentMode, "DOCUMENT_ANALYSIS", history).collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        _uiState.update { it.copy(isLoading = true) }
                    }
                    is Resource.Success -> {
                        val aiCommand = resource.data
                        
                        var executionMessage: String? = null
                        var requireConfirmation = false
                        var confDetails: com.cognify.ai.ConfirmationDetails? = null
                        
                        if (aiCommand != null && aiCommand.type == com.cognify.data.model.AiCommandType.ACTION) {
                            val execResult = aiCommandExecutor.execute(aiCommand)
                            if (execResult.status == com.cognify.ai.AiCommandExecutionResult.Status.REQUIRES_CONFIRMATION) {
                                requireConfirmation = true
                                confDetails = execResult.confirmationDetails
                            } else {
                                executionMessage = execResult.message
                            }
                        }
                        
                        if (requireConfirmation) {
                            _uiState.update { it.copy(
                                isLoading = false,
                                pendingCommand = aiCommand,
                                pendingConfirmationDetails = confDetails
                            ) }
                            
                            val aiMsgEntity = ConversationMessageEntity(
                                id = UUID.randomUUID().toString(),
                                conversationId = convId,
                                role = "AI",
                                content = "I need your confirmation to proceed.",
                                timestamp = System.currentTimeMillis(),
                                userId = currentUser.uid
                            )
                            conversationDao.insertMessage(aiMsgEntity)
                            conversationDao.updateConversationTimestamp(convId, System.currentTimeMillis(), currentUser.uid)
                            loadConversation(convId)
                        } else {
                            val aiContent = executionMessage ?: aiCommand?.confirmationMessage ?: "Analysis completed."
                            
                            val aiMsgEntity = ConversationMessageEntity(
                                id = UUID.randomUUID().toString(),
                                conversationId = convId,
                                role = "AI",
                                content = aiContent,
                                timestamp = System.currentTimeMillis(),
                                userId = currentUser.uid
                            )
                            conversationDao.insertMessage(aiMsgEntity)
                            conversationDao.updateConversationTimestamp(convId, System.currentTimeMillis(), currentUser.uid)
                            
                            loadConversation(convId)
                            _uiState.update { it.copy(isLoading = false) }
                        }
                        
                        aiUsageRepository.incrementAiAssistantUsage()
                        fetchAiUsage()
                    }
                    is Resource.Error -> {
                        val errorMessage = ChatMessage(content = resource.message ?: "An error occurred", role = Role.ERROR)
                        _uiState.update { it.copy(messages = it.messages + errorMessage, isLoading = false) }
                        fetchAiUsage()
                    }
                }
            }
        }
    }

    fun confirmPendingCommand() {
        val pendingCmd = _uiState.value.pendingCommand ?: return
        val convId = _uiState.value.currentConversationId ?: return
        
        _uiState.update { it.copy(pendingCommand = null, pendingConfirmationDetails = null, isLoading = true) }
        
        viewModelScope.launch {
            val execResult = aiCommandExecutor.execute(pendingCmd, isConfirmed = true)
            
            val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
            val aiMsgEntity = ConversationMessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = convId,
                role = "AI",
                content = execResult.message,
                timestamp = System.currentTimeMillis(),
                userId = userId
            )
            conversationDao.insertMessage(aiMsgEntity)
            conversationDao.updateConversationTimestamp(convId, System.currentTimeMillis(), userId)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun cancelPendingCommand() {
        val convId = _uiState.value.currentConversationId ?: return
        _uiState.update { it.copy(pendingCommand = null, pendingConfirmationDetails = null) }
        
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
        viewModelScope.launch {
            val aiMsgEntity = ConversationMessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = convId,
                role = "AI",
                content = "Cancelled.",
                timestamp = System.currentTimeMillis(),
                userId = userId
            )
            conversationDao.insertMessage(aiMsgEntity)
            conversationDao.updateConversationTimestamp(convId, System.currentTimeMillis(), userId)
        }
    }
}
