package com.cognify.data.model

data class AiUsage(
    val aiChatUsed: Int = 0,
    val aiChatLimit: Int = 20,
    val documentAnalysisUsed: Int = 0,
    val documentAnalysisLimit: Int = 3,
    val emailAnalysisUsed: Int = 0,
    val emailAnalysisLimit: Int = 5
)
