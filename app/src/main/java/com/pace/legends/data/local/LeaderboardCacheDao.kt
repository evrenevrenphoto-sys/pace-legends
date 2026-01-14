package com.pace.legends.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pace.legends.data.local.entity.LeaderboardCacheEntity

@Dao
interface LeaderboardCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<LeaderboardCacheEntity>)

    @Query("SELECT * FROM leaderboard_cache WHERE trackId = :trackId AND periodId = :periodId ORDER BY rank ASC")
    suspend fun getLeaderboard(trackId: String, periodId: String): List<LeaderboardCacheEntity>
    
    @Query("SELECT MIN(timestamp) FROM leaderboard_cache WHERE trackId = :trackId AND periodId = :periodId")
    suspend fun getCacheTimestamp(trackId: String, periodId: String): Long?

    @Query("DELETE FROM leaderboard_cache WHERE trackId = :trackId AND periodId = :periodId")
    suspend fun clearLeaderboard(trackId: String, periodId: String)
    
    // Pruning
    @Query("DELETE FROM leaderboard_cache WHERE timestamp < :threshold")
    suspend fun deleteOldCache(threshold: Long)
}
