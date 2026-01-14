package com.pace.legends.domain.usecase.leaderboard

import com.pace.legends.domain.repository.AuthRepository
import com.pace.legends.domain.repository.LeaderboardEntry
import com.pace.legends.domain.repository.LeaderboardRepository
import com.pace.legends.domain.repository.StepRepository
import javax.inject.Inject

/**
 * UseCase for fetching and processing the leaderboard.
 * 
 * 🧠 Business Logic:
 * 1. Fetch Firestore global leaderboard (Top 50)
 * 2. Fetch User's local fresh steps
 * 3. Merge User into the list (if not already present or needs update)
 * 4. Sort by steps (Descending)
 * 5. Assign Ranks (1..N)
 */
class GetLeaderboardUseCase @Inject constructor(
    private val leaderboardRepository: LeaderboardRepository,
    private val stepRepository: StepRepository,
    private val authRepository: AuthRepository
) {
    /**
     * Get processed leaderboard with current user merged and ranked.
     */
    suspend operator fun invoke(trackId: String, periodId: String): Result<List<LeaderboardEntry>> {
        return try {
            // 1. Fetch Firestore Data (Others)
            val firestoreEntries = leaderboardRepository.getMonthlyLeaderboard(trackId, periodId)
            
            // 2. Fetch My Local Data (Source of Truth: Health Connect via Fresh Fetch)
            val mySteps = stepRepository.getFreshCurrentPeriodSteps()
            val userId = authRepository.getCurrentUserId()
            
            val finalLeaderboard = if (userId != null) {
                // TODO: User displayName should ideally come from a cached profile repo to avoid auth calls
                val currentUser = authRepository.getCurrentUser()
                val myDisplayName = currentUser?.displayName ?: "Sen"
                
                // 3. Create My Entry (Always fresh)
                val myEntry = LeaderboardEntry(
                    userId = userId,
                    displayName = myDisplayName,
                    steps = mySteps,
                    rank = 0, // Will be calculated
                    isCurrentUser = true
                )
                
                // 4. Filter out stale "Me" from Firestore list
                val others = firestoreEntries.filter { it.userId != userId }
                
                // 5. Merge & Sort
                val combined = (others + myEntry)
                    .filter { it.steps > 0 } // Filter out zero steps
                    .sortedByDescending { it.steps }
                
                // 6. Re-Rank
                combined.mapIndexed { index, entry -> 
                    entry.copy(rank = index + 1)
                }
            } else {
                firestoreEntries
                    .sortedByDescending { it.steps }
                    .mapIndexed { index, entry -> 
                        entry.copy(rank = index + 1)
                    }
            }
            
            Result.success(finalLeaderboard)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
