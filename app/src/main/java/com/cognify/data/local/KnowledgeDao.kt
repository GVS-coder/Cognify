package com.cognify.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface KnowledgeDao {

    // ── Observing ──────────────────────────────────────────────────────────────
    @Query("SELECT * FROM knowledge WHERE userId = :userId ORDER BY pinned DESC, updatedAt DESC")
    fun getAllKnowledge(userId: String): Flow<List<KnowledgeEntity>>

    @Query("SELECT * FROM knowledge WHERE userId = :userId AND isPrivate = 0 ORDER BY pinned DESC, updatedAt DESC LIMIT 3")
    fun getRecentKnowledge(userId: String): Flow<List<KnowledgeEntity>>

    @Query("SELECT * FROM knowledge WHERE userId = :userId AND isFavorite = 1 ORDER BY updatedAt DESC")
    fun getFavorites(userId: String): Flow<List<KnowledgeEntity>>

    @Query("SELECT * FROM knowledge WHERE userId = :userId AND type = :type ORDER BY pinned DESC, updatedAt DESC")
    fun getByType(type: String, userId: String): Flow<List<KnowledgeEntity>>

    @Query("SELECT * FROM knowledge WHERE userId = :userId AND category = :category ORDER BY pinned DESC, updatedAt DESC")
    fun getByCategory(category: String, userId: String): Flow<List<KnowledgeEntity>>

    @Query("""
        SELECT * FROM knowledge WHERE userId = :userId AND (
            (isPrivate = 0 AND (
                title LIKE '%' || :query || '%' OR
                description LIKE '%' || :query || '%' OR
                tags LIKE '%' || :query || '%' OR
                language LIKE '%' || :query || '%' OR
                category LIKE '%' || :query || '%' OR
                codeBlock LIKE '%' || :query || '%'
            )) OR
            (isPrivate = 1 AND title LIKE '%' || :query || '%')
        ) ORDER BY pinned DESC, updatedAt DESC
    """)
    fun search(query: String, userId: String): Flow<List<KnowledgeEntity>>

    @Query("SELECT * FROM knowledge WHERE userId = :userId AND (syncStatus = 'PENDING' OR syncStatus = 'FAILED')")
    suspend fun getUnsyncedKnowledge(userId: String): List<KnowledgeEntity>

    // ── Single item ────────────────────────────────────────────────────────────
    @Query("SELECT * FROM knowledge WHERE id = :id AND userId = :userId")
    suspend fun getKnowledgeById(id: String, userId: String): KnowledgeEntity?

    // ── Write ──────────────────────────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKnowledge(item: KnowledgeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<KnowledgeEntity>)

    @Update
    suspend fun updateKnowledge(item: KnowledgeEntity)

    @Query("DELETE FROM knowledge WHERE id = :id AND userId = :userId")
    suspend fun deleteKnowledgeById(id: String, userId: String)

    @Query("DELETE FROM knowledge WHERE userId = :userId")
    suspend fun deleteAll(userId: String)
}
