package com.pace.legends.ui.stats

import com.pace.legends.domain.manager.StepSyncManager
import com.pace.legends.domain.model.PeriodHistory
import com.pace.legends.domain.repository.AllTimeStats

/**
 * Single Source of Truth for Global Stats Screen.
 * 
 * All UI state consolidated into one immutable data class.
 * This prevents partial state updates and reduces recomposition overhead.
 */
data class GlobalStatsUiState(
    // Loading & Error
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    
    // Period Info
    val periodInfo: StepSyncManager.PeriodInfo = StepSyncManager.PeriodInfo.empty(),
    
    // Current Race Stats
    val currentRace: CurrentRaceStats = CurrentRaceStats(),
    
    // Time-based Step Breakdowns
    val todaySteps: Long = 0,
    val weekSteps: Long = 0,
    val monthSteps: Long = 0,
    val racePeriodSteps: Long = 0,
    
    // All-Time Stats
    val allTimeStats: AllTimeStats = AllTimeStats(0, 0, 0.0),
    
    // Past Periods History
    val pastPeriods: List<PeriodHistory> = emptyList()
) {
    /**
     * Current race statistics calculated from steps and track.
     */
    data class CurrentRaceStats(
        val steps: Long = 0,
        val laps: Int = 0,
        val rank: Int? = null,
        val totalParticipants: Int? = null
    )
    
    /**
     * Convenience: Has error to display
     */
    val hasError: Boolean get() = errorMessage != null
    
    /**
     * Convenience: Has data loaded
     */
    val hasData: Boolean get() = !isLoading && pastPeriods.isNotEmpty()
}
