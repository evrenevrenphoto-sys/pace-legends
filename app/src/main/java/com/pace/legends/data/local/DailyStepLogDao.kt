package com.pace.legends.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pace.legends.domain.model.DailyStepLog
import kotlinx.coroutines.flow.Flow

@Dao
abstract class DailyStepLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertLogInternal(log: DailyStepLog)
    
    // 🛡️ SECURITY FIX: Input Validation
    @androidx.room.Transaction
    open suspend fun insertLog(log: DailyStepLog) {
        if (log.userId.isEmpty()) throw IllegalArgumentException("userId boş olamaz")
        if (log.userId.length > 128) throw IllegalArgumentException("userId çok uzun")
        // Date format validation (Basic length check for YYYY-MM-DD)
        if (log.dateString.length != 10) throw IllegalArgumentException("Geçersiz tarih formatı")
        
        insertLogInternal(log)
    }

    @Query("SELECT * FROM daily_step_log WHERE userId = :userId AND trackId = :trackId ORDER BY epochDay DESC")
    abstract fun observeLogs(userId: String, trackId: String): Flow<List<DailyStepLog>>
    
    @Query("SELECT * FROM daily_step_log WHERE userId = :userId AND epochDay = :day")
    abstract suspend fun getLogForDay(userId: String, day: Long): List<DailyStepLog>

    @Query("SELECT * FROM daily_step_log WHERE userId = :userId AND epochDay >= :startDay AND epochDay <= :endDay ORDER BY epochDay DESC")
    abstract suspend fun getLogsByDateRange(userId: String, startDay: Long, endDay: Long): List<DailyStepLog>
    
    // 🆕 Pruning Query
    @Query("DELETE FROM daily_step_log WHERE epochDay < :thresholdDay")
    abstract suspend fun deleteOldLogs(thresholdDay: Long)

    // 🚀 Performance: Aggregate Queries (Avoid loading list into memory)
    @Query("SELECT SUM(steps) FROM daily_step_log WHERE userId = :userId AND trackId = :trackId AND epochDay = :day")
    abstract suspend fun getStepsForDay(userId: String, trackId: String, day: Long): Long?

    @Query("SELECT SUM(steps) FROM daily_step_log WHERE userId = :userId AND trackId = :trackId AND epochDay >= :startDay AND epochDay <= :endDay")
    abstract suspend fun getStepsForDateRange(userId: String, trackId: String, startDay: Long, endDay: Long): Long?
}
