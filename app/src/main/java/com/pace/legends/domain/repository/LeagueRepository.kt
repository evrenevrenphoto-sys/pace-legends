package com.pace.legends.domain.repository

import com.pace.legends.domain.model.LeagueTier
import com.pace.legends.domain.model.UserLeagueInfo

/**
 * Lig Sistemi Repository Interface
 * 
 * Eleme Havuzu (sınırsız) ve Ligler (50'şer kişi) için CRUD işlemleri
 */
interface LeagueRepository {
    
    /**
     * Kullanıcının mevcut lig bilgisini getir
     */
    /**
     * Kullanıcının mevcut lig bilgisini getir
     */
    suspend fun getUserLeagueInfo(userId: String): Result<UserLeagueInfo>
    
    /**
     * @deprecated Use createLeague/addUserToLeague logic via LeagueManager
     */
    suspend fun registerToQualifyingPool(userId: String, trackId: String, periodId: String)
    
    /**
     * @deprecated Use getLeagueLeaderboard
     */
    /**
     * @deprecated Use getLeagueLeaderboard
     */
    suspend fun getQualifyingPoolLeaderboard(
        trackId: String, 
        periodId: String, 
        limit: Int = 100,
        lastSteps: Long? = null,
        lastUserId: String? = null
    ): Result<List<LeaderboardEntry>>
    
    /**
     * Belirli bir ligin sıralamasını getir (50 kişi)
     */
    suspend fun getLeagueLeaderboard(
        leagueId: String,
        limit: Int = 20,
        lastSteps: Long? = null,
        lastUserId: String? = null
    ): Result<List<LeaderboardEntry>>
    
    /**
     * Kullanıcıyı yeni lige ata (yükselme/düşme)
     */
    suspend fun updateUserLeague(
        userId: String, 
        newTier: LeagueTier, 
        newLeagueId: String?
    ): Result<Unit>
    
    /**
     * Boş yer olan lig bul (50'den az üyeli)
     */
    suspend fun findAvailableLeague(tier: LeagueTier, trackId: String, maxMembers: Int): Result<String?>
    
    /**
     * Yeni lig oluştur
     */
    suspend fun createLeague(tier: LeagueTier, trackId: String): Result<String>
    
    /**
     * @deprecated Use getUserLeagueRank
     */
    suspend fun getUserQualifyingRank(userId: String, trackId: String, periodId: String): Int
    
    /**
     * Kullanıcının ligdeki sıralamasını getir
     */
    suspend fun getUserLeagueRank(userId: String, leagueId: String): Result<Int>
    
    /**
     * 🆕 Kullanıcıyı lig üyesi olarak ekle ve memberCount'u artır
     */
    suspend fun addUserToLeague(userId: String, leagueId: String, displayName: String): Result<Unit>
    
    /**
     * 🆕 Kullanıcıyı ligden çıkar ve memberCount'u azalt
     * Yükselme/düşme sırasında eski ligden temizlik için kullanılır
     */
    suspend fun removeUserFromLeague(userId: String, leagueId: String): Result<Unit>
}
