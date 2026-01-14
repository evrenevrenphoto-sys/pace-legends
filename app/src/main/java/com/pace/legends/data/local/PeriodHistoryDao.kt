package com.pace.legends.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.pace.legends.data.local.entity.PeriodHistoryEntity

@Dao
interface PeriodHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPeriod(period: PeriodHistoryEntity)
    
    @Query("SELECT * FROM period_history WHERE userId = :userId ORDER BY endTimestamp DESC")
    fun observeAllPeriods(userId: String): Flow<List<PeriodHistoryEntity>>
    
    @Query("SELECT * FROM period_history WHERE userId = :userId ORDER BY endTimestamp DESC")
    suspend fun getAllPeriods(userId: String): List<PeriodHistoryEntity>
    
    @Query("SELECT * FROM period_history WHERE userId = :userId AND trackId = :trackId ORDER BY endTimestamp DESC")
    suspend fun getPeriodsByTrack(userId: String, trackId: String): List<PeriodHistoryEntity>
    
    @Query("SELECT SUM(totalSteps) FROM period_history WHERE userId = :userId")
    suspend fun getAllTimeSteps(userId: String): Long?
    
    @Query("SELECT SUM(completedLaps) FROM period_history WHERE userId = :userId")
    suspend fun getAllTimeLaps(userId: String): Int?
}
