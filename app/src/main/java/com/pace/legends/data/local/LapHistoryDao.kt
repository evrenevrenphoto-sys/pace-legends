package com.pace.legends.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pace.legends.domain.model.LapHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface LapHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLap(lap: LapHistory)

    @Query("SELECT * FROM lap_history WHERE userId = :userId AND trackId = :trackId ORDER BY lapNumber DESC")
    fun observeLaps(userId: String, trackId: String): Flow<List<LapHistory>>
    
    @Query("SELECT * FROM lap_history WHERE userId = :userId AND trackId = :trackId ORDER BY lapNumber DESC")
    suspend fun getLaps(userId: String, trackId: String): List<LapHistory>
    
    @Query("DELETE FROM lap_history")
    suspend fun deleteAllLaps()

    @Query("DELETE FROM lap_history WHERE endTime < :thresholdTime")
    suspend fun deleteOldLaps(thresholdTime: Long)
}
