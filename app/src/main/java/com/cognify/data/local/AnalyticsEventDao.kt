package com.cognify.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalyticsEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: AnalyticsEventEntity)

    @Query("SELECT * FROM analytics_events WHERE userId = :userId ORDER BY eventTime DESC")
    fun getAllEvents(userId: String): Flow<List<AnalyticsEventEntity>>

    @Query("SELECT * FROM analytics_events WHERE userId = :userId AND type = :type ORDER BY eventTime DESC")
    fun getEventsByType(type: AnalyticsEventType, userId: String): Flow<List<AnalyticsEventEntity>>
    
    @Query("SELECT * FROM analytics_events WHERE userId = :userId AND eventTime >= :startTime AND eventTime <= :endTime ORDER BY eventTime DESC")
    fun getEventsBetween(startTime: Long, endTime: Long, userId: String): Flow<List<AnalyticsEventEntity>>

    @Query("DELETE FROM analytics_events WHERE userId = :userId")
    suspend fun deleteAllEvents(userId: String)
}
