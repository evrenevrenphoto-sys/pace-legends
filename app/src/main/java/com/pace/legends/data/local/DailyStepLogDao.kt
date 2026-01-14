package com.pace.legends.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pace.legends.domain.model.DailyStepLog
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyStepLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: DailyStepLog)

    @Query("SELECT * FROM daily_step_log WHERE userId = :userId AND trackId = :trackId ORDER BY epochDay DESC")
    fun observeLogs(userId: String, trackId: String): Flow<List<DailyStepLog>>
    
    @Query("SELECT * FROM daily_step_log WHERE userId = :userId AND epochDay = :day")
    suspend fun getLogForDay(userId: String, day: Long): List<DailyStepLog>

    @Query("SELECT * FROM daily_step_log WHERE userId = :userId AND epochDay >= :startDay AND epochDay <= :endDay ORDER BY epochDay DESC")
    suspend fun getLogsByDateRange(userId: String, startDay: Long, endDay: Long): List<DailyStepLog>
    
    // 🆕 Pruning Query
    @Query("DELETE FROM daily_step_log WHERE epochDay < :thresholdDay")
    suspend fun deleteOldLogs(thresholdDay: Long)
}
