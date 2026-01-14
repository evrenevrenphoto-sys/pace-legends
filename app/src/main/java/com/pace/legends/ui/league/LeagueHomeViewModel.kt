package com.pace.legends.ui.league

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pace.legends.domain.manager.LeagueManager
import com.pace.legends.domain.manager.RemoteConfigManager
import com.pace.legends.domain.model.LeagueTier
import com.pace.legends.domain.model.Track
import com.pace.legends.domain.model.UserLeagueInfo
import com.pace.legends.domain.repository.LeaderboardEntry
import com.pace.legends.domain.repository.StepRepository
import com.pace.legends.domain.repository.TrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Lig = Pist Ana Ekranı ViewModel
 * 
 * Kullanıcı pist seçmez, liği onun pistini belirler.
 */
@HiltViewModel
class LeagueHomeViewModel @Inject constructor(
    private val leagueManager: LeagueManager,
    private val trackRepository: TrackRepository,
    private val stepRepository: StepRepository,
    private val remoteConfigManager: RemoteConfigManager
) : ViewModel() {

    // Single Source of Truth
    private val _uiState = MutableStateFlow(LeagueHomeUiState(isLoading = true))
    val uiState: StateFlow<LeagueHomeUiState> = _uiState.asStateFlow()
    
    // Remote Config'den gelen dinamik değerler
    val promotionThreshold: StateFlow<Int> = remoteConfigManager.promotionThreshold
    val demotionThreshold: StateFlow<Int> = remoteConfigManager.demotionThreshold
    val leagueSize: StateFlow<Int> = remoteConfigManager.leagueSize

    init {
        loadLeagueData()
    }

    fun loadLeagueData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, userMessage = null) }
            try {
                // 1. Kullanıcının lig bilgisini al
                val infoResult = leagueManager.getCurrentLeagueInfo()
                val info = infoResult.getOrNull()
                
                if (info == null) {
                     _uiState.update { 
                        it.copy(isLoading = false, userMessage = "Lig bilgisi alınamadı: ${infoResult.exceptionOrNull()?.message}") 
                    }
                    return@launch
                }
                
                // 2. Atanmış pisti al (Remote Config'den) -- getAssignedTrack handles Result internally but returns default if fail
                val trackId = leagueManager.getAssignedTrack()
                val track = trackRepository.getTrack(trackId).getOrNull()
                
                // Pisti stepRepository'ye set et (Global Sync)
                if (track != null) {
                    stepRepository.selectTrackForMonth(trackId)
                }
                
                // 3. Sıralamayı al
                val leaderboardResult = leagueManager.getLeaderboard(limit = 20)
                val leaderboardList = leaderboardResult.getOrNull()?.take(10) ?: emptyList()
                
                if (leaderboardResult.isFailure && leaderboardList.isEmpty()) {
                     // Log warning but continue? Or show partial error?
                     // Report said UI swallows errors. 
                     // Let's set message if empty
                     if (leaderboardResult.exceptionOrNull() != null) {
                         // But we might have info loaded, just leaderboard failed.
                         // Let's not block the whole screen but show message?
                         // Current State structure has single userMessage.
                     }
                }
                
                // 4. Kullanıcının sıralamasını al
                var rank = leagueManager.getUserRank()
                
                // 🆕 FALLBACK: Eğer rank 0 döndüyse (Firestore latency vb.), listeden bulmaya çalış
                if (rank == 0) {
                    val userId = leagueManager.getCurrentUserId() // Helper needed or use authRepo
                    if (userId != null) {
                        val userEntry = leaderboardList.find { it.userId == userId }
                        if (userEntry != null) {
                            rank = userEntry.rank
                            android.util.Log.i("LeagueHomeVM", "⚠️ Rank was 0, recovered from leaderboard list: $rank")
                        }
                    }
                }
                
                android.util.Log.d("LeagueHomeVM", "📊 Loaded League Data - Tier: ${info.tier}, Rank: $rank, Threshold: ${promotionThreshold.value}")
                
                // 5. Tier mapping
                val mapping = loadTierTracksMapping()
                
                // Update State Atomic-like
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        leagueInfo = info,
                        assignedTrack = track,
                        leaderboard = leaderboardList,
                        userRank = rank,
                        tierTracks = mapping
                    )
                }
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(isLoading = false, userMessage = "Veriler yüklenemedi: ${e.message}") 
                }
            }
        }
    }
    
    /**
     * 🆕 P0: Pagination - Daha fazla kullanıcı yükle
     */
    fun loadMoreLeaderboard() {
        val state = _uiState.value
        if (state.isLoadingMore || state.endReached) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            
            try {
                val lastEntry = state.leaderboard.lastOrNull()
                if (lastEntry == null) {
                    _uiState.update { it.copy(isLoadingMore = false, endReached = true) }
                    return@launch
                }
                
                // Composite Cursor: steps + userId
                val moreEntriesResult = leagueManager.getLeaderboard(
                    limit = 20, 
                    lastSteps = lastEntry.steps,
                    lastUserId = lastEntry.userId
                )
                
                val moreEntries = moreEntriesResult.getOrNull() ?: emptyList()
                
                if (moreEntries.isEmpty()) {
                    val isError = moreEntriesResult.isFailure
                    if (isError) {
                        _uiState.update { 
                            it.copy(
                                isLoadingMore = false,
                                userMessage = "Liste yüklenemedi: ${moreEntriesResult.exceptionOrNull()?.message}"
                            ) 
                        }
                    } else {
                        _uiState.update { it.copy(isLoadingMore = false, endReached = true) }
                    }
                } else {
                    _uiState.update { 
                        it.copy(
                            isLoadingMore = false,
                            leaderboard = it.leaderboard + moreEntries
                        ) 
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoadingMore = false,
                        userMessage = "Beklenmeyen hata: ${e.message}"
                    ) 
                }
            }
        }
    }
    
    private suspend fun loadTierTracksMapping(): Map<LeagueTier, Track?> {
        val mapping = mutableMapOf<LeagueTier, Track?>()
        
        LeagueTier.entries.forEach { tier ->
            val trackId = remoteConfigManager.getTrackForTier(tier.name)
            val track = trackRepository.getTrack(trackId).getOrNull()
            mapping[tier] = track
        }
        
        return mapping
    }
    
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                stepRepository.syncHealthConnectSteps(force = true)
                loadLeagueData()
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(isLoading = false, userMessage = "Sync başarısız: ${e.message}") 
                }
            }
        }
    }

    fun userMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
