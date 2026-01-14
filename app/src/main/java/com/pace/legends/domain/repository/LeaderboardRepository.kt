package com.pace.legends.domain.repository

import androidx.compose.runtime.Immutable
import com.pace.legends.domain.model.UserProgress

/**
 * Aylık Leaderboard için entry data class
 * @Immutable: Compose recomposition optimizasyonu için işaretlendi
 */
@Immutable
data class LeaderboardEntry(
    val userId: String = "",
    val displayName: String = "",
    val steps: Long = 0,
    val rank: Int = 0,
    val isCurrentUser: Boolean = false,
    val isPro: Boolean = false
)

interface LeaderboardRepository {
    suspend fun getLeaderboard(trackId: String): List<UserProgress>
    suspend fun submitScore(userProgress: UserProgress)
    
    // 🆕 Aylık Maraton Leaderboard
    suspend fun getMonthlyLeaderboard(trackId: String, month: String, forceRefresh: Boolean = false): Result<List<LeaderboardEntry>>
    
    // 🆕 Ölçeklenebilir Sıralama (Top 100 dışındaki kullanıcılar için)
    suspend fun getUserRankBySteps(trackId: String, periodId: String, userSteps: Long): Int?
    
    // 🆕 Toplam Katılımcı Sayısı
    suspend fun getTotalParticipants(trackId: String, periodId: String): Int

    // 🆕 Real Ghost Racing: Top Racers (Best Lap Time)
    suspend fun getTopRacers(trackId: String, limit: Int = 3): List<UserProgress>
}
