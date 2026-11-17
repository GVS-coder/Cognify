package com.cognify.data.repository

import com.cognify.data.local.KnowledgeEntity
import kotlinx.coroutines.flow.Flow

interface KnowledgeRepository {
    fun getAllKnowledge(): Flow<List<KnowledgeEntity>>
    fun getRecentKnowledge(): Flow<List<KnowledgeEntity>>
    fun getFavorites(): Flow<List<KnowledgeEntity>>
    fun getByType(type: String): Flow<List<KnowledgeEntity>>
    fun search(query: String): Flow<List<KnowledgeEntity>>
    suspend fun getById(id: String): KnowledgeEntity?
    suspend fun saveKnowledge(item: KnowledgeEntity)
    suspend fun deleteKnowledge(id: String)
    suspend fun syncPendingToFirestore()
    suspend fun syncFromFirestore()
}
