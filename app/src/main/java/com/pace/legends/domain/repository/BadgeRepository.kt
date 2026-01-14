package com.pace.legends.domain.repository

import com.pace.legends.domain.model.BadgeType

/**
 * Badge Repository Interface
 * 
 * Abstracts badge data access from Room (local badges) and Firestore (champion badges).
 */
interface BadgeRepository {
    
    /**
     * Get all earned badges from local DB.
     */
    suspend fun getEarnedBadges(): List<EarnedBadge>
    
    /**
     * Get championship badges from Firestore.
     */
    suspend fun getChampionBadges(userId: String): List<ChampionBadge>
    
    /**
     * Award a championship badge to user.
     */
    suspend fun awardChampionBadge(userId: String, badge: ChampionBadge): Result<Unit>
    
    /**
     * Check if a specific champion badge already exists.
     */
    suspend fun hasChampionBadge(userId: String, badgeId: String): Boolean
    
    /**
     * Get user's period history for retrospective badge claims.
     */
    suspend fun getPeriodHistory(userId: String): List<PeriodHistoryEntry>
    
    /**
     * Get user's rank from a past leaderboard.
     */
    suspend fun getRankFromLeaderboard(trackId: String, periodId: String, userId: String): Int?
}

/**
 * Earned badge from local Room DB.
 */
data class EarnedBadge(
    val badgeId: String,
    val badgeType: BadgeType,
    val earnedTimestamp: Long
)

/**
 * Championship badge from Firestore.
 */
data class ChampionBadge(
    val badgeId: String,
    val periodId: String,
    val periodDisplayName: String,
    val trackId: String,
    val trackDisplayName: String,
    val rank: Int,
    val earnedAt: Long
)

/**
 * Period history entry for retrospective badge calculation.
 */
data class PeriodHistoryEntry(
    val periodId: String,
    val trackId: String,
    val displayName: String?,
    val finalRank: Int?
)
