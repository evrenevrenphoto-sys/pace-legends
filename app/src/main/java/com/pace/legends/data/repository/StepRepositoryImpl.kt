package com.pace.legends.data.repository

import android.content.Context
import com.pace.legends.data.local.AppDatabase
import com.pace.legends.data.local.UserProgressDao
import com.pace.legends.data.local.entity.UserProgressEntity
import com.pace.legends.domain.model.UserProgress
import com.pace.legends.domain.repository.AuthRepository
import com.pace.legends.domain.repository.StepRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import com.pace.legends.domain.manager.HealthConnectManager

/**
 * StepRepository Implementation
 * 
 * Clean Architecture: Implementation sınıfı Data katmanında yer alır.
 * Domain katmanındaki interface'i implement eder.
 */
@Singleton
class StepRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val userProgressDao: UserProgressDao,
    private val dailyStepLogDao: com.pace.legends.data.local.DailyStepLogDao,
    private val lapHistoryDao: com.pace.legends.data.local.LapHistoryDao,
    private val authRepository: AuthRepository,
    private val stepSyncManager: com.pace.legends.domain.manager.StepSyncManager,
    private val healthConnectManager: HealthConnectManager,
    private val badgeManager: com.pace.legends.domain.manager.BadgeManager,
    private val trackRepository: com.pace.legends.domain.repository.TrackRepository, // 🆕 Dinamik pist uzunluğu için
    private val safetyCarManager: com.pace.legends.domain.manager.SafetyCarManager, // 🆕 Pit stop uyarıları
    @com.pace.legends.di.ApplicationScope private val repositoryScope: CoroutineScope,
    @ApplicationContext private val context: Context
) : StepRepository {

    private val prefs by lazy {
        // ✅ P1-6: Use EncryptedSharedPreferences instead of plain SharedPreferences
        val masterKey = androidx.security.crypto.MasterKey.Builder(context)
            .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
            .build()
        androidx.security.crypto.EncryptedSharedPreferences.create(
            context,
            "pace_legends_race_encrypted", // New encrypted storage
            masterKey,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    // private val dao: UserProgressDao = db.userProgressDao() ❌ Removed in favor of injection
    // private val dao = userProgressDao // Use the injected one directly if needed, or rename usage.
    // Simplifying: I will replace usages of 'dao' with 'userProgressDao' in the file, but 'dao' is a short name used often.
    // To minimize changes, I can do:
    private val dao = userProgressDao

    private val _currentSteps = MutableStateFlow(0)
    override val currentSteps: StateFlow<Int> = _currentSteps
    
    private val _currentTrackId = MutableStateFlow<String?>(null)
    override val currentTrackId: StateFlow<String?> = _currentTrackId
    
    // 🆕 Aylık Maraton için
    private val _monthlySteps = MutableStateFlow(0L)
    override val monthlySteps: StateFlow<Long> = _monthlySteps

    // 🆕 Pit Stop Uyarıları
    private val _pitStopWarning = kotlinx.coroutines.flow.MutableSharedFlow<com.pace.legends.domain.model.PitStopMessage>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    override val pitStopWarning = _pitStopWarning.asSharedFlow()

    // 🆕 Phase 5: Lap Completed Event
    private val _lapCompletedEvent = kotlinx.coroutines.flow.MutableSharedFlow<Int>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    override val lapCompletedEvent = _lapCompletedEvent.asSharedFlow()

    // In-memory counters (oturum için) - Mutex ile korunuyor
    private var sessionSteps = 0
    private var unsavedSteps = 0
    private var sessionDuration = 0L
    
    // P0 FIX: Race Condition önlemek için Mutex
    private val stepMutex = Mutex()

    init {
        // Uygulama açıldığında aktif yarış varsa verileri geri yükle
        val savedTrackId = prefs.getString("active_track_id", null)
        if (savedTrackId != null) {
            val savedSteps = prefs.getInt("active_steps", 0)
            sessionSteps = savedSteps
            unsavedSteps = savedSteps
            _currentSteps.value = savedSteps
            _currentTrackId.value = savedTrackId
        }
        
        // Aylık adımları yükle
        _monthlySteps.value = prefs.getLong("monthly_steps", 0L)

        // One-time cleanup of debug offset
        if (prefs.contains("debug_step_offset")) {
            prefs.edit().remove("debug_step_offset").apply()
        }
        
        // 🆕 P1 FIX: SharedPreferences listener for track sync
        // Lig değişiminde Firestore güncellenince local StateFlow'u da güncelle
        prefs.registerOnSharedPreferenceChangeListener { _, key ->
            if (key == "active_track_id") {
                val newTrack = prefs.getString(key, null)
                if (newTrack != null && newTrack != _currentTrackId.value) {
                    android.util.Log.i("StepRepository", "🔄 Track changed via prefs: ${_currentTrackId.value} → $newTrack")
                    _currentTrackId.value = newTrack
                }
            }
        }
    }
    
    private fun getUserId(): String? {
        return authRepository.getCurrentUserId()
    }
    
    /**
     * Dönem başlangıç zamanını belirle
     */
    private fun getStartOfPeriod(trackId: String?): Instant {
        val periodInfo = stepSyncManager.getCurrentPeriodInfo()
        val periodStartDate = periodInfo.startDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
        
        android.util.Log.d("DEBUG_DATE", "🔍 PeriodInfo: ID=${periodInfo.periodId}, Start=${periodInfo.startDate}, End=${periodInfo.endDate}, Remaining=${periodInfo.daysRemaining} days")
        android.util.Log.d("DEBUG_DATE", "🔍 Calculated Start Instant: $periodStartDate")
        
        return periodStartDate
    }

    /**
     * 🆕 Health Connect ile Senkronizasyon (Polling)
     * Force: true ise cost-saving kurallarını yoksayar (App Start)
     */
    override suspend fun syncHealthConnectSteps(force: Boolean) {
        val activeTrackId = _currentTrackId.value
        
        android.util.Log.d("DEBUG_SYNC", "🔍 STARTING SYNC - Force: $force")
        android.util.Log.d("DEBUG_SYNC", "🔍 hasAllPermissions: ${healthConnectManager.hasAllPermissions()}")
        android.util.Log.d("DEBUG_SYNC", "🔍 HC Client null?: ${healthConnectManager.healthConnectClient == null}")
        android.util.Log.d("DEBUG_SYNC", "🔍 activeTrackId: $activeTrackId")


        // 🔄 CENTRALIZED FIX: Dönem geçiş kontrolü (StepSyncManager yönetir)
        val periodChanged = stepSyncManager.checkForPeriodTransition()
        
        if (periodChanged) {
            android.util.Log.i("StepRepository", "✅ Period transition handled by StepSyncManager")
        }


        // 1. Heavy Read operations - Outside Mutex
        if (!healthConnectManager.hasAllPermissions()) {
            android.util.Log.w("StepRepository", "⚠️ Health Connect permissions missing")
            return
        }

        // 🆕 CRASH FIX: Android 14+ requires foreground for Health Connect read
        // Prevents SecurityException: "must be in foreground to call aggregate method"
        if (!isAppInForeground()) {
            android.util.Log.d("StepRepository", "📱 Sync skipped: App in background (SecurityException prevention)")
            return
        }

        val now = Instant.now()
        val startTime = getStartOfPeriod(activeTrackId)
        
        // 🆕 CRASH FIX: Wrap Health Connect calls
        val effectiveTotalSteps = try {
            val realSteps = healthConnectManager.readStepsByTimeRange(startTime, now)
            if (realSteps < 0) {
                 android.util.Log.e("StepRepository", "❌ Sync Aborted: Health Connect returned error ($realSteps)")
                 return
            }
            realSteps
        } catch (e: Exception) {
            android.util.Log.e("StepRepository", "❌ Health Connect critical crash prevented: ${e.message}")
            return
        }

        // 2. Atomic Read-Update-Write - Inside Mutex
            stepMutex.withLock {
                val userId = getUserId()
                if (activeTrackId != null && userId != null) {
                    val dbProgressEntity = dao.getProgressByTrack(userId, activeTrackId)
                    val dbProgress = dbProgressEntity?.toDomain()
                    val dbTotalSteps = dbProgress?.totalSteps ?: 0L
                    
                    android.util.Log.d("StepRepository", "📈 Sync: Updating DB from HC (DB: $dbTotalSteps -> HC: $effectiveTotalSteps)")
                    
                    // 🆕 Phase 5: Anti-Cheat Cadence Filter
                    // İnsan sınırlarını aşan ani adım artışlarını filtrele
                    // Örn: Usain Bolt ~4.5 adım/sn atıyor. Biz 8 adım/sn (480/dk) diyelim.
                    val dbLastUpdate = dbProgress?.lastUpdateTimestamp ?: 0L
                    val timeDeltaMillis = System.currentTimeMillis() - dbLastUpdate
                    val stepDelta = effectiveTotalSteps - dbTotalSteps
                    
                    // Sadece pozitif ve anlamlı değişimlerde kontrol et
                    if (stepDelta > 50 && timeDeltaMillis > 2000) {
                        val stepsPerSecond = stepDelta.toDouble() / (timeDeltaMillis / 1000.0)
                        
                        if (stepsPerSecond > 10.0) { // 10 adım/saniye (Çok agresif limit)
                            android.util.Log.e("StepRepository", "🛡️ CADENCE LIMIT EXCEEDED: $stepsPerSecond steps/sec ($stepDelta in ${timeDeltaMillis}ms). Ignoring update.")
                            return@withLock
                        }
                    }

                    val periodStartTime = startTime.toEpochMilli()
                    val currentProgress = dbProgress ?: UserProgress(
                        userId = userId,
                        trackId = activeTrackId,
                        periodStartTime = periodStartTime
                    )
                    
                    val totalDistance = effectiveTotalSteps * com.pace.legends.domain.util.RaceProgressCalculator.STEP_TO_METERS
                    
                    val isSamePeriod = currentProgress.periodStartTime == periodStartTime
                    
                    var newAllTimeSteps = currentProgress.allTimeSteps
                    var newAllTimeDistance = currentProgress.allTimeDistanceMeters
                    
                    if (isSamePeriod) {
                        val diff = effectiveTotalSteps - dbTotalSteps
                        newAllTimeSteps += diff
                        newAllTimeDistance += (diff * com.pace.legends.domain.util.RaceProgressCalculator.STEP_TO_METERS)
                    } else {
                        newAllTimeSteps += effectiveTotalSteps
                        newAllTimeDistance += (effectiveTotalSteps * com.pace.legends.domain.util.RaceProgressCalculator.STEP_TO_METERS)
                        
                        android.util.Log.d("StepRepository", "📅 New period detected! Adding $effectiveTotalSteps to AllTime (Base: ${currentProgress.allTimeSteps})")
                    }

                    val updatedProgress = currentProgress.copy(
                        totalSteps = effectiveTotalSteps,
                        totalDistanceWalked = totalDistance,
                        lastUpdateTimestamp = System.currentTimeMillis(),
                        periodStartTime = periodStartTime,
                        allTimeSteps = if (newAllTimeSteps < 0) 0 else newAllTimeSteps,
                        allTimeDistanceMeters = if (newAllTimeDistance < 0.0) 0.0 else newAllTimeDistance
                    )
                    dao.upsertProgress(UserProgressEntity.fromDomain(updatedProgress))
                
                // Daily Log - Optional/Secondary
                try {
                    val todayDate = java.time.LocalDate.now().toString()
                    val todayStart = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
                    val realStepsToday = healthConnectManager.readStepsByTimeRange(todayStart, now)
                    if (realStepsToday >= 0) {
                        val epochDay = java.time.LocalDate.now().toEpochDay()
                        val dailyLog = com.pace.legends.domain.model.DailyStepLog(
                            epochDay = epochDay,
                            dateString = todayDate,
                            trackId = activeTrackId,
                            userId = userId,
                            steps = realStepsToday,
                            distance = realStepsToday * com.pace.legends.domain.util.RaceProgressCalculator.STEP_TO_METERS
                        )
                        dailyStepLogDao.insertLog(dailyLog)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("StepRepository", "Failed to update daily log", e)
                }

                _monthlySteps.value = effectiveTotalSteps
                _currentSteps.value = effectiveTotalSteps.toInt()
            } else {
                _monthlySteps.value = effectiveTotalSteps
                _currentSteps.value = effectiveTotalSteps.toInt()
            }

            prefs.edit().putLong("monthly_steps", _monthlySteps.value).apply()
        }

        // 🆕 SAFETY CAR: Pit Stop Uyarı Kontrolü
        // Bu background step limitine yaklaşınca kullanıcıyı uyar
        checkPitStopWarning(effectiveTotalSteps)

        // 3. Cloud Sync - Outside Mutex (Fire-and-forget but SAFE)
        if (activeTrackId != null) {
            val totalSteps = _monthlySteps.value
            
            // 🆕 P3 FIX: syncIfNeeded artık SyncLogic'i içeriyor (Throttling UseCase içinde)
            // Sadece çağırmamız yeterli.
            repositoryScope.launch {
                try {
                    // Pass the 'force' parameter from the method argument, don't hardcode true!
                    val result = stepSyncManager.syncIfNeeded(totalSteps, activeTrackId, force = force)
                    
                    if (result is com.pace.legends.domain.usecase.sync.SyncResult.Success) {
                        // ✅ Başarılı sync sonrası Local DB'yi güncelle
                        markSynced(activeTrackId)
                        
                        // Prefs güncelle (Hala yedek olarak tutuyoruz)
                        prefs.edit()
                            .putLong("repo_last_synced_steps", totalSteps)
                            .putLong("repo_last_sync_time", System.currentTimeMillis())
                            .apply()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("StepRepository", "⚠️ Sync failed safely: ${e.message}")
                }
            }
        }
    }

    /**
     * 💰 Aggressive Cost-Saving Logic
     */
    private fun shouldSync(currentSteps: Long, lastSteps: Long, lastTime: Long, force: Boolean): Boolean {
        if (force) {
            android.util.Log.d("StepRepository", "🚀 Force sync requested (App Start/Exit)")
            return true
        }
        
        val timeDiff = System.currentTimeMillis() - lastTime
        val timeDiffMinutes = timeDiff / (1000 * 60)
        
        // 💰 Rule 1: Minimum 5 minutes between cloud syncs (Cost Saving)
        // Kullanıcı 10 saniyede bir lokalde güncelleniyor, ama sunucuya 5 dakikada bir yazıyoruz.
        if (timeDiffMinutes < 5) {
             // Debug log seviyesini düşürebiliriz spam olmaması için
             // android.util.Log.v("StepRepository", "⏸️ Sync throttle: Only ${timeDiffMinutes}m passed (Min 5m)")
             return false
        }
        
        val stepDiff = currentSteps - lastSteps
        if (stepDiff < 0) return true // Veri tutarsızlığı düzeltmesi
        
        // 💰 Rule 2: If less than 100 steps, don't bother syncing even if 5 mins passed
        // Küçük değişimler için 15 dakika bekle
        if (stepDiff < 100 && timeDiffMinutes < 15) {
            android.util.Log.d("StepRepository", "⏸️ Sync throttle: Small change ($stepDiff) & <15m passed")
            return false
        }
        
        android.util.Log.d("StepRepository", "✅ Sync Clean: Diff=$stepDiff, Time=${timeDiffMinutes}m")
        return true
    }

    /**
     * 🆕 SAFETY CAR: Pit Stop Uyarı Kontrolü
     * Kullanıcının arka plan step limitine yaklaşıp yaklaşmadığını kontrol et
     */
    private fun checkPitStopWarning(currentSteps: Long) {
        val warningThreshold = safetyCarManager.getWarningThreshold()
        val criticalThreshold = safetyCarManager.getCriticalThreshold()
        
        // Throttling: Son uyarıdan itibaren en az 1 saat geçmiş olmalı
        val lastWarningTime = prefs.getLong("last_pitstop_warning", 0L)
        val now = System.currentTimeMillis()
        val hoursSinceLastWarning = (now - lastWarningTime) / (1000 * 60 * 60)
        
        if (hoursSinceLastWarning < 1) {
            // Spam önleme: 1 saat içinde tekrar uyarma
            return
        }
        
        when {
            currentSteps >= criticalThreshold -> {
                val message = safetyCarManager.getCriticalMessage()
                repositoryScope.launch {
                    _pitStopWarning.emit(message)
                    prefs.edit().putLong("last_pitstop_warning", now).apply()
                    android.util.Log.w("StepRepository", "🛑 CRITICAL: ${message.title}")
                }
            }
            currentSteps >= warningThreshold -> {
                val message = safetyCarManager.getWarningMessage()
                repositoryScope.launch {
                    _pitStopWarning.emit(message)
                    prefs.edit().putLong("last_pitstop_warning", now).apply()
                    android.util.Log.w("StepRepository", "⚠️ WARNING: ${message.title}")
                }
            }
        }
    }


    /**
     * Deprecated: Sensör dinleme (Artık kullanılmıyor)
     */
    override suspend fun updateFromSensor(sensorValue: Long) {
        android.util.Log.d("StepRepository", "🚫 updateFromSensor ignored (Health Connect migration)")
    }

    /**
     * 🆕 Safety Net: Uygulama arka plana geçtiğinde zorla sync
     */
    override suspend fun forceSync() {
        val activeTrackId = _currentTrackId.value
        val currentSteps = _monthlySteps.value
        
        if (activeTrackId != null && currentSteps > 0) {
            android.util.Log.d("StepRepository", "🛡️ Force sync triggered: $currentSteps steps to $activeTrackId")
            stepSyncManager.syncIfNeeded(currentSteps, activeTrackId, force = true)
        } else {
            android.util.Log.d("StepRepository", "🛡️ Force sync skipped: no active track or zero steps")
        }
    }
    
    /**
     * 🐛 DEBUG: Verileri yenile
     */
    override suspend fun refreshUserData() {
        val isFreshStart = prefs.getBoolean("is_fresh_start", false)
        if (isFreshStart) {
            android.util.Log.d("StepRepository", "🆕 Fresh start mode - skipping Firestore restoration")
            _currentTrackId.value = null
            _lockedMonth = null
            _isLocked = false
            return
        }
        
        val lockResult = checkTrackLockStatus()
        
        when (lockResult) {
            is com.pace.legends.domain.model.LockStatusResult.Locked -> {
                val trackId = lockResult.trackId
                val lockedMonth = lockResult.month
                
                _currentTrackId.value = trackId
                _lockedMonth = lockedMonth
                _isLocked = true
                
                prefs.edit()
                    .putString("active_track_id", trackId)
                    .putString("locked_month", lockedMonth)
                    .apply()
            }
            is com.pace.legends.domain.model.LockStatusResult.NotLocked,
            is com.pace.legends.domain.model.LockStatusResult.Error -> {
                // 🆕 P0 FIX: Lock durumu yoksa bile default track ata
                // Böylece "pist kurtarılamadı" hatası engellenir
                android.util.Log.w("StepRepository", "⚠️ No locked track found, using fallback...")
                
                val fallbackTrack = getCurrentOrSetDefaultTrack()
                _currentTrackId.value = fallbackTrack
                _lockedMonth = null
                _isLocked = false
                
                prefs.edit()
                    .putString("active_track_id", fallbackTrack)
                    .remove("locked_month")
                    .apply()
                    
                android.util.Log.i("StepRepository", "✅ Fallback track set: $fallbackTrack")
            }
        }
    }

    override suspend fun addSteps(count: Int) = stepMutex.withLock {
        val activeTrackId = _currentTrackId.value
        if (activeTrackId == null) {
            android.util.Log.w("StepRepository", "addSteps called but no active track - ignoring $count steps")
            return@withLock
        }
        
        sessionSteps += count
        unsavedSteps += count
        
        val newTotal = sessionSteps
        _currentSteps.value = newTotal
        
        prefs.edit().putInt("active_steps", newTotal).apply()
        
        repositoryScope.launch {
            try {
                val userId = getUserId() ?: return@launch
                val totalSteps = dao.getTotalStepsAllTracks(userId) + newTotal
                val totalDistance = totalSteps * com.pace.legends.domain.util.RaceProgressCalculator.STEP_TO_METERS
                
                badgeManager.checkBadges(
                    totalSteps = totalSteps,
                    totalDistanceMeters = totalDistance,
                    completedLoops = 0,
                    sessionSteps = newTotal
                )
            } catch (e: Exception) {
                android.util.Log.e("StepRepository", "Badge check failed: ${e.message}")
            }
        }
    }

    override suspend fun addActiveTime(millis: Long) = stepMutex.withLock {
        sessionDuration += millis
    }
    
    override suspend fun saveCurrentProgress() = stepMutex.withLock {
        saveCurrentProgressInternal()
    }
    
    private suspend fun saveCurrentProgressInternal() {
        val trackId = _currentTrackId.value ?: return
        
        val stepsToSave = unsavedSteps.toLong()
        val durationToSave = sessionDuration
        
        unsavedSteps = 0
        sessionDuration = 0L
        
        val timestamp = System.currentTimeMillis()
        
        if (stepsToSave == 0L && durationToSave == 0L) return
        
        android.util.Log.d("StepRepository", "Saving $stepsToSave unsaved steps to track: $trackId")
        
        val userId = getUserId() ?: return
        dao.saveProgressTransaction(userId, trackId, stepsToSave, durationToSave, timestamp)
    }

    override suspend fun completeLap(lapTimeSeconds: Long) = stepMutex.withLock {
        val trackId = _currentTrackId.value ?: return@withLock
        val timestamp = System.currentTimeMillis()
        val userId = getUserId() ?: return@withLock
        
        saveCurrentProgressInternal()
        
        val currentProgressEntity = dao.getProgressByTrack(userId, trackId)
        val currentProgress = currentProgressEntity?.toDomain()
        val currentLapNum = currentProgress?.currentLapNumber ?: 1
        val startTime = currentProgress?.currentLapStartTime ?: timestamp
        
        // 🔄 FIX v3: Dinamik pist uzunluğu (Remote Config/Firestore'dan)
        val track = trackRepository.getTrack(trackId).getOrNull()
        val trackDistanceMeters = track?.totalDistanceMeters?.toDouble() ?: 5338.0 // Fallback: İstanbul Park
        val totalDistanceMeters = _monthlySteps.value * com.pace.legends.domain.util.RaceProgressCalculator.STEP_TO_METERS
        val lapDistanceMeters = totalDistanceMeters % trackDistanceMeters
        val lapSteps = (lapDistanceMeters / com.pace.legends.domain.util.RaceProgressCalculator.STEP_TO_METERS).toLong()
            .coerceAtLeast(1) // Minimum 1 adım (0 olmasını engelle)
        
        val lapHistory = com.pace.legends.domain.model.LapHistory(
            trackId = trackId,
            userId = userId,
            startTime = startTime,
            endTime = timestamp,
            durationSeconds = lapTimeSeconds,
            totalSteps = lapSteps,
            lapNumber = currentLapNum,
            periodId = stepSyncManager.getCurrentPeriod()
        )
        
        lapHistoryDao.insertLap(lapHistory)
        
        dao.addLoopsToTrack(userId, trackId, 1, timestamp)
        
        val nextLapNum = currentLapNum + 1
        if (currentProgress != null) {
            val updated = currentProgress.copy(
                completedLoops = currentProgress.completedLoops + 1,
                currentLapNumber = nextLapNum,
                currentLapStartTime = timestamp,
                lastUpdateTimestamp = timestamp,
                allTimeLaps = currentProgress.allTimeLaps + 1
            )
            dao.upsertProgress(UserProgressEntity.fromDomain(updated))
        }
        
        val totalLoops = dao.getTotalLoopsAllTracks(userId)
        val totalSteps = dao.getTotalStepsAllTracks(userId)
        
        repositoryScope.launch {
             badgeManager.checkBadges(totalSteps, totalSteps * com.pace.legends.domain.util.RaceProgressCalculator.STEP_TO_METERS, totalLoops, 0)
        }
        
        dao.updateBestLapTimeIfBetter(userId, trackId, lapTimeSeconds, timestamp)
        
        sessionSteps = 0
        unsavedSteps = 0
        _currentSteps.value = _monthlySteps.value.toInt()
        
        prefs.edit()
            .putInt("active_steps", 0)
            .putLong("active_last_lap_timestamp", timestamp)
            .apply()
            
        android.util.Log.d("StepRepository", "✅ Lap $currentLapNum Completed. History Saved. Next Lap: $nextLapNum")
        
        repositoryScope.launch {
            _lapCompletedEvent.emit(currentLapNum)
        }
        
        val syncRequest = androidx.work.OneTimeWorkRequestBuilder<com.pace.legends.worker.DataSyncWorker>()
            .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        androidx.work.WorkManager.getInstance(context).enqueue(syncRequest)
    }

    // Lock durumu (memory cache)
    @Volatile
    private var _isLocked = false
    @Volatile
    private var _lockedMonth: String? = null
    
    override suspend fun selectTrackForMonth(trackId: String): Result<Unit> = stepMutex.withLock {
        android.util.Log.d("StepRepository", "🔐 Selecting track for month: $trackId")
        
        val result = stepSyncManager.selectTrackForMonth(trackId)
        
        if (result.isSuccess) {
            _currentTrackId.value = trackId
            _isLocked = true
            _lockedMonth = stepSyncManager.getCurrentPeriod()
            
            prefs.edit()
                .putString("active_track_id", trackId)
                .putString("locked_month", _lockedMonth)
                .putLong("locked_timestamp", System.currentTimeMillis())
                .remove("is_fresh_start")
                .apply()
            
            val userId = getUserId() ?: return@withLock Result.failure(Exception("Kullanıcı giriş yapmamış"))
            val existing = dao.getProgressByTrack(userId, trackId)
            if (existing == null) {
                val periodInfo = stepSyncManager.getCurrentPeriodInfo()
                val actualPeriodStart = periodInfo.startDate
                    .atStartOfDay(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                
                val newProgress = UserProgress(
                    trackId = trackId,
                    userId = userId,
                    displayName = "User",
                    totalSteps = 0,
                    completedLoops = 0,
                    totalActiveTimeMillis = 0,
                    lastUpdateTimestamp = System.currentTimeMillis(),
                    bestLapTimeSeconds = 0,
                    periodStartTime = actualPeriodStart
                )
                dao.upsertProgress(UserProgressEntity.fromDomain(newProgress))
            }
            
            android.util.Log.d("StepRepository", "✅ Track locked: $trackId for $_lockedMonth")
        }
        
        result
    }
    
    override suspend fun checkTrackLockStatus(): com.pace.legends.domain.model.LockStatusResult {
        return stepSyncManager.checkLockStatus()
    }
    
    override fun isTrackLocked(): Boolean {
        val currentPeriod = stepSyncManager.getCurrentPeriod()
        return _isLocked && _lockedMonth == currentPeriod
    }

    override suspend fun saveProgress(trackId: String) {
        saveCurrentProgress()
    }
    
    override suspend fun markSynced(trackId: String) {
        val userId = getUserId() ?: return
        dao.markSynced(userId, trackId, System.currentTimeMillis())
        android.util.Log.d("StepRepository", "✅ Local DB marked as synced for $trackId")
    }

    override suspend fun getSyncPendingProgress(): List<UserProgress> {
        val userId = getUserId() ?: return emptyList()
        return dao.getAllProgress(userId).map { it.toDomain() }
    }



    override fun getActiveRace(): com.pace.legends.domain.repository.ActiveRace? {
        val trackId = prefs.getString("active_track_id", null) ?: return null
        val startTime = prefs.getLong("active_start_time", 0)
        val steps = prefs.getInt("active_steps", 0)
        val lastLapTimestamp = prefs.getLong("active_last_lap_timestamp", startTime)
        
        if (startTime == 0L) return null
        return com.pace.legends.domain.repository.ActiveRace(trackId, startTime, steps, lastLapTimestamp)
    }
    
    override suspend fun clearActiveRace() = stepMutex.withLock {
        saveCurrentProgressInternal()
        
        prefs.edit()
            .remove("active_track_id")
            .remove("active_start_time")
            .remove("active_steps")
            .remove("active_last_lap_timestamp")
            .apply()
            
        sessionSteps = 0
        unsavedSteps = 0
        sessionDuration = 0L
        _currentSteps.value = 0
        _currentTrackId.value = null
    }
    
    override fun getBestLapTime(trackId: String): Long {
        return prefs.getLong("best_lap_$trackId", 0L)
    }
    
    override fun saveBestLapTime(trackId: String, timeSeconds: Long) {
        val currentBest = getBestLapTime(trackId)
        if (currentBest == 0L || timeSeconds < currentBest) {
            prefs.edit()
                .putLong("best_lap_$trackId", timeSeconds)
                .apply()
        }
    }
    
    override suspend fun getProgressByTrack(trackId: String): UserProgress? {
        val userId = getUserId() ?: return null
        return dao.getProgressByTrack(userId, trackId)?.toDomain()
    }
    
    override fun observeProgressByTrack(trackId: String): Flow<UserProgress?> {
        val userId = getUserId() ?: return kotlinx.coroutines.flow.flowOf(null)
        return dao.observeProgressByTrack(userId, trackId).map { it?.toDomain() }
    }
    
    override suspend fun getAllProgress(): List<UserProgress> {
        val userId = getUserId() ?: return emptyList()
        return dao.getAllProgress(userId).map { it.toDomain() }
    }
    
    override suspend fun getTotalStepsAllTracks(): Long {
        val userId = getUserId() ?: return 0L
        return dao.getTotalStepsAllTracks(userId)
    }
    
    override suspend fun getTotalLoopsAllTracks(): Int {
        val userId = getUserId() ?: return 0
        return dao.getTotalLoopsAllTracks(userId)
    }
    
    override suspend fun getTotalSteps(): Long {
        return _monthlySteps.value
    }
    
    override suspend fun clearAllLocalData() {
        prefs.edit().clear().apply()
        
        prefs.edit().putBoolean("is_fresh_start", true).apply()
        
        val userId = getUserId()
        if (!userId.isNullOrEmpty()) {
            dao.deleteAllProgress(userId)
            try { lapHistoryDao.deleteAllLaps() } catch (e: Exception) { }
        }
        
        try {
            db.clearAllTables()
            android.util.Log.w("StepRepository", "🔥 Database tables cleared.")
        } catch (e: Exception) {
             android.util.Log.e("StepRepository", "❌ Failed to clear DB tables: ${e.message}")
        }
        
        stepMutex.withLock {
            sessionSteps = 0
            unsavedSteps = 0
            sessionDuration = 0L
            _currentSteps.value = 0
            _monthlySteps.value = 0L
            _currentTrackId.value = null
            _isLocked = false
            _lockedMonth = null
        }
        
        android.util.Log.d("StepRepository", "🗑️ All local data cleared! Fresh start flag set.")
    }

    override suspend fun getStepsByTimeRange(startTime: Instant, endTime: Instant): Long {
        return healthConnectManager.readStepsByTimeRange(startTime, endTime)
    }
    
    override fun getCurrentPeriod(): String {
        return stepSyncManager.getCurrentPeriod()
    }
    
    override suspend fun getFreshCurrentPeriodSteps(): Long {
        if (!healthConnectManager.hasAllPermissions()) return 0L
        val activeTrackId = _currentTrackId.value
        val startTime = getStartOfPeriod(activeTrackId)
        val now = Instant.now()
        val steps = healthConnectManager.readStepsByTimeRange(startTime, now)
        return if (steps < 0) 0L else steps
    }
    
    /**
     * 🆕 P0 FIX: Ensure track is ALWAYS set
     * Yeni kullanıcılar için default track atar
     */
    // 🆕 Phase 5: Rapid Polling (Live Map)
    private var rapidPollingJob: kotlinx.coroutines.Job? = null
    
    override fun startRapidPolling() {
        if (rapidPollingJob?.isActive == true) return
        
        android.util.Log.i("StepRepository", "🏎️ STARTING RAPID POLLING (Live Race Mode)")
        rapidPollingJob = repositoryScope.launch {
            while (isActive) {
                try {
                    // Force = false (Cloud Sync cost-saving logic is ACTIVE)
                    // But Local DB update happens every 5s
                    syncHealthConnectSteps(force = false)
                } catch (e: Exception) {
                    android.util.Log.e("StepRepository", "Rapid poll error: ${e.message}")
                }
                kotlinx.coroutines.delay(5000) // 5 Saniye
            }
        }
    }
    
    override fun stopRapidPolling() {
        android.util.Log.i("StepRepository", "🛑 STOPPING RAPID POLLING")
        rapidPollingJob?.cancel()
        rapidPollingJob = null
    }

    private fun isAppInForeground(): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false
        val packageName = context.packageName
        for (appProcess in appProcesses) {
            if (appProcess.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                appProcess.processName == packageName) {
                return true
            }
        }
        return false
    }


    override suspend fun getCurrentOrSetDefaultTrack(): String {
        val current = _currentTrackId.value
        if (current != null && current.isNotEmpty()) return current
        
        val saved = prefs.getString("active_track_id", null)
        if (!saved.isNullOrEmpty()) {
            _currentTrackId.value = saved
            return saved
        }
        
        // Firestore check
        val userId = getUserId()
        if (userId != null) {
            try {
                val userDoc = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users").document(userId).get().await()
                val firebaseTrack = userDoc.getString("activeTrackId")
                if (!firebaseTrack.isNullOrEmpty()) {
                    _currentTrackId.value = firebaseTrack
                    prefs.edit().putString("active_track_id", firebaseTrack).apply()
                    return firebaseTrack
                }
            } catch (e: Exception) {
                android.util.Log.w("StepRepository", "Firestore fetch error: ${e.message}")
            }
        }
        
        // Default fallback
        val defaultTrack = "istanbul_park"
        android.util.Log.w("StepRepository", "⚠️ Setting default track: $defaultTrack")
        selectTrackForMonth(defaultTrack)
        return defaultTrack
    }
}
