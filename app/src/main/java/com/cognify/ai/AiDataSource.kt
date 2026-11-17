package com.cognify.ai

interface AiDataSource {
    suspend fun generateResponse(prompt: String, requestType: String = "AI_CHAT"): String?
    suspend fun getAiUsage(): com.cognify.data.model.AiUsage?
}
