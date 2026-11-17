package com.cognify.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CalculatorHistoryDao {
    @Query("SELECT * FROM calculator_history WHERE userId = :userId ORDER BY timestamp DESC")
    fun getAllHistory(userId: String): Flow<List<CalculatorHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: CalculatorHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(history: List<CalculatorHistoryEntity>)

    @Query("DELETE FROM calculator_history WHERE userId = :userId")
    suspend fun clearHistory(userId: String)
}
