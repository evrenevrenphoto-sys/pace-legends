package com.pace.legends.domain.manager

import com.pace.legends.data.local.UserBadgeDao
import com.pace.legends.domain.model.BadgeType
import com.pace.legends.domain.model.UserBadge
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BadgeManager @Inject constructor(
    private val badgeDao: UserBadgeDao
) {
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
    suspend fun emitChampionBadge(type: BadgeType, trackName: String, rank: Int) = badgeMutex.withLock {
        // 1. Generic rozeti Room DB'ye kaydet (tekil: "Dönem Şampiyonu" veya "Podyum")
        if (!badgeDao.hasBadge(type.id)) {
            val badge = UserBadge(
                badgeId = type.id,
                earnedTimestamp = System.currentTimeMillis()
            )
            badgeDao.insertBadge(badge)
        }
        
        // 2. UI Event (Snackbar/Dialog için)
        // Not: Detaylı bilgi (pist adı, sıra) için ayrı bir event tipi kullanılabilir
        // Şimdilik basit BadgeType emit ediyoruz
        _badgeEarnedEvents.emit(type)
        
        android.util.Log.d("BadgeManager", "🏆 Champion badge emitted: ${type.title} - $trackName (Rank $rank)")
    }
}
