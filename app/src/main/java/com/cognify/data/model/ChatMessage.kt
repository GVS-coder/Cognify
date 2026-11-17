package com.cognify.data.model

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val content: String,
    val role: Role,
    val timestamp: Long = System.currentTimeMillis(),
    val documentAttachment: DocumentAttachment? = null
)

data class DocumentAttachment(
    val fileName: String,
    val fileType: String,
    val fileSize: String,
    val processingStatus: String
)

enum class Role {
    USER,
    AI,
    ERROR
}
