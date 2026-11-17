package com.cognify.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cognify.data.model.Difficulty
import com.cognify.data.model.KnowledgeType
import com.cognify.data.model.ProgrammingLanguage
import com.cognify.data.model.SyncStatus

@Entity(
    tableName = "knowledge",
    indices = [
        Index("type"),
        Index("category"),
        Index("syncStatus")
    ]
)
data class KnowledgeEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String,
    val type: KnowledgeType,
    val tags: List<String>,
    val language: ProgrammingLanguage,
    val category: String,
    val isFavorite: Boolean,
    val codeBlock: String?,
    val difficulty: Difficulty,
    val source: String?,
    val pinned: Boolean,
    val folderId: String?,
    val embeddingStatus: String?,
    val aiSummary: String?,
    val lastIndexed: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: SyncStatus,
    val lastSyncedAt: Long?,
    val isPrivate: Boolean = false,
    val userId: String = ""
)
