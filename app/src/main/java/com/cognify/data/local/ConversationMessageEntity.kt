package com.cognify.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversation_messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversationId"])]
)
data class ConversationMessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String, // "USER" or "AI"
    val content: String,
    val timestamp: Long,
    val attachmentName: String? = null,
    val attachmentType: String? = null,
    val attachmentSize: String? = null,
    val userId: String = ""
)
