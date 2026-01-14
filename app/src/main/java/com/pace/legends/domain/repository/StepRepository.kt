package com.pace.legends.domain.repository

import com.pace.legends.domain.model.UserProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

/**
 * Aktif yarış durumu (lifecycle için kalıcı)
 */
data class ActiveRace(
    val trackId: String,
    val startTimeMillis: Long,
    val steps: Int = 0,
    val lastLapTimestamp: Long = 0L
)

/**
 * Step Repository Interface
 * 
 * Clean Architecture: Interface domain katmanında yer alır.
 * Implementation (StepRepositoryImpl) data katmanındadır.
 * 
 * @see com.pace.legends.data.repository.StepRepositoryImpl
 */
interface StepRepository {
    val currentSteps: StateFlow<Int>
    val currentTrackId: StateFlow<String?>
    
    // 🆕 Aylık Maraton için
    val monthlySteps: StateFlow<Long>
    
    // 🆕 Pit Stop Uyarıları (SafetyCarManager)
    val pitStopWarning: kotlinx.coroutines.flow.SharedFlow<com.pace.legends.domain.model.PitStopMessage>
    
    // 🆕 Phase 5: Ad Trigger (Lap Complete) - Returns Lap Count
    val lapCompletedEvent: kotlinx.coroutines.flow.SharedFlow<Int>
    
    // Adım güncelleme (Health Connect Sync)
    suspend fun syncHealthConnectSteps(force: Boolean = false)
    
    // 🆕 Phase 5: Live Sync Control
    fun startRapidPolling()
    fun stopRapidPolling()

    // Deprecated: Sensör artık kullanılmıyor
    suspend fun updateFromSensor(sensorValue: Long)
    
    // Eski API - Backward compatibility
    suspend fun addSteps(count: Int)
    suspend fun addActiveTime(millis: Long)
    
    // İlerleme kaydetme (pist değişimi veya tur tamamlama)
    suspend fun saveCurrentProgress()
    suspend fun completeLap(lapTimeSeconds: Long)
    
    // Eski API uyumluluğu
    suspend fun saveProgress(trackId: String)
    suspend fun getSyncPendingProgress(): List<UserProgress>
    suspend fun markSynced(trackId: String)
    
    // 🆕 Track Lock-In: Aylık pist seçimi
    suspend fun selectTrackForMonth(trackId: String): Result<Unit>
    suspend fun checkTrackLockStatus(): com.pace.legends.domain.model.LockStatusResult
    fun isTrackLocked(): Boolean
    
    // Aktif yarış yönetimi (lifecycle)
    fun getActiveRace(): ActiveRace?
    suspend fun clearActiveRace()
    
    // 🆕 P0 FIX: Ensure track is always set (fallback for new users)
    suspend fun getCurrentOrSetDefaultTrack(): String
    
    // En iyi tur süresi
    fun getBestLapTime(trackId: String): Long
    fun saveBestLapTime(trackId: String, timeSeconds: Long)
    
    // İstatistikler
    suspend fun getProgressByTrack(trackId: String): UserProgress?
    fun observeProgressByTrack(trackId: String): Flow<UserProgress?>
    suspend fun getAllProgress(): List<UserProgress>
    suspend fun getTotalStepsAllTracks(): Long
    suspend fun getTotalLoopsAllTracks(): Int
    
    // 🆕 Toplam adım (Statistics için)
    suspend fun getTotalSteps(): Long
    
    // 🆕 Safety Net: Uygulama arka plana geçtiğinde zorla sync
    suspend fun forceSync()

    // 🐛 Debug Only
    suspend fun refreshUserData()
    suspend fun clearAllLocalData()
    
    // 🆕 Helper direct HC access
    suspend fun getStepsByTimeRange(startTime: Instant, endTime: Instant): Long
    
    // 🆕 Period Helper (exposed to VM)
    fun getCurrentPeriod(): String
    
    // 🆕 Fresh HC Fetch (Bypass cache)
    suspend fun getFreshCurrentPeriodSteps(): Long
    
}
