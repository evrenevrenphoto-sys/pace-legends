package com.pace.legends.data.local

import androidx.room.*
import com.pace.legends.data.local.entity.UserProgressEntity
import kotlinx.coroutines.flow.Flow

/**
 * Kullanıcı ilerlemesini yerel veritabanında saklamak için DAO.
 * Her pist ve kullanıcı için ayrı kayıt tutulur.
 */
@Dao
interface UserProgressDao {

    /**
     * Belirli bir pist için kullanıcı ilerlemesini getir
     */
    @Query("SELECT * FROM user_progress WHERE userId = :userId AND trackId = :trackId LIMIT 1")
    suspend fun getProgressByTrack(userId: String, trackId: String): UserProgressEntity?
    
    /**
     * Belirli bir pist için ilerlemeyi Flow olarak getir
     */
    @Query("SELECT * FROM user_progress WHERE userId = :userId AND trackId = :trackId LIMIT 1")
    fun observeProgressByTrack(userId: String, trackId: String): Flow<UserProgressEntity?>

    /**
     * Kullanıcının tüm pistlerdeki ilerlemesini getir
     */
    @Query("SELECT * FROM user_progress WHERE userId = :userId")
    suspend fun getAllProgress(userId: String): List<UserProgressEntity>
    
    /**
     * Tüm ilerlemeyi Flow olarak getir
     */
    @Query("SELECT * FROM user_progress WHERE userId = :userId")
    fun observeAllProgress(userId: String): Flow<List<UserProgressEntity>>

    /**
     * Toplam atılan adım sayısını getir (tüm pistler dahil)
     */
    @Query("SELECT COALESCE(SUM(totalSteps), 0) FROM user_progress WHERE userId = :userId")
    suspend fun getTotalStepsAllTracks(userId: String): Long
    
    /**
     * Toplam tamamlanan tur sayısını getir
     */
    @Query("SELECT COALESCE(SUM(completedLoops), 0) FROM user_progress WHERE userId = :userId")
    suspend fun getTotalLoopsAllTracks(userId: String): Int

    /**
     * İlerleme kaydet veya güncelle (Upsert)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: UserProgressEntity)

    /**
     * Adım sayısını artır (Atomic operation)
     */
    @Query("""
        UPDATE user_progress 
        SET totalSteps = totalSteps + :stepsToAdd,
            lastUpdateTimestamp = :timestamp,
            isSynced = 0
        WHERE userId = :userId AND trackId = :trackId
    """)
    suspend fun addStepsToTrack(userId: String, trackId: String, stepsToAdd: Long, timestamp: Long)

    /**
     * Tur sayısını artır
     */
    @Query("""
        UPDATE user_progress 
        SET completedLoops = completedLoops + :loopsToAdd,
            lastUpdateTimestamp = :timestamp,
            isSynced = 0
        WHERE userId = :userId AND trackId = :trackId
    """)
    suspend fun addLoopsToTrack(userId: String, trackId: String, loopsToAdd: Int, timestamp: Long)

    /**
     * Aktif süreyi artır
     */
    @Query("""
        UPDATE user_progress 
        SET totalActiveTimeMillis = totalActiveTimeMillis + :millisToAdd,
            lastUpdateTimestamp = :timestamp,
            isSynced = 0
        WHERE userId = :userId AND trackId = :trackId
    """)
    suspend fun addActiveTimeToTrack(userId: String, trackId: String, millisToAdd: Long, timestamp: Long)

    /**
     * En iyi tur süresini güncelle
     */
    @Query("""
        UPDATE user_progress 
        SET bestLapTimeSeconds = :newBestTime,
            lastUpdateTimestamp = :timestamp,
            isSynced = 0
        WHERE userId = :userId AND trackId = :trackId 
        AND (bestLapTimeSeconds = 0 OR bestLapTimeSeconds > :newBestTime)
    """)
    suspend fun updateBestLapTimeIfBetter(userId: String, trackId: String, newBestTime: Long, timestamp: Long)

    /**
     * Belirli bir pistin verisini sil
     */
    @Query("DELETE FROM user_progress WHERE userId = :userId AND trackId = :trackId")
    suspend fun deleteProgressByTrack(userId: String, trackId: String)

    /**
     * Kullanıcının tüm verilerini sil
     */
    @Query("DELETE FROM user_progress WHERE userId = :userId")
    suspend fun deleteAllProgress(userId: String)
    
    // ==================== Cloud Sync ====================
    
    @Query("SELECT * FROM user_progress WHERE userId = :userId AND isSynced = 0")
    suspend fun getUnsyncedProgress(userId: String): List<UserProgressEntity>

    @Query("UPDATE user_progress SET isSynced = 1, lastSyncedTimestamp = :timestamp WHERE userId = :userId AND trackId = :trackId")
    suspend fun markSynced(userId: String, trackId: String, timestamp: Long)
    
    // ==================== Transactions ====================
    
    @Transaction
    suspend fun saveProgressTransaction(
        userId: String,
        trackId: String, 
        stepsToAdd: Long, 
        durationToAdd: Long, 
        timestamp: Long
    ) {
        // Mevcut kaydı kontrol et
        val existing = getProgressByTrack(userId, trackId)
        
        if (existing == null) {
            // Yeni kayıt oluştur
            upsertProgress(
                UserProgressEntity(
                    trackId = trackId,
                    userId = userId, // userId zorunlu
                    displayName = "User", // Repository bunu güncelleyebilir
                    totalSteps = stepsToAdd,
                    completedLoops = 0,
                    totalActiveTimeMillis = durationToAdd,
                    lastUpdateTimestamp = timestamp,
                    bestLapTimeSeconds = 0,
                    isSynced = false,
                    lastSyncedTimestamp = 0,
                    totalDistanceWalked = 0.0,
                    currentLapStartTime = 0,
                    periodStartTime = 0,
                    currentLapNumber = 1,
                    allTimeSteps = 0,
                    allTimeLaps = 0,
                    allTimeDistanceMeters = 0.0
                )
            )
        } else {
            // Mevcut kaydı güncelle
            if (stepsToAdd > 0) {
                addStepsToTrack(userId, trackId, stepsToAdd, timestamp)
            }
            if (durationToAdd > 0) {
                addActiveTimeToTrack(userId, trackId, durationToAdd, timestamp)
            }
        }
    }
}
