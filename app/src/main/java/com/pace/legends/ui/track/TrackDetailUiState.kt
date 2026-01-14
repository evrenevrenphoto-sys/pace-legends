package com.pace.legends.ui.track

import com.google.android.gms.maps.model.LatLng
import com.pace.legends.domain.model.DownloadState
import com.pace.legends.domain.model.Opponent
import com.pace.legends.domain.model.Sector
import com.pace.legends.domain.model.Track
import com.pace.legends.domain.model.UserProgress

/**
 * UI State for Track Detail / Race Screen.
 * Aggregates all race data into a single verified state.
 */
data class TrackDetailUiState(
    // Data Loading
    val isLoading: Boolean = true,
    val downloadState: DownloadState = DownloadState.Idle,
    val errorUserMessage: String? = null,

    // Track Data
    val currentTrack: Track = Track(),
    val trackPath: List<LatLng> = emptyList(),
    val sectors: List<Sector> = emptyList(),

    // User Progress (Race State)
    val userProgress: UserProgress = UserProgress(),
    val currentPosition: LatLng = LatLng(0.0, 0.0),
    val walkedPath: List<LatLng> = emptyList(),
    val distanceCovered: Double = 0.0,
    
    // Race Stats
    val bestLapTime: Long = 0L,
    val projectedFinishTime: Long = 0L,
    
    // Live Dynamic Objects
    val opponents: List<Opponent> = emptyList(),
    
    // Sector Info
    val currentSectorIndex: Int = 0,
    val lastSectorTime: SectorTime? = null,
    
    // Sync Status
    val lastSyncMinutesAgo: Int = -1
)

data class SectorTime(
    val sectorIndex: Int, 
    val timeSeconds: Long, 
    val delta: Long, 
    val status: SectorStatus
)

enum class SectorStatus { PURPLE, GREEN, YELLOW }
