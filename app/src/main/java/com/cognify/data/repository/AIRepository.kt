package com.cognify.data.repository

import com.cognify.data.model.AiCommand
import com.cognify.data.model.Resource
import com.cognify.ui.ai.AiMode
import kotlinx.coroutines.flow.Flow

interface AIRepository {
    suspend fun sendMessage(prompt: String, mode: AiMode, requestType: String = "AI_CHAT", history: String = ""): Flow<Resource<AiCommand>>
    suspend fun analyzeEmail(content: String): Flow<Resource<com.cognify.data.model.EmailAnalysisResult>>
    suspend fun getAiUsage(): Resource<com.cognify.data.model.AiUsage>
}
