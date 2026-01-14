package com.pace.legends.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pace.legends.domain.model.PeriodInfo
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
    private val statsRepository: StatsRepository,
    private val dailyStepLogDao: com.pace.legends.data.local.DailyStepLogDao // 🚀 P1: Direct DB Access for Aggregation
) : ViewModel() {

    // ✅ Single Source of Truth
    private val _uiState = MutableStateFlow(GlobalStatsUiState())
    val uiState: StateFlow<GlobalStatsUiState> = _uiState.asStateFlow()

    // ✅ Race condition guard (P1 fix)
    private val loadMutex = kotlinx.coroutines.sync.Mutex()

    init {
        loadStats()
    }

    fun loadStats() {
        viewModelScope.launch {
            // Prevent concurrent execution
            if (!loadMutex.tryLock()) {
                android.util.Log.w("GlobalStatsVM", "Load already in progress, skipping duplicate")
                return@launch
            }
            
            try {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            
            try {
                // 1. Period Info (Fast)
                val periodInfo = try {
                    stepSyncManager.getCurrentPeriodInfo()
                } catch (e: Exception) {
                    android.util.Log.e("GlobalStats", "Period Info Fail: ${e.message}")
                    PeriodInfo.empty()
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
                        statsRepository.getAllTimeStats()
                            .getOrDefault(AllTimeStats(0, 0, 0.0))
                    }
                    val pastPeriodsDeferred = async {
                        statsRepository.getPeriodHistory(userId)
                            .getOrDefault(emptyList())
                    }
                    val trackDistanceDeferred = async {
                        if (activeTrackId != null) {
                            val track = trackRepository.getTrack(activeTrackId).getOrNull()
                            track?.totalDistanceMeters?.toDouble() ?: 5338.0
                        } else {
                            5338.0
                        }
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
            } finally {
                // ✅ Unlock after load completes (P1 fix)
                loadMutex.unlock()
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
    
    private suspend fun loadPeriodBreakdowns(periodInfo: PeriodInfo): StepBreakdowns {
        val userId = authRepository.getCurrentUserId() ?: return StepBreakdowns(0, 0, 0, 0)
        // Active track ID'yi al (null ise varsayılan kullanılabilir veya 0 döner)
        // NOT: İdeal olan o anki seçili track için istatistik göstermektir.
        val trackId = stepRepository.currentTrackId.value ?: return StepBreakdowns(0, 0, 0, 0)

        // Date Calculations
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now()
        
        val todayEpoch = today.toEpochDay()
        val weekStartEpoch = today.minusDays(6).toEpochDay() // Last 7 days inclusive
        val monthStartEpoch = java.time.YearMonth.now().atDay(1).toEpochDay()
        val monthEndEpoch = java.time.YearMonth.now().atEndOfMonth().toEpochDay()
        
        // Race Period
        val raceStartEpoch = periodInfo.startDate.toEpochDay()
        val raceEndEpoch = periodInfo.endDate.toEpochDay()

        // 🚀 PARALLEL EXECUTION (P1)
        return kotlinx.coroutines.coroutineScope {
            val todayDeferred = async { 
                dailyStepLogDao.getStepsForDay(userId, trackId, todayEpoch) ?: 0L 
            }
            val weekDeferred = async { 
                dailyStepLogDao.getStepsForDateRange(userId, trackId, weekStartEpoch, todayEpoch) ?: 0L 
            }
            val monthDeferred = async { 
                dailyStepLogDao.getStepsForDateRange(userId, trackId, monthStartEpoch, monthEndEpoch) ?: 0L 
            }
            // Race period might be same as month, but kept separate for logic
            val raceDeferred = async {
                dailyStepLogDao.getStepsForDateRange(userId, trackId, raceStartEpoch, raceEndEpoch) ?: 0L
            }
            
            StepBreakdowns(
                todaySteps = todayDeferred.await(),
                weekSteps = weekDeferred.await(),
                monthSteps = monthDeferred.await(),
                racePeriodSteps = raceDeferred.await()
            )
        }
    }
}
