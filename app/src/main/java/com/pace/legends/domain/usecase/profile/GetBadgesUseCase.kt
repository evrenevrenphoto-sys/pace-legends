package com.pace.legends.domain.usecase.profile

import com.pace.legends.domain.model.BadgeType
import com.pace.legends.domain.repository.BadgeRepository
import com.pace.legends.domain.repository.EarnedBadge
import javax.inject.Inject

/**
 * UseCase for fetching all badge types with earned status.
 * 
 * Combines badge definitions with user's earned badges from Room DB.
 */
class GetBadgesUseCase @Inject constructor(
    private val badgeRepository: BadgeRepository
) {
    /**
     * Get all badges with their earned status.
     * 
     * @return List of BadgeInfo objects containing badge type and earned status.
     */
    suspend operator fun invoke(): List<BadgeInfo> {
        val earnedBadges = badgeRepository.getEarnedBadges()
        val earnedMap = earnedBadges.associateBy { it.badgeId }
        
        return BadgeType.entries.map { type ->
            val earned = earnedMap[type.id]
            BadgeInfo(
                type = type,
                isEarned = earned != null,
                earnedAt = earned?.earnedTimestamp
            )
        }
    }
}

/**
 * Badge information with earned status.
 */
data class BadgeInfo(
    val type: BadgeType,
    val isEarned: Boolean,
    val earnedAt: Long?
)
