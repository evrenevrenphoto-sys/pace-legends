package com.pace.legends.domain.manager

import com.pace.legends.data.local.UserBadgeDao
import com.pace.legends.domain.model.BadgeType
import com.pace.legends.domain.model.UserBadge
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BadgeManager @Inject constructor(
    private val badgeDao: UserBadgeDao,
    // 🆕 Injected for Champion Badge Awarding
    private val firestore: com.google.firebase.firestore.FirebaseFirestore,
    private val authRepository: com.pace.legends.domain.repository.AuthRepository,
    private val trackRepository: com.pace.legends.domain.repository.TrackRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) {
    private val prefs: android.content.SharedPreferences by lazy {
        context.getSharedPreferences("step_sync_secure", android.content.Context.MODE_PRIVATE)
    }

    companion object {
        private const val TAG = "BadgeManager"
    }
    // P3 FIX: Dead code (scope) removed
    
    // UI'ın dinlemesi için event flow (Snackbar vb. için)
    // P1 FIX: Deadlock prevention via Buffer & DROP_OLDEST
    private val _badgeEarnedEvents = MutableSharedFlow<BadgeType>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val badgeEarnedEvents = _badgeEarnedEvents.asSharedFlow()

    // P2 FIX: Race Condition prevention
    private val badgeMutex = Mutex()

    suspend fun checkBadges(
        totalSteps: Long,
        totalDistanceMeters: Double,
        completedLoops: Int,
        sessionSteps: Int
    ) {
        // 1. Formasyon Turu (İlk Adım)
        if (totalSteps > 0) {
            checkAndEarn(BadgeType.FORMATION_LAP)
        }
        
        // 2. Damalı Bayrak (İlk Tur)
        if (completedLoops >= 1) {
            checkAndEarn(BadgeType.CHECKERED_FLAG)
        }
        
        // 3. Gece Yarışı (22:00 - 05:00 arası ve > 1000 session adımı)
        if (sessionSteps > 1000) {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (hour >= 22 || hour < 5) {
                checkAndEarn(BadgeType.NIGHT_RACE)
            }
        }
        
        // 4. Dayanıklılık Pilotu (42.195 metre)
        if (totalDistanceMeters >= 42195) {
            checkAndEarn(BadgeType.ENDURANCE_PILOT)
        }
    }
    
    private suspend fun checkAndEarn(type: BadgeType) = badgeMutex.withLock {
        // Double-check locking pattern with Mutex
        if (!badgeDao.hasBadge(type.id)) {
            val badge = UserBadge(
                badgeId = type.id,
                earnedTimestamp = System.currentTimeMillis()
            )
            badgeDao.insertBadge(badge)
            
            // Artık güvenli - deadlock yok
            _badgeEarnedEvents.emit(type)
        }
    }
    /**
     * 🆕 Harici olarak tetiklenen şampiyonluk rozeti (StepSyncManager kullanır)
     * 
     * @param type BadgeType (PERIOD_CHAMPION veya PODIUM_FINISH)
     * @param trackName Pistin adı (UI'da gösterilecek)
     * @param rank Sıralama (1, 2, veya 3)
     */
    /**
     * 🆕 Harici olarak tetiklenen şampiyonluk rozeti (StepSyncManager kullanır)
     * 
     * Firestore'a kaydeder VE UI event tetikler.
     */
    suspend fun awardChampionBadge(
        periodId: String,
        trackId: String,
        rank: Int
    ): Result<Unit> = badgeMutex.withLock {
        val userId = authRepository.getCurrentUserId() 
            ?: return Result.failure(Exception("User not logged in"))
            
        return try {
            // Get period display name from saved prefs or generate
            val periodDisplayName = prefs.getString("period_display_name", null) ?: "Dönem $periodId"
            
            val track = trackRepository.getTrack(trackId).getOrNull()
            val trackName = track?.genericName?.get("tr") ?: track?.genericName?.get("en") ?: trackId
            
            val badgeId = "${periodId}_${trackId}_rank_$rank"
            
            val badgeInfo = mapOf(
                "badgeId" to badgeId,
                "periodId" to periodId,
                "periodDisplayName" to periodDisplayName,
                "trackId" to trackId,
                "trackDisplayName" to trackName,
                "rank" to rank,
                "earnedAt" to System.currentTimeMillis()
            )
            
            // 1. Firestore Write
            firestore
                .collection("users")
                .document(userId)
                .collection("championBadges")
                .document(badgeId)
                .set(badgeInfo)
                .await()
            
            // 2. Local Room DB Write (Generic Type)
            val badgeType = if (rank == 1) {
                BadgeType.PERIOD_CHAMPION
            } else {
                BadgeType.PODIUM_FINISH
            }
            
            if (!badgeDao.hasBadge(badgeType.id)) {
                val badge = UserBadge(
                    badgeId = badgeType.id,
                    earnedTimestamp = System.currentTimeMillis()
                )
                badgeDao.insertBadge(badge)
            }
            
            // 3. UI Event
            _badgeEarnedEvents.emit(badgeType)
            
            android.util.Log.d(TAG, "🏆 Champion Badge Awarded! Track: $trackName, Rank: $rank")
            Result.success(Unit)
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to award champion badge: ${e.message}")
            Result.failure(e)
        }
    }
    
    // Legacy support or internal use if needed - can be removed if unused
    suspend fun emitChampionBadge(type: BadgeType, trackName: String, rank: Int): Result<Unit> {
        // Redirect to new logic? No, this was just UI emit. 
        // We can keep it for now but StepSyncManager will use awardChampionBadge.
        return Result.success(Unit) 
    }
}
