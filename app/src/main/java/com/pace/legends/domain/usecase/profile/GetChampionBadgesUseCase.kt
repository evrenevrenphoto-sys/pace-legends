package com.pace.legends.domain.usecase.profile

import com.pace.legends.domain.repository.AuthRepository
import com.pace.legends.domain.repository.BadgeRepository
import com.pace.legends.domain.repository.ChampionBadge
import javax.inject.Inject

/**
 * UseCase for fetching championship badges.
 * 
 * Retrieves past championship badges from Firestore.
 */
class GetChampionBadgesUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val badgeRepository: BadgeRepository
) {
    /**
     * Get all championship badges for the current user.
     * 
     * @return List of ChampionBadge objects or empty list if not signed in.
     */
    suspend operator fun invoke(): List<ChampionBadge> {
        val userId = authRepository.getCurrentUserId() ?: return emptyList()
        return badgeRepository.getChampionBadges(userId)
    }
}
