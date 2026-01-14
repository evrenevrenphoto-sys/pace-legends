package com.pace.legends.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pace.legends.domain.manager.StepSyncManager
import com.pace.legends.domain.repository.AuthRepository
import com.pace.legends.domain.repository.StepRepository
import com.pace.legends.domain.repository.StatsRepository
import com.pace.legends.domain.repository.AllTimeStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import javax.inject.Inject

/**
 * Global Stats ViewModel
 * 
 * ✅ REFACTORED: Single UiState pattern
 * - All state consolidated into GlobalStatsUiState
 * - Atomic updates via .update { it.copy(...) }
 * - Error handling with userMessage field
 */
@HiltViewModel
class AppGlobalStatsViewModel @Inject constructor(
    private val stepRepository: StepRepository,
    private val stepSyncManager: StepSyncManager,
    private val authRepository: AuthRepository,
    private val trackRepository: com.pace.legends.domain.repository.TrackRepository,
    private val statsRepository: StatsRepository
) : ViewModel() {

    // ✅ Single Source of Truth
    private val _uiState = MutableStateFlow(GlobalStatsUiState())
    val uiState: StateFlow<GlobalStatsUiState> = _uiState.asStateFlow()

    init {
        loadStats()
    }

    fun loadStats() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            
            try {
                // 1. Period Info (Fast)
                val periodInfo = try {
                    stepSyncManager.getCurrentPeriodInfo()
                } catch (e: Exception) {
                    android.util.Log.e("GlobalStats", "Period Info Fail: ${e.message}")
                    StepSyncManager.PeriodInfo.empty()
                }
                
                val userId = authRepository.getCurrentUserId()
                if (userId == null) {
                    _uiState.update { 
                        it.copy(isLoading = false, errorMessage = "Kullanıcı oturumu yok") 
                    }
                    return@launch
                }
                
                val activeTrackId = stepRepository.currentTrackId.value

                // 2. Parallel Fetching with SupervisorScope
                kotlinx.coroutines.supervisorScope {
                    val breakdownsDeferred = async { loadPeriodBreakdowns(periodInfo) }
                    val allTimeDeferred = async { 
                        runCatching { statsRepository.getAllTimeStats() }
                            .getOrDefault(AllTimeStats(0, 0, 0.0))
                    }
                    val pastPeriodsDeferred = async {
                        runCatching { statsRepository.getPeriodHistory(userId) }
                            .getOrDefault(emptyList())
                    }
                    val trackDistanceDeferred = async {
                        runCatching { 
                            if (activeTrackId != null) {
                                trackRepository.getTrack(activeTrackId)?.totalDistanceMeters?.toDouble() ?: 5338.0
                            } else 5338.0
                        }.getOrDefault(5338.0)
                    }

                    // Await all
                    val breakdowns = breakdownsDeferred.await()
                    val allTimeStats = allTimeDeferred.await()
                    val pastPeriods = pastPeriodsDeferred.await()
                    val trackDistance = trackDistanceDeferred.await()

                    // Calculate race stats
                    val distanceWalked = breakdowns.racePeriodSteps * 
                        com.pace.legends.domain.util.RaceProgressCalculator.STEP_TO_METERS
                    val laps = (distanceWalked / trackDistance).toInt()
                    
                    // ✅ Atomic State Update
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = null,
                            periodInfo = periodInfo,
                            todaySteps = breakdowns.todaySteps,
                            weekSteps = breakdowns.weekSteps,
                            monthSteps = breakdowns.monthSteps,
                            racePeriodSteps = breakdowns.racePeriodSteps,
                            allTimeStats = allTimeStats,
                            pastPeriods = pastPeriods.sortedByDescending { p -> p.endTimestamp },
                            currentRace = GlobalStatsUiState.CurrentRaceStats(
                                steps = breakdowns.racePeriodSteps,
                                laps = laps,
                                rank = null,
                                totalParticipants = null
                            )
                        )
                    }
                }

            } catch (e: Exception) {
                android.util.Log.e("AppGlobalStatsViewModel", "CRITICAL: loadStats failed", e)
                _uiState.update { 
                    it.copy(isLoading = false, errorMessage = "Veriler yüklenemedi: ${e.message}") 
                }
            }
        }
    }
    
    /**
     * Clear error message after shown to user
     */
    fun errorShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }
    
    /**
     * Refresh data (pull-to-refresh)
     */
    fun refresh() {
        loadStats()
    }
    
    // Helper data class for breakdown results
    private data class StepBreakdowns(
        val todaySteps: Long,
        val weekSteps: Long,
        val monthSteps: Long,
        val racePeriodSteps: Long
    )
    
    private suspend fun loadPeriodBreakdowns(periodInfo: StepSyncManager.PeriodInfo): StepBreakdowns {
        val now = java.time.Instant.now()
        val zone = java.time.ZoneId.systemDefault()
        
        val todayStart = java.time.LocalDate.now().atStartOfDay(zone).toInstant()
        val weekStart = java.time.LocalDate.now().minusDays(6).atStartOfDay(zone).toInstant()
        val monthStart = java.time.YearMonth.now().atDay(1).atStartOfDay(zone).toInstant()
        val raceStart = periodInfo.startDate.atStartOfDay(zone).toInstant()
        
        return StepBreakdowns(
            todaySteps = stepRepository.getStepsByTimeRange(todayStart, now),
            weekSteps = stepRepository.getStepsByTimeRange(weekStart, now),
            monthSteps = stepRepository.getStepsByTimeRange(monthStart, now),
            racePeriodSteps = stepRepository.getStepsByTimeRange(raceStart, now)
        )
    }
}
