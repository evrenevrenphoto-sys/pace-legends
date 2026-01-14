package com.pace.legends.domain.usecase.profile

import com.pace.legends.domain.repository.AuthRepository
import com.pace.legends.domain.repository.BadgeRepository
import com.pace.legends.domain.repository.ChampionBadge
import javax.inject.Inject

/**
 * UseCase for claiming past championship badges.
 * 
 * Scans period history and awards retrospective badges for
 * top 3 finishes that weren't previously awarded.
 */
class ClaimPastChampionshipsUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val badgeRepository: BadgeRepository
) {
    /**
     * Claim any unclaimed championship badges from past periods.
     * 
     * @return ClaimResult containing number of badges claimed.
     */
    suspend operator fun invoke(): ClaimResult {
        val userId = authRepository.getCurrentUserId()
            ?: return ClaimResult.NotSignedIn
        
        val periodHistory = badgeRepository.getPeriodHistory(userId)
        var claimedCount = 0
        
        for (entry in periodHistory) {
            // Determine rank: use finalRank if available, otherwise query leaderboard
            val rank = entry.finalRank ?: badgeRepository.getRankFromLeaderboard(
                trackId = entry.trackId,
                periodId = entry.periodId,
                userId = userId
            ) ?: continue
            
            // Only award badges for top 3 finishes
            if (rank > 3) continue
            
            val badgeId = "${entry.periodId}_${entry.trackId}_rank_$rank"
            
            // Check if badge already exists
            if (badgeRepository.hasChampionBadge(userId, badgeId)) continue
            
            // Award the badge
            val badge = ChampionBadge(
                badgeId = badgeId,
                periodId = entry.periodId,
                periodDisplayName = entry.displayName ?: "Period ${entry.periodId}",
                trackId = entry.trackId,
                trackDisplayName = getTrackDisplayName(entry.trackId),
                rank = rank,
                earnedAt = System.currentTimeMillis()
            )
            
            val result = badgeRepository.awardChampionBadge(userId, badge)
            if (result.isSuccess) {
                claimedCount++
            }
        }
        
        return if (claimedCount > 0) {
            ClaimResult.Success(claimedCount)
        } else {
            ClaimResult.NoBadgesToClaim
        }
    }
    
    private fun getTrackDisplayName(trackId: String): String {
        return when (trackId) {
            "belgian_forest_loop" -> "Belçika Doğa Turu"
            "istanbul_park" -> "Istanbul Park"
            "silverstone_sprint" -> "Silverstone Sprint"
            else -> trackId.replace("_", " ").replaceFirstChar { it.uppercase() }
        }
    }
}

/**
 * Result of claiming past championship badges.
 */
sealed class ClaimResult {
    data class Success(val claimedCount: Int) : ClaimResult()
    data object NoBadgesToClaim : ClaimResult()
    data object NotSignedIn : ClaimResult()
}
