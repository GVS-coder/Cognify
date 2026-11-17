package com.cognify.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Query("SELECT * FROM conversations WHERE userId = :userId ORDER BY updatedAt DESC")
    fun getConversations(userId: String): Flow<List<ConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ConversationMessageEntity)

    @Query("SELECT cm.* FROM conversation_messages cm INNER JOIN conversations c ON cm.conversationId = c.id WHERE cm.conversationId = :conversationId AND c.userId = :userId ORDER BY cm.timestamp ASC")
    fun getMessagesForConversation(conversationId: String, userId: String): Flow<List<ConversationMessageEntity>>

    @Query("SELECT cm.* FROM (SELECT cm_inner.* FROM conversation_messages cm_inner INNER JOIN conversations c ON cm_inner.conversationId = c.id WHERE cm_inner.conversationId = :conversationId AND c.userId = :userId ORDER BY cm_inner.timestamp DESC LIMIT :limit) cm ORDER BY cm.timestamp ASC")
    suspend fun getRecentMessagesList(conversationId: String, limit: Int, userId: String): List<ConversationMessageEntity>

    @Query("DELETE FROM conversations WHERE id = :conversationId AND userId = :userId")
    suspend fun deleteConversation(conversationId: String, userId: String)
    
    @Query("UPDATE conversations SET updatedAt = :timestamp WHERE id = :conversationId AND userId = :userId")
    suspend fun updateConversationTimestamp(conversationId: String, timestamp: Long, userId: String)

    @Query("DELETE FROM conversation_messages WHERE userId = :userId")
    suspend fun deleteAllMessages(userId: String)

    @Query("DELETE FROM conversations WHERE userId = :userId")
    suspend fun deleteAllConversations(userId: String)
}
