package com.pace.legends.ui.league

import com.pace.legends.domain.model.LeagueTier
import com.pace.legends.domain.model.Track
import com.pace.legends.domain.model.UserLeagueInfo
import com.pace.legends.domain.repository.LeaderboardEntry

/**
 * UI State for League Home Screen.
 * Follows Single Source of Truth principle.
 */
data class LeagueHomeUiState(
    val isLoading: Boolean = false,
    val leagueInfo: UserLeagueInfo = UserLeagueInfo(),
    val assignedTrack: Track? = null,
    val leaderboard: List<LeaderboardEntry> = emptyList(),
    val userRank: Int = 0,
    val tierTracks: Map<LeagueTier, Track?> = emptyMap(),
    val isLoadingMore: Boolean = false, // 🆕 Pagination State
    val endReached: Boolean = false,    // 🆕 Pagination State
    val userMessage: String? = null // For Snackbars/Errors
)
