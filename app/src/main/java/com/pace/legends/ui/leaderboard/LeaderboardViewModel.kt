package com.pace.legends.ui.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pace.legends.domain.manager.StepSyncManager
import com.pace.legends.domain.model.PeriodInfo
import com.pace.legends.domain.repository.AuthRepository
import com.pace.legends.domain.repository.LeaderboardEntry
import com.pace.legends.domain.repository.LeaderboardRepository
import com.pace.legends.domain.repository.StepRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Aylık Maraton Leaderboard ViewModel
 * 
 * F1 temalı sıralama: steps'e göre DESC sıralı top 50
 * ✅ REFACTORED: Error state eklendi
 */
@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    private val getLeaderboardUseCase: com.pace.legends.domain.usecase.leaderboard.GetLeaderboardUseCase,
    private val stepRepository: StepRepository,
    private val stepSyncManager: StepSyncManager,
    private val authRepository: AuthRepository
) : ViewModel() {

    // Top 50 listesi
    private val _leaderboard = MutableStateFlow<List<LeaderboardEntry>>(emptyList())
    val leaderboard: StateFlow<List<LeaderboardEntry>> = _leaderboard.asStateFlow()
    
    // Kullanıcının kendi sıralaması
    private val _userRank = MutableStateFlow<LeaderboardEntry?>(null)
    val userRank: StateFlow<LeaderboardEntry?> = _userRank.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    
    private val _currentMonth = MutableStateFlow("")
    val currentMonth: StateFlow<String> = _currentMonth.asStateFlow()
    
    // Period bilgisi
    private val _periodInfo = MutableStateFlow(PeriodInfo.empty())
    val periodInfo: StateFlow<PeriodInfo> = _periodInfo.asStateFlow()
    
    // ✅ Error State (standardized)
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private var currentTrackId: String? = null

    init {
        // Auto-Refresh on Track Change
        viewModelScope.launch {
            stepRepository.currentTrackId.collect { newTrackId ->
                if (newTrackId != null && newTrackId != currentTrackId) {
                    android.util.Log.d("LeaderboardVM", "🔄 Track changed to $newTrackId - Reloading leaderboard")
                    loadLeaderboard(newTrackId)
                }
            }
        }
    }

    /**
     * İlk yükleme
     */
    fun loadLeaderboard(trackId: String) {
        currentTrackId = trackId
        
        viewModelScope.launch {
            val info = stepSyncManager.getCurrentPeriodInfo()
            _periodInfo.value = info
            _currentMonth.value = info.periodId
            
            _isLoading.value = true
            _errorMessage.value = null
            try {
                fetchLeaderboard(trackId, info.periodId)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Pull-to-refresh
     */
    fun refresh() {
        val activeTrackId = stepRepository.currentTrackId.value ?: currentTrackId ?: return
        currentTrackId = activeTrackId
        
        viewModelScope.launch {
            _isRefreshing.value = true
            _errorMessage.value = null
            try {
                fetchLeaderboard(activeTrackId, _periodInfo.value.periodId)
            } finally {
                _isRefreshing.value = false
            }
        }
    }
    
    /**
     * Clear error message after shown
     */
    fun errorShown() {
        _errorMessage.value = null
    }
    
    private suspend fun fetchLeaderboard(trackId: String, periodId: String) {
        getLeaderboardUseCase(trackId, periodId)
            .onSuccess { finalLeaderboard ->
                _leaderboard.value = finalLeaderboard
                
                val userId = authRepository.getCurrentUserId()
                if (userId != null) {
                    _userRank.value = finalLeaderboard.find { it.userId == userId }
                }
            }
            .onFailure { error ->
                android.util.Log.e("LeaderboardVM", "Error fetching leaderboard: ${error.message}")
                _errorMessage.value = "Sıralama yüklenemedi: ${error.message}"
            }
    }
}

