package com.pace.legends.domain.manager

import com.pace.legends.domain.model.LeagueTier
import com.pace.legends.domain.model.PromotionResult
import com.pace.legends.domain.model.UserLeagueInfo
import com.pace.legends.domain.repository.AuthRepository
import com.pace.legends.domain.repository.LeaderboardEntry
import com.pace.legends.domain.repository.LeagueRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lig Sistemi Yöneticisi
 * 
 * 🆕 LİG = PİST Konsepti:
 * - Her lig kademesi bir piste eşlenir
 * - Kullanıcı pist seçmez, ligi onun pistini belirler
 * - Remote Config ile pist ataması değiştirilebilir
 */
@Singleton
class LeagueManager @Inject constructor(
    private val leagueRepository: LeagueRepository,
    private val authRepository: AuthRepository,
    private val stepSyncManager: StepSyncManager,
    private val remoteConfigManager: RemoteConfigManager,
    private val rewardManager: RewardManager // 🆕 Ödül sistemi
) {
    companion object {
        // 🔄 PROMOTION_THRESHOLD ve DEMOTION_THRESHOLD artık RemoteConfig'den geliyor
        private const val TAG = "LeagueManager"
    }

    /**
     * Kullanıcının mevcut lig bilgisini getir
     */
    /**
     * Kullanıcının mevcut lig bilgisini getir
     */
    suspend fun getCurrentLeagueInfo(): Result<UserLeagueInfo> {
        val userId = authRepository.getCurrentUserId() 
            ?: return Result.failure(Exception("User not logged in"))
        return leagueRepository.getUserLeagueInfo(userId)
    }

    /**
     * 🆕 Kullanıcının ligine göre atanmış pisti döndür
     * Lig = Pist konseptinin temel fonksiyonu
     */
    suspend fun getAssignedTrack(): String {
        val leagueInfoResult = getCurrentLeagueInfo()
        val leagueInfo = leagueInfoResult.getOrNull() 
            ?: UserLeagueInfo() // Fallback to QUALIFYING if error (Safe Default)
            
        return remoteConfigManager.getTrackForTier(leagueInfo.tier.name)
    }

    /**
     * Helper to expose current user ID
     */
    fun getCurrentUserId(): String? {
        return authRepository.getCurrentUserId()
    }
    
    /**
     * 🆕 Belirli bir kademe için pist ID'sini döndür
     */
    fun getTrackForTier(tier: LeagueTier): String {
        return remoteConfigManager.getTrackForTier(tier.name)
    }

    /**
     * Yeni kullanıcıyı Eleme Havuzu'na kaydet
     * (Uygulama ilk açıldığında otomatik çağrılır)
     */
    suspend fun registerNewUser() {
        val userId = authRepository.getCurrentUserId() ?: return
        val periodId = stepSyncManager.getCurrentPeriod()
        
        val currentInfo = leagueRepository.getUserLeagueInfo(userId).getOrNull() ?: UserLeagueInfo()
        
        // Zaten bir ligde ise, LOCAL pist verisini güncelle (Restore)
        if (currentInfo.tier != LeagueTier.QUALIFYING) {
            android.util.Log.d(TAG, "User already in a league: ${currentInfo.tier}. Ensuring local track sync.")
            val assignedTrack = getTrackForTier(currentInfo.tier)
            stepSyncManager.forceUpdateActiveTrack(assignedTrack)
            return
        }
        
        // Eleme havuzunun pisti
        val qualifyingTrackId = getTrackForTier(LeagueTier.QUALIFYING)
        
        // 🆕 UNIFIED BUCKETS: Eleme için 100 kişilik bucket kullan
        val leagueId = findOrCreateLeague(LeagueTier.QUALIFYING, qualifyingTrackId) ?: return 
        
        leagueRepository.updateUserLeague(userId, LeagueTier.QUALIFYING, leagueId)
        
        // 🆕 BUG #2 & #3 FIX: Lig üyesi oluştur ve memberCount artır
        val displayName = authRepository.getCurrentUser()?.displayName ?: "Racer ${userId.take(4)}"
        leagueRepository.addUserToLeague(userId, leagueId, displayName)
        
        // 🆕 P0 FIX: Firestore'daki eski activeTrackId'yi de güncelle
        stepSyncManager.forceUpdateActiveTrack(qualifyingTrackId)
        
        android.util.Log.d(TAG, "✅ New user registered to qualifying bucket: $leagueId")
    }

    /**
     * Kullanıcının bulunduğu havuz/lig sıralamasını getir
     */
    /**
     * Kullanıcının bulunduğu havuz/lig sıralamasını getir
     */
    suspend fun getLeaderboard(
        limit: Int = 20,
        lastSteps: Long? = null,
        lastUserId: String? = null
    ): Result<List<LeaderboardEntry>> {
        val userId = authRepository.getCurrentUserId() ?: return Result.failure(Exception("User not logged in"))
        
        val leagueInfoResult = leagueRepository.getUserLeagueInfo(userId)
        val leagueInfo = leagueInfoResult.getOrNull() 
            ?: return Result.failure(leagueInfoResult.exceptionOrNull() ?: Exception("Failed to get league info"))
        
        return if (leagueInfo.leagueId != null) {
            leagueRepository.getLeagueLeaderboard(leagueInfo.leagueId, limit, lastSteps, lastUserId)
        } else {
             // Fallback logic
             Result.success(emptyList())
        }
    }

    /**
     * Kullanıcının sıralamasını getir
     */
    suspend fun getUserRank(): Int {
        val userId = authRepository.getCurrentUserId() ?: return 0
        val leagueInfo = leagueRepository.getUserLeagueInfo(userId).getOrNull() ?: return 0
        
        return leagueInfo.leagueId?.let { 
             leagueRepository.getUserLeagueRank(userId, it).getOrNull()
        } ?: 0
    }

    /**
     * Dönem sonu yükselme/düşme işlemlerini hesapla ve uygula
     * (StepSyncManager.archiveCompletedPeriod içinden çağrılır)
     */
    suspend fun processEndOfPeriod(
        userId: String,
        periodId: String,
        userRank: Int
    ): PromotionResult? {
        val currentInfo = leagueRepository.getUserLeagueInfo(userId).getOrNull() ?: return null
        val currentTier = currentInfo.tier
        
        // 🔄 Remote Config'den dinamik threshold'lar
        val promotionThreshold = remoteConfigManager.promotionThreshold.value
        val demotionThreshold = remoteConfigManager.demotionThreshold.value
        
        // Yükselme kontrolü
        if (userRank in 1..promotionThreshold && currentTier.canPromote()) {
            val newTier = currentTier.nextTier() ?: return null
            val newTrackId = getTrackForTier(newTier)
            val newLeagueId = findOrCreateLeague(newTier, newTrackId) ?: return null
            
            leagueRepository.updateUserLeague(userId, newTier, newLeagueId)
            
            // 🆕 FIX: Eski ligden çıkar (hayalet üye önleme)
            currentInfo.leagueId?.let { oldLeagueId ->
                leagueRepository.removeUserFromLeague(userId, oldLeagueId)
            }
            
            // 🆕 BUG #2 & #3 FIX: Lig üyesi oluştur ve memberCount artır
            val displayName = authRepository.getCurrentUser()?.displayName ?: "Racer ${userId.take(4)}"
            leagueRepository.addUserToLeague(userId, newLeagueId, displayName)
            
            // 🆕 P0 FIX: Lokal Track ID'yi güncelle
            stepSyncManager.forceUpdateActiveTrack(newTrackId)
            
            // 🆕 REWARD: Lig yükselme ödülü ver (Coin + Çerçeve)
            val rewardResult = rewardManager.awardLeaguePromotion(newTier, periodId, newTrackId)
            if (rewardResult.isFailure) {
                android.util.Log.e(TAG, "⚠️ Failed to award league promotion: ${rewardResult.exceptionOrNull()?.message}")
            }
            
            android.util.Log.d(TAG, "🎉 PROMOTION: $currentTier -> $newTier (New Track: $newTrackId)")
            return PromotionResult(
                previousTier = currentTier,
                newTier = newTier,
                isPromotion = true
            )
        }
        
        // 🆕 REWARD: Dönem sonu sıralama ödülü (Top 3)
        if (userRank in 1..3) {
            val rewardResult = rewardManager.awardPeriodRank(userRank, periodId, getTrackForTier(currentTier))
             if (rewardResult.isFailure) {
                android.util.Log.e(TAG, "⚠️ Failed to award period rank: ${rewardResult.exceptionOrNull()?.message}")
            }
        }
        
        // Düşme kontrolü (ligdeyse)
        if (currentTier != LeagueTier.QUALIFYING) {
            val totalInLeague = currentInfo.leagueId?.let { 
                leagueRepository.getLeagueLeaderboard(it).getOrNull()?.size 
            } ?: remoteConfigManager.leagueSize.value
            
            val demotionZoneStart = totalInLeague - demotionThreshold + 1
            
            if (userRank >= demotionZoneStart && currentTier.canDemote()) {
                val newTier = currentTier.previousTier() ?: LeagueTier.QUALIFYING
                val newTrackId = getTrackForTier(newTier)
                val newLeagueId = if (newTier == LeagueTier.QUALIFYING) null 
                                  else findOrCreateLeague(newTier, newTrackId)
                
                leagueRepository.updateUserLeague(userId, newTier, newLeagueId)
                
                // 🆕 FIX: Eski ligden çıkar (hayalet üye önleme)
                currentInfo.leagueId?.let { oldLeagueId ->
                    leagueRepository.removeUserFromLeague(userId, oldLeagueId)
                }
                
                // 🆕 Qualifying'e düşüyorsa havuza kaydet
                if (newTier == LeagueTier.QUALIFYING) {
                    val periodId = stepSyncManager.getCurrentPeriod()
                    leagueRepository.registerToQualifyingPool(userId, newTrackId, periodId)
                    android.util.Log.d(TAG, "📝 Registered to qualifying pool: $newTrackId for period $periodId")
                }
                
                // 🆕 P0 FIX: Lokal Track ID'yi güncelle
                stepSyncManager.forceUpdateActiveTrack(newTrackId)
                
                android.util.Log.d(TAG, "📉 DEMOTION: $currentTier -> $newTier (New Track: $newTrackId)")
                return PromotionResult(
                    previousTier = currentTier,
                    newTier = newTier,
                    isPromotion = false,
                    isDemotion = true
                )
            }
        }
        
        // Değişiklik yok
        return null
    }

    private suspend fun findOrCreateLeague(tier: LeagueTier, trackId: String): String? {
        // 🆕 UNIFIED BUCKETS: Eleme için 100, diğerleri için config (50)
        val maxMembers = if (tier == LeagueTier.QUALIFYING) 100 
                         else remoteConfigManager.leagueSize.value
                         
        val availableId = leagueRepository.findAvailableLeague(tier, trackId, maxMembers).getOrNull()
        if (availableId != null) return availableId
        
        return leagueRepository.createLeague(tier, trackId).getOrNull()
    }
}

