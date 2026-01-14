package com.pace.legends.domain.manager

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.pace.legends.domain.repository.AuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import com.pace.legends.domain.repository.LeaderboardRepository
import com.pace.legends.domain.repository.TrackRepository
import com.pace.legends.domain.manager.BadgeManager

/**
 * Yarışma Senkronizasyon Yöneticisi
 * 
 * Firestore yazımlarını optimize eder:
 * - Her 500 adımda bir sync
 * - Her 15 dakikada bir sync
 * - Pist değişiminde anında sync
 * - Uygulama arka plana geçtiğinde sync
 * 
 * 🆕 Dinamik Yarışma Süresi:
 * - Firebase Remote Config'den race_duration_days okunur
 * - Başlangıç tarihinden itibaren dönemler hesaplanır
 */
@Singleton
class StepSyncManager @Inject constructor(
    private val firestore: FirebaseFirestore,
    @ApplicationContext private val context: Context,
    private val appDatabase: com.pace.legends.data.local.AppDatabase,
    private val authRepository: AuthRepository,
    private val remoteConfigManager: RemoteConfigManager,
    private val healthConnectManager: HealthConnectManager,
    private val leaderboardRepository: LeaderboardRepository,
    private val functions: com.google.firebase.functions.FirebaseFunctions,
    private val raceLocationManager: RaceLocationManager,
    @com.pace.legends.di.ApplicationScope private val externalScope: kotlinx.coroutines.CoroutineScope,
    private val trackRepository: TrackRepository,
    private val badgeManager: BadgeManager,
    private val leagueManagerLazy: dagger.Lazy<LeagueManager>,
    private val leagueRepository: com.pace.legends.domain.repository.LeagueRepository,
    // 🆕 GOD OBJECT FIX: Period hesaplama ayrı sınıfa delegasyon
    // 🆕 P3: Delegate to UseCase
    private val syncStepsUseCase: com.pace.legends.domain.usecase.sync.SyncStepsUseCase
) {
    
    // 🆕 Lig değişikliği eventi (UI kutlama için)
    data class LeagueChangeEvent(
        val previousTier: String,
        val newTier: String,
        val isPromotion: Boolean
    )
    private val _leagueChangeEvents = kotlinx.coroutines.flow.MutableSharedFlow<LeagueChangeEvent>()
    val leagueChangeEvents: kotlinx.coroutines.flow.SharedFlow<LeagueChangeEvent> = _leagueChangeEvents
    
    private val prefs: SharedPreferences = context.getSharedPreferences("step_sync", Context.MODE_PRIVATE)
    
    // P0 FIX: Race condition önlemek için Mutex
    private val syncMutex = Mutex()
    
    // Throttling sabitleri
    companion object {
        private const val SYNC_INTERVAL_MS = 15 * 60 * 1000L  // 15 dakika
        private const val STEP_MILESTONE = 500L               // 💰 Her 500 adım (maliyet dengesi)
        private const val PREF_LAST_SYNCED_STEPS = "last_synced_steps"
        private const val PREF_LAST_SYNC_TIME = "last_sync_time"
        private const val PREF_MONTHLY_OFFSET = "monthly_offset"
        private const val PREF_CURRENT_MONTH = "current_month"
    }

    // ... [Properties and Methods for Period Calculation remain the same] ...

    /**
     * Throttled sync kontrolü
     * P0 FIX: Mutex ile atomik kontrol ve güncelleme
     * @return SyncResult indicating outcome
     */
    suspend fun syncIfNeeded(
        monthlySteps: Long,
        activeTrackId: String?,
        force: Boolean = false
    ): com.pace.legends.domain.usecase.sync.SyncResult = syncMutex.withLock {
        // 🆕 P3: Use SyncStepsUseCase for unified logic
        val result = syncStepsUseCase(
            monthlySteps = monthlySteps,
            activeTrackId = activeTrackId,
            force = force
        )
        
        // Log outcome
        when (result) {
            is com.pace.legends.domain.usecase.sync.SyncResult.Success -> {
                android.util.Log.d("StepSync", "✅ Sync success: ${result.syncedSteps} steps")
            }
            is com.pace.legends.domain.usecase.sync.SyncResult.Failed -> {
                android.util.Log.e("StepSync", "❌ Sync failed: ${result.error}")
            }
            is com.pace.legends.domain.usecase.sync.SyncResult.Throttled -> {
                 android.util.Log.v("StepSync", "⏸️ Sync throttled")
            }
            else -> { /* Skipped or Cheat */ }
        }
        
        return@withLock result
    }
    
    /**
     * 🆕 Mevcut yarışma dönemini hesapla
     * 
     * Firebase Remote Config'den alınan race_duration_days ve race_start_date'e göre
     * mevcut dönemi "period_X" formatında döndürür.
     * 
     * Örnek:
     * - Başlangıç: 2026-01-01, Süre: 7 gün
     * - 2026-01-01 ~ 2026-01-07 = "period_0"
     * - 2026-01-08 ~ 2026-01-14 = "period_1"
     */
    /**
     * 🆕 Period Info Wrapper
     */
    data class PeriodInfo(
        val periodId: String,           // "period_5"
        val displayName: String,        // "Sevgililer Günü Sprintu"
        val description: String,
        val emoji: String,
        val startDate: LocalDate,
        val endDate: LocalDate,
        val daysRemaining: Int,
        val hoursRemaining: Int,
        val remainingTimeDisplay: String, // 🆕 Formatted string
        val isLastDay: Boolean,
        val isExpired: Boolean
    ) {
        companion object {
            fun empty() = PeriodInfo(
                "", "", "", "", LocalDate.MIN, LocalDate.MIN, 0, 0, "", false, false
            )
        }
    }

    /**
     * 🆕 Mevcut yarışma dönemini detaylı hesapla (Remote Config Tabanlı)
     */
    fun getCurrentPeriodInfo(): PeriodInfo {
        return try {
            val durationDays = remoteConfigManager.raceDurationDays.value
            val startDateStr = remoteConfigManager.raceStartDate.value

            // 🆕 OTOMATİK MOD: Eğer Remote Config "AUTO" dönerse, Takvim Ayını kullan
            if (startDateStr.equals("AUTO", ignoreCase = true)) {
                val now = LocalDate.now()
                val yearMonth = YearMonth.from(now)
                
                val startDate = yearMonth.atDay(1)
                val endDate = yearMonth.atEndOfMonth() // Ayın son gününü (28, 30, 31) otomatik bulur
                
                // Period ID: YYYY_MM (Örn: 2026_01) - Her ay benzersiz olur
                val periodId = yearMonth.format(DateTimeFormatter.ofPattern("yyyy_MM"))
                
                // Kalan Süre Hesabı
                val nowInstant = java.time.Instant.now()
                val raceEndDateTime = endDate.atStartOfDay(java.time.ZoneId.systemDefault()).plusDays(1) // Bitiş: Ertesi ayın ilk anı
                val duration = java.time.Duration.between(nowInstant, raceEndDateTime.toInstant())
                
                val totalHoursRemaining = duration.toHours()
                val totalMinutesRemaining = duration.toMinutes()
                
                val displayText = when {
                     totalMinutesRemaining <= 0L -> "Dönem Tamamlandı"
                     totalHoursRemaining < 24 -> "${totalHoursRemaining} saat kaldı"
                     else -> "${totalHoursRemaining / 24} gün kaldı"
                }

                return PeriodInfo(
                    periodId = periodId,
                    displayName = remoteConfigManager.raceDisplayName.value.ifEmpty { "${yearMonth.monthValue}. Dönem" },
                    description = remoteConfigManager.raceDescription.value,
                    emoji = remoteConfigManager.raceEmoji.value,
                    startDate = startDate,
                    endDate = endDate,
                    daysRemaining = (totalHoursRemaining / 24).toInt(),
                    hoursRemaining = (totalHoursRemaining % 24).toInt(),
                    remainingTimeDisplay = displayText,
                    isLastDay = totalHoursRemaining in 0L..23L,
                    isExpired = totalMinutesRemaining <= 0L
                )
            }
            
            // Log if defaults are being used (Duration 30, Start 2026-01-01)
            if (durationDays == RemoteConfigManager.DEFAULT_RACE_DURATION_DAYS && 
                startDateStr == RemoteConfigManager.DEFAULT_RACE_START_DATE) {
                android.util.Log.i("StepSync", "ℹ️ Using default race configuration (Remote Config might not be fetched yet)")
            }

            val displayName = remoteConfigManager.raceDisplayName.value
            val description = remoteConfigManager.raceDescription.value
            val emoji = remoteConfigManager.raceEmoji.value
            
            val startDate = LocalDate.parse(startDateStr, DateTimeFormatter.ISO_LOCAL_DATE)
            val today = LocalDate.now()
            val now = java.time.ZonedDateTime.now() // Use ZonedDateTime for accuracy
            
            // 🔄 P1 FIX: Yarış henüz başlamadıysa özel durum döndür
            if (today.isBefore(startDate)) {
                val daysUntilStart = java.time.temporal.ChronoUnit.DAYS.between(today, startDate).toInt()
                return PeriodInfo(
                    periodId = "upcoming",
                    displayName = "Yakında Başlıyor",
                    description = description,
                    emoji = "⏳",
                    startDate = startDate,
                    endDate = startDate.plusDays(durationDays.toLong()),
                    daysRemaining = daysUntilStart,
                    hoursRemaining = 0,
                    remainingTimeDisplay = "$daysUntilStart gün sonra başlıyor",
                    isLastDay = false,
                    isExpired = false
                )
            }
            
            // Period hesaplama
            val daysSinceStart = java.time.temporal.ChronoUnit.DAYS.between(startDate, today)
            val periodNumber = (daysSinceStart / durationDays).toInt().coerceAtLeast(0)
            
            // Bu period'un başlangıç ve bitiş tarihleri
            val periodStartDate = startDate.plusDays((periodNumber * durationDays).toLong())
            val periodEndDate = periodStartDate.plusDays(durationDays.toLong())
            
            // P1 FIX: High Precision Remaining Time Calculation
            val raceEndDateTime = periodEndDate.atStartOfDay(java.time.ZoneId.systemDefault())
            val duration = java.time.Duration.between(now, raceEndDateTime)
            val totalMinutesRemaining = duration.toMinutes()
            val totalHoursRemaining = duration.toHours()
            
            val displayText = when {
                totalMinutesRemaining <= 0 -> "Dönem bitti"
                totalMinutesRemaining < 60 -> "$totalMinutesRemaining dakika kaldı"
                totalHoursRemaining < 24 -> "$totalHoursRemaining saat kaldı"
                else -> "${totalHoursRemaining / 24} gün kaldı"
            }
            
            val daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(today, periodEndDate).toInt()
            val hoursRemaining = (totalHoursRemaining % 24).toInt()
            
            PeriodInfo(
                periodId = "period_$periodNumber",
                displayName = displayName,
                description = description,
                emoji = emoji,
                startDate = periodStartDate,
                endDate = periodEndDate,
                daysRemaining = daysRemaining,
                hoursRemaining = hoursRemaining,
                remainingTimeDisplay = displayText,
                isLastDay = totalHoursRemaining in 0..23,
                isExpired = totalMinutesRemaining <= 0
            )
        } catch (e: Exception) {
            // Fallback to strict monthly mode if everything else fails
            android.util.Log.e("StepSync", "❌ Error calculating period info: ${e.message}")
            val now = LocalDate.now()
            PeriodInfo.empty().copy(
                periodId = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM")),
                displayName = "Aylık Mod (Sistem Hatası)",
                startDate = now.withDayOfMonth(1),
                endDate = now.withDayOfMonth(1).plusMonths(1),
                remainingTimeDisplay = "Hata"
            )
        }
    }

    /**
     * 🆕 Mevcut yarışma dönemini hesapla (Basit ID dönen eski method, yeni yapıya uyarlandı)
     */
    fun getCurrentPeriod(): String {
        return getCurrentPeriodInfo().periodId
    }

    /**
     * Mevcut ayı "2026-01" formatında döndürür (Eski - Backward compatibility)
     */
    @Deprecated("Use getCurrentPeriod() instead")
    fun getCurrentMonth(): String {
        return getCurrentPeriod()
    }
    
    /**
     * Dönem değişimi kontrolü - Yeni dönem başladıysa offset'i sıfırla
     * VE Eski dönemi arşivle.
     */
    suspend fun checkPeriodReset(currentSensorValue: Long): Long {
        val currentPeriod = getCurrentPeriod()
        val savedPeriod = prefs.getString(PREF_CURRENT_MONTH, "") ?: ""
        
        // Önemli: Period geçişini yönet
        if (savedPeriod.isNotEmpty() && savedPeriod != currentPeriod) {
            checkAndHandlePeriodTransition(savedPeriod, currentPeriod)
        }

        return if (currentPeriod != savedPeriod) {
            // Yeni dönem! Offset'i güncelle
            android.util.Log.d("StepSync", "📅 New period detected: $savedPeriod -> $currentPeriod")
            prefs.edit()
                .putString(PREF_CURRENT_MONTH, currentPeriod)
                .putLong(PREF_MONTHLY_OFFSET, currentSensorValue)
                .putLong(PREF_LAST_SYNCED_STEPS, 0) // Sıfırla
                // Yeni period başlangıç zamanını kaydet (Snapshot için)
                .putLong("period_start_timestamp", System.currentTimeMillis())
                .apply()
            currentSensorValue
        } else {
            prefs.getLong(PREF_MONTHLY_OFFSET, currentSensorValue)
        }
    }

    /**
     * 🆕 Period değişimi kontrolü ve arşivleme
     */
    private suspend fun checkAndHandlePeriodTransition(oldPeriodId: String, newPeriodId: String) {
        android.util.Log.i("StepSync", "🏁 Period Ending: $oldPeriodId. Starting: $newPeriodId")
        archiveCompletedPeriod(oldPeriodId)
    }

    /**
     * 🆕 PUBLIC: Dönem geçişi kontrolü (Worker'lar için)
     * 
     * Bu metod arka plan Worker'lar tarafından çağrılabilir.
     * Mevcut dönemi kontrol eder, eğer değişmişse arşivleme ve lig işlemlerini tetikler.
     * 
     * @return true if period changed and transition was processed
     */
    suspend fun checkForPeriodTransition(): Boolean {
        val currentPeriod = getCurrentPeriod()
        val savedPeriod = prefs.getString(PREF_CURRENT_MONTH, "") ?: ""
        
        if (savedPeriod.isNotEmpty() && savedPeriod != currentPeriod) {
            android.util.Log.i("StepSync", "🔔 [Worker] Period transition detected: $savedPeriod -> $currentPeriod")
            
            // Dönem geçişini işle
            checkAndHandlePeriodTransition(savedPeriod, currentPeriod)
            
            // Mevcut dönemi kaydet
            prefs.edit().putString(PREF_CURRENT_MONTH, currentPeriod).apply()
            
            return true
        }
        
        // Eğer henüz hiç kaydedilmemişse, şu anki dönemi kaydet
        if (savedPeriod.isEmpty()) {
            prefs.edit().putString(PREF_CURRENT_MONTH, currentPeriod).apply()
            android.util.Log.d("StepSync", "📝 Initial period saved: $currentPeriod")
        }
        
        return false
    }

    /**
     * 🆕 Tamamlanan period'u arşivle
     */
    /**
     * 🆕 Tamamlanan period'u arşivle
     * 
     * ARCHITECTURE CHANGE: 
     * We no longer accumulate "All Time" stats locally in UserProgress (allTimeSteps, etc.).
     * Instead, we push the completed period to Firestore "periodHistory".
     * StatsRepository will calculate "All Time" by summing Firestore History + Current Period.
     */
    suspend fun archiveCompletedPeriod(oldPeriodId: String) {  // 🔄 Made public
        val userId = authRepository.getCurrentUserId() ?: return
        
        // Aktif track'i bul
        val activeTrackId = prefs.getString("active_track_id", null)
        if (activeTrackId == null) return

        val dao = appDatabase.userProgressDao()
        val currentProgress = dao.getProgressByTrack(userId, activeTrackId) ?: return

        try {
            // 🆕 P0 FIX: Lig bilgisini ÖNCE al (processEndOfPeriod değiştirebilir)
            val currentLeagueInfo = try {
                leagueRepository.getUserLeagueInfo(userId)
            } catch (e: Exception) {
                android.util.Log.w("StepSync", "⚠️ Lig bilgisi alınamadı: ${e.message}")
                null
            }
            val currentLeagueTier = currentLeagueInfo?.tier?.name ?: "QUALIFYING"
            val currentLeagueId = currentLeagueInfo?.leagueId
            
            android.util.Log.d("StepSync", "🏁 Dönem sonu lig durumu: $currentLeagueTier (League: $currentLeagueId)")
            
            // 0. Sıralama ve katılımcı sayısını al (Ölçeklenebilir)
            val mySteps = currentProgress.totalSteps
            
            // 🆕 ÖLÇEKLENEBİLİR SIRALAMA: Top 100 limitine takılma
            val myRank = leaderboardRepository.getUserRankBySteps(activeTrackId, oldPeriodId, mySteps)
            val totalParticipants = leaderboardRepository.getTotalParticipants(activeTrackId, oldPeriodId)
            
            // 🆕 Güvenlik kontrolü: Şüpheli sıralama durumu logla
            if (myRank == 1 && totalParticipants <= 1 && mySteps > 0) {
                android.util.Log.w("StepSync", "⚠️ Şüpheli sıralama: Tek katılımcı gibi görünüyor. Leaderboard sync gecikmeli olabilir.")
            }
            
            android.util.Log.d("StepSync", "📊 User rank: $myRank / $totalParticipants (Steps: $mySteps)")

            // Dönem bilgisini al (display name için)
            val periodInfo = getCurrentPeriodInfo()
            val periodDisplayName = remoteConfigManager.raceDisplayName.value.ifEmpty { "Dönem $oldPeriodId" }

            // 1. Firestore'a kaydet (TEK KAYNAK - Source of Truth for History)
            val periodData = mutableMapOf<String, Any?>(
                "periodId" to oldPeriodId,
                "periodDisplayName" to periodDisplayName, // 🆕 Görünen isim
                "trackId" to activeTrackId,
                "totalSteps" to currentProgress.totalSteps,
                "completedLaps" to currentProgress.completedLoops,
                "totalDistance" to currentProgress.totalDistanceWalked,
                "startDate" to (prefs.getLong("period_start_timestamp", 0)), 
                "endTimestamp" to FieldValue.serverTimestamp(), // 🔒 SECURITY FIX: Use server time
                // 🆕 Final Rank
                "finalRank" to myRank,
                "totalParticipants" to totalParticipants,
                // 🆕 P0 FIX: Lig bilgisi eklendi
                "leagueTier" to currentLeagueTier,
                "leagueId" to currentLeagueId
            )

            firestore
                .collection("users")
                .document(userId)
                .collection("periodHistory")
                .document(oldPeriodId)
                .set(periodData, SetOptions.merge()) // Idempotent / Duplicate Safe
                .await()
                
            android.util.Log.d("StepSync", "📦 Period archived to Firestore: $oldPeriodId (Rank: $myRank)")

            // 🆕 ŞAMPİYONLUK ROZET KONTROLÜ
            if (myRank != null && myRank <= 3) {
                awardChampionBadge(oldPeriodId, activeTrackId, myRank)
            }

            // 🆕 KRİTİK: LİG YÜKSELME/DÜŞME İŞLEMİ
            try {
                val promotionResult = leagueManagerLazy.get().processEndOfPeriod(userId, oldPeriodId, myRank ?: 999)
                if (promotionResult != null) {
                    if (promotionResult.isPromotion) {
                        android.util.Log.i("StepSync", "🎉 YÜKSELME: ${promotionResult.previousTier} -> ${promotionResult.newTier}")
                    } else if (promotionResult.isDemotion) {
                        android.util.Log.i("StepSync", "📉 DÜŞME: ${promotionResult.previousTier} -> ${promotionResult.newTier}")
                    }
                    
                    // 🆕 UI'a lig değişikliği bildirimi (Kutlama ekranı için)
                    _leagueChangeEvents.emit(LeagueChangeEvent(
                        previousTier = promotionResult.previousTier.name,
                        newTier = promotionResult.newTier.name,
                        isPromotion = promotionResult.isPromotion
                    ))
                }
            } catch (e: Exception) {
                android.util.Log.e("StepSync", "❌ Lig işlemi hatası: ${e.message}")
            }

            // ✅ ARŞİV BAŞARILI - Şimdi lokal DB'yi sıfırla
            // 2. Lokal DB'yi sadece MEVCUT DÖNEM için sıfırla
            // AllTime HESAPLANMIYOR, Firestore'dan geliyor.
            
            // P0 FIX: Use actual new period start time for consistency with sync logic
            val newPeriodInfo = getCurrentPeriodInfo()
            val newPeriodStartTime = newPeriodInfo.startDate
                .atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            
            val resetProgress = currentProgress.copy(
                totalSteps = 0,
                completedLoops = 0,
                currentLapNumber = 1,
                totalDistanceWalked = 0.0,
                
                // Period başlangıcını resetle - ACTUAL period start, not current time
                currentLapStartTime = System.currentTimeMillis(),
                periodStartTime = newPeriodStartTime // P0 FIX
            )
            
            dao.upsertProgress(resetProgress)
            android.util.Log.d("StepSync", "✅ Local period progress reset for new period.")
            
        } catch (e: Exception) {
            // 🛑 VERİ KAYBI ÖNLEMİ: Arşiv başarısız olursa lokal veriyi SİLME!
            android.util.Log.e("StepSync", "❌ Failed to archive period to Firestore: ${e.message}")
            android.util.Log.w("StepSync", "⚠️ Local data preserved - will retry on next sync")
            // Lokal veri korundu, bir sonraki sync'te tekrar denenecek
            return
        }
    }
    
    /**
     * 🆕 Şampiyonluk Rozeti Ver
     */
    private suspend fun awardChampionBadge(periodId: String, trackId: String, rank: Int) {
        val userId = authRepository.getCurrentUserId() ?: return
        
        try {
            // Get period display name from saved prefs or generate
            val periodDisplayName = prefs.getString("period_display_name", null) ?: "Dönem $periodId"
            
            val track = trackRepository.getTrack(trackId)
            val trackName = track?.genericName?.get("tr") ?: track?.genericName?.get("en") ?: trackId
            
            val badgeId = "${periodId}_${trackId}_rank_$rank"
            
            val badge = mapOf(
                "badgeId" to badgeId,
                "periodId" to periodId,
                "periodDisplayName" to periodDisplayName,
                "trackId" to trackId,
                "trackDisplayName" to trackName,
                "rank" to rank,
                "earnedAt" to System.currentTimeMillis()
            )
            
            firestore
                .collection("users")
                .document(userId)
                .collection("championBadges")
                .document(badgeId)
                .set(badge)
                .await()
            
            // Save to local Room DB as well for offline access
            val badgeType = if (rank == 1) {
                com.pace.legends.domain.model.BadgeType.PERIOD_CHAMPION
            } else {
                com.pace.legends.domain.model.BadgeType.PODIUM_FINISH
            }
            
            // Emit UI event for snackbar/notification
            badgeManager.emitChampionBadge(badgeType, trackName, rank)
            
            android.util.Log.d("StepSync", "🏆 Champion Badge Awarded! Track: $trackName, Rank: $rank")

        } catch (e: Exception) {
            android.util.Log.e("StepSync", "❌ Failed to award champion badge: ${e.message}")
        }
    }
    
    /**
     * Ay değişimi kontrolü - Eski (Backward compatibility)
     */
    @Deprecated("Use checkPeriodReset() instead")
    suspend fun checkMonthlyReset(currentSensorValue: Long): Long {
        return checkPeriodReset(currentSensorValue)
    }
    
    /**
     * Dönem adım hesaplama
     */
    suspend fun calculateMonthlySteps(currentSensorValue: Long): Long {
        val offset = checkPeriodReset(currentSensorValue)
        return (currentSensorValue - offset).coerceAtLeast(0)
    }
    
    /**
     * Throttled sync kontrolü
     * P0 FIX: Mutex ile atomik kontrol ve güncelleme
     * @return true if sync was performed
     */
    suspend fun syncIfNeeded(
        monthlySteps: Long,
        activeTrackId: String?,
        force: Boolean = false
    ): Boolean = syncMutex.withLock {
        val userId = authRepository.getCurrentUserId()
        if (userId == null) {
            android.util.Log.w("StepSync", "❌ Sync skipped: No user ID")
            return@withLock false
        }
        if (activeTrackId == null) {
            android.util.Log.w("StepSync", "❌ Sync skipped: No active track")
            return@withLock false
        }
        
        val now = System.currentTimeMillis()
        val stepDelta = monthlySteps - lastSyncedSteps
        val timeDelta = now - lastSyncTime
        
        // 🆕 Adaptive Throttling: Eşik Remote Config'den okunuyor (Maliyet optimizasyonu)
        val dynamicMilestone = remoteConfigManager.getStepSyncMilestone()
        
        val shouldSync = force ||
            stepDelta >= dynamicMilestone ||
            timeDelta >= SYNC_INTERVAL_MS
        
        if (!shouldSync) {
            // 🐛 DEBUG: Log every check (verbose mode for testing)
            android.util.Log.d("StepSync", "⏸️ Sync throttled: delta=$stepDelta (need $dynamicMilestone), time=${timeDelta/1000}s (need ${SYNC_INTERVAL_MS/1000}s)")
            return@withLock false
        }
        
        android.util.Log.d("StepSync", "🚀 Triggering sync: $monthlySteps steps to $activeTrackId")
        
        // 🛡️ ANTI-CHEAT: Speed Limit Check (25 km/h)
        if (checkSpeedViolation(stepDelta, timeDelta)) {
            android.util.Log.w("StepSync", "🚨 SPEED VIOLATION DETECTED! Sync rejected.")
            logSpeedCheatAttempt(userId, stepDelta, timeDelta)
            // Revert local state to match last sync? Or just skip sync.
            // For now, skipping sync is safer to avoid looping.
            return@withLock false
        }

    
    /**
     * 🆕 TRACK LOCK-IN: Ayın başında pist seçimi
     * 
     * Kullanıcı bir pist seçtiğinde, ay sonuna kadar kilitlenir.
     * Ay içinde pist değişikliği YASAKTIR.
     * 
     * @throws TrackLockedException Eğer kullanıcı bu ay zaten pist seçmişse
     */
    /**
     * 🆕 TRACK LOCK-IN: Ayın başında pist seçimi
     * 
     * Kullanıcı bir pist seçtiğinde, ay sonuna kadar kilitlenir.
     * Ay içinde pist değişikliği YASAKTIR.
     * 
     * P1 FIX: Firestore Transaction ile Race Condition önlendi.
     * 
     * @throws TrackLockedException Eğer kullanıcı bu ay zaten pist seçmişse
     */
    suspend fun selectTrackForMonth(trackId: String): Result<Unit> {
        val userId = authRepository.getCurrentUserId() ?: return Result.failure(Exception("Kullanıcı giriş yapmamış"))
        val currentMonth = getCurrentPeriod() // P3: Use getCurrentPeriod
        
        return try {
            // P1 FIX: Transaction ile Atomik İşlem
            firestore.runTransaction { transaction ->
                val userRef = firestore.collection("users").document(userId)
                val snapshot = transaction.get(userRef)
                
                val lockedMonth = snapshot.getString("lockedMonth")
                val currentTrackId = snapshot.getString("activeTrackId")
                
                // 🔒 ATOMIC CHECK - Sadece FARKLI bir track seçmeye çalışırsa hata ver
                // Aynı track'e tekrar giriliyorsa sorun yok
                if (lockedMonth == currentMonth && currentTrackId != null && currentTrackId != trackId) {
                    throw TrackLockedException(
                        "Bu ay zaten '$currentTrackId' pistini seçtiniz. Yeni ay başlayana kadar değiştiremezsiniz."
                    )
                }
                
                // Eğer zaten aynı track seçiliyse, tekrar yazmaya gerek yok (early return)
                if (lockedMonth == currentMonth && currentTrackId == trackId) {
                    return@runTransaction // Zaten doğru track seçili, işlem başarılı
                }
                
                // ✅ ATOMIC WRITE (same transaction)
                transaction.set(userRef, mapOf(
                    "activeTrackId" to trackId,
                    "lockedMonth" to currentMonth,
                    "lastSyncTimestamp" to System.currentTimeMillis()
                ), SetOptions.merge())
            }.await()
            
            // Leaderboard'a kayıt oluştur (Transaction dışında yapılabilir, kritik değil)
            createLeaderboardEntry(userId, trackId, currentMonth)
            
            android.util.Log.d("StepSync", "✅ Track selected and locked (Atomic): $trackId for $currentMonth")
            Result.success(Unit)
            
        } catch (e: TrackLockedException) {
            android.util.Log.w("StepSync", "🔒 Selection rejected: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            android.util.Log.e("StepSync", "❌ Track selection failed: ${e.message}")
            Result.failure(e)
        }
    }
    
    private suspend fun createLeaderboardEntry(userId: String, trackId: String, month: String) {
        try {
            val leaderboardRef = firestore
                .collection("leaderboards")
                .document(trackId)
                .collection("monthly")
                .document(month)
                .collection("entries")
                .document(userId)
            
            val displayName = authRepository.getCurrentUser()?.displayName ?: "Racer ${userId.take(4)}"
            
            leaderboardRef.set(mapOf(
                "userId" to userId,
                "displayName" to displayName,
                "steps" to 0L,
                "lastUpdated" to FieldValue.serverTimestamp()
            ), SetOptions.merge()).await()
        } catch (e: Exception) {
             android.util.Log.w("StepSync", "Leaderboard entry init failed: ${e.message}")
        }
    }
    
    /**
     * Mevcut lock durumunu kontrol et
     * P2 FIX: Network hatalarını "kilit yok" olarak yutma!
     */
    /**
     * 🆕 SİSTEM ATAMASI: Lig değişikliği sonrası pist güncelleme
     * Kilit (Lock) kontrolünü bypass eder.
     */
    suspend fun forceUpdateActiveTrack(trackId: String) {
        val userId = authRepository.getCurrentUserId() ?: return
        val currentMonth = getCurrentPeriod() 
        
        try {
            // 1. Firestore'u güncelle (Kilitli ayı da güncelle)
            firestore.collection("users").document(userId).set(mapOf(
                "activeTrackId" to trackId,
                "lockedMonth" to currentMonth,
                "lastSyncTimestamp" to System.currentTimeMillis()
            ), SetOptions.merge()).await()
            
            // 2. Lokal SharedPreferences'ı güncelle
            prefs.edit()
                .putString("active_track_id", trackId)
                .apply()
                
            android.util.Log.i("StepSync", "🔄 Track FORCE updated to: $trackId (League Change)")
            
        } catch (e: Exception) {
            android.util.Log.e("StepSync", "❌ Force track update failed: ${e.message}")
        }
    }

    suspend fun checkLockStatus(): com.pace.legends.domain.model.LockStatusResult {
        val userId = authRepository.getCurrentUserId() ?: return com.pace.legends.domain.model.LockStatusResult.NotLocked
        
        return try {
            val userDoc = firestore.collection("users").document(userId).get().await()
            val lockedMonth = userDoc.getString("lockedMonth")
            val currentTrackId = userDoc.getString("activeTrackId")
            
            val currentPeriod = getCurrentPeriod()
            
            if (lockedMonth == currentPeriod && currentTrackId != null) {
                com.pace.legends.domain.model.LockStatusResult.Locked(currentTrackId, lockedMonth)
            } else {
                com.pace.legends.domain.model.LockStatusResult.NotLocked
            }
        } catch (e: Exception) {
            com.pace.legends.domain.model.LockStatusResult.Error(e) // Eksplicit hata
        }
    }
    
    /**
     * Yeni ay başladı mı kontrol et (Lock reset)
     */
    fun isNewMonthStarted(lockedMonth: String?): Boolean {
        if (lockedMonth == null) return true
        return lockedMonth != getCurrentPeriod() // P3 FIX
    }
}
