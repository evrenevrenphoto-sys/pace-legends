package com.pace.legends.ui.track

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.pace.legends.domain.manager.GhostRunnerManager
import com.pace.legends.domain.manager.HealthConnectManager
import com.pace.legends.domain.manager.RaceLocationManager
import com.pace.legends.domain.model.DownloadState
import com.pace.legends.domain.model.Track
import com.pace.legends.domain.repository.AuthRepository
import com.pace.legends.domain.repository.LeaderboardRepository
import com.pace.legends.domain.repository.StepRepository
import com.pace.legends.domain.repository.TrackRepository
import com.pace.legends.domain.util.GeoJsonParser
import com.pace.legends.utils.PathUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class TrackDetailViewModel @Inject constructor(
    private val stepRepository: StepRepository,
    private val trackRepository: TrackRepository,
    private val authRepository: AuthRepository,
    private val leaderboardRepository: LeaderboardRepository,
    private val healthConnectManager: HealthConnectManager,
    val raceLocationManager: RaceLocationManager,
    private val pathUtils: PathUtils,
    private val geoJsonParser: GeoJsonParser,
    private val ghostRunnerManager: GhostRunnerManager,
    @ApplicationContext private val context: android.content.Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Track ID
    private val trackId: String = savedStateHandle["trackId"] ?: "istanbul_park"

    // Single Source of Truth
    private val _uiState = MutableStateFlow(TrackDetailUiState())
    val uiState: StateFlow<TrackDetailUiState> = _uiState.asStateFlow()

    // Internal Race State
    private var startTimeMillis: Long = 0
    private var previousLoops: Int = 0
    private var currentLapStartTime: Long = 0
    
    // Sector Tracking
    private var activeSectorIndex: Int = 0
    private var sectorStartTime: Long = 0

    init {
        loadTrackData()
        startSyncTimeLoop()
        observeGhostRunners()
    }

    private fun observeGhostRunners() {
        viewModelScope.launch {
            ghostRunnerManager.opponents.collect { opponents ->
                _uiState.update { it.copy(opponents = opponents) }
            }
        }
    }

    private fun startSyncTimeLoop() {
        viewModelScope.launch {
            while (isActive) {
                val mins = healthConnectManager.getMinutesSinceLastSync()
                _uiState.update { it.copy(lastSyncMinutesAgo = mins) }
                delay(30_000)
            }
        }
    }

    private fun loadTrackData() {
        viewModelScope.launch {
            try {
                // TrackRepository üzerinden dinamik pist verisi çek
                val trackResult = trackRepository.getTrack(trackId)
                
                if (trackResult.isFailure) {
                    _uiState.update { it.copy(errorUserMessage = "Pist verisi yüklenemedi: ${trackResult.exceptionOrNull()?.message}") }
                    return@launch
                }
                
                val track = trackResult.getOrNull() ?: run {
                    _uiState.update { it.copy(errorUserMessage = "Pist bulunamadı") }
                    return@launch
                }
                
                // State güncelle (Initial)
                _uiState.update { it.copy(currentTrack = track) }
                
                // Smart Download / Cache Check
                trackRepository.prepareTrackFile(track).collect { state ->
                    _uiState.update { it.copy(downloadState = state) }
                    
                    if (state is DownloadState.Success) {
                        // File loaded, parse using helper
                        val jsonString = withContext(Dispatchers.IO) { state.file.readText() }
                        
                        // Arka planda parse et
                        val parsedData = withContext(Dispatchers.Default) {
                            val result = geoJsonParser.parse(jsonString)
                            result
                        }
                        
                        if (parsedData.path.isEmpty()) {
                             _uiState.update { it.copy(errorUserMessage = "GeoJSON parse edilemedi") }
                             return@collect
                        }
                        
                        // Pist yönü ayarı
                        val finalPath = if (track.reverseDirection) parsedData.path.reversed() else parsedData.path
                        val smoothedPath = pathUtils.smoothPath(finalPath, iterations = 3)
                        
                        // Update UI
                        _uiState.update { 
                            it.copy(
                                trackPath = smoothedPath,
                                sectors = parsedData.sectors
                            )
                        }
                        
                        initializeRaceState()
                        
                        // Start Ghost Race
                        ghostRunnerManager.loadGhosts(trackId, track, smoothedPath)
                        ghostRunnerManager.startRaceSimulation(viewModelScope, smoothedPath, track.totalDistanceMeters.toDouble())
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("TrackVM", "Exception in loadTrackData: ${e.message}", e)
                _uiState.update { it.copy(errorUserMessage = e.message) }
            }
        }
    }

    private fun initializeRaceState() {
        viewModelScope.launch {
            // Restore session
            val savedState = stepRepository.getActiveRace()
            startTimeMillis = System.currentTimeMillis()
            currentLapStartTime = startTimeMillis
            
            if (savedState != null && savedState.trackId == trackId) {
                val elapsed = System.currentTimeMillis() - savedState.startTimeMillis
                startTimeMillis = System.currentTimeMillis() - elapsed
                currentLapStartTime = savedState.lastLapTimestamp
                
                val trackDist = _uiState.value.currentTrack.totalDistanceMeters
                if (trackDist > 0) {
                     val currentDist = savedState.steps * 0.75 // Approx
                     previousLoops = (currentDist / trackDist).toInt()
                }
                 val distanceWalked = savedState.steps * 0.75
                 _uiState.update { it.copy(distanceCovered = distanceWalked) }
            }
            
            // Kişisel rekoru yükle
            val bestTime = stepRepository.getBestLapTime(trackId)
            _uiState.update { it.copy(bestLapTime = bestTime) }
            
            // Start Pace Calculation Loop
            monitorSteps()
        }
    }
    
    private suspend fun monitorSteps() {
        stepRepository.currentSteps.collect { steps ->
            // Calculate distance based on steps using central constant (approx 0.75m/step)
            val distance = steps * 0.75 
            
            val trackDistance = _uiState.value.currentTrack.totalDistanceMeters
            if (trackDistance <= 0) return@collect
            
            val loops = (distance / trackDistance).toInt()
            
            // Tur tamamlandı mı?
            if (loops > previousLoops) {
                previousLoops = loops
                onLapCompleted(loops)
            }
            
            // Position updates
            val path = _uiState.value.trackPath
            if (path.isNotEmpty()) {
                val newPos = pathUtils.calculateCurrentPosition(path, distance)
                val walked = pathUtils.calculateWalkedPath(path, distance)
                
                _uiState.update { 
                    it.copy(
                        distanceCovered = distance,
                        currentPosition = newPos,
                        walkedPath = walked,
                        userProgress = it.userProgress.copy(
                            totalSteps = steps.toLong(),
                            completedLoops = loops
                        )
                    )
                }
                
                checkSectorCompletion(distance, path.size)
                calculateProjectedFinish()
            }
        }
    }

    private fun checkSectorCompletion(distance: Double, totalPathPoints: Int) {
        val sectors = _uiState.value.sectors
        if (sectors.isEmpty() || activeSectorIndex >= sectors.size) return
        
        if (activeSectorIndex == 0 && sectorStartTime == 0L) {
            sectorStartTime = System.currentTimeMillis()
        }
        
        val activeSector = sectors[activeSectorIndex]
        val trackDistance = _uiState.value.currentTrack.totalDistanceMeters.toDouble()
        val distanceInLap = distance % trackDistance
        
        // Sektör sonuna gelindi mi? (Basitleştirilmiş logic)
        // Gerçek implementasyonda point index hesabı gerekir, burada distance üzerinden gidiyoruz
        // ... (Kısaltılmış logic)
    }

    private fun calculateProjectedFinish() {
        val trackDistance = _uiState.value.currentTrack.totalDistanceMeters.toDouble()
        if (trackDistance <= 0) return
        
        val elapsedMs = System.currentTimeMillis() - currentLapStartTime
        val distanceInCurrentLap = _uiState.value.distanceCovered % trackDistance
        
        if (distanceInCurrentLap < 100 || elapsedMs < 5000) {
            _uiState.update { it.copy(projectedFinishTime = 0L) }
            return
        }
        
        val elapsedSeconds = elapsedMs / 1000.0
        val averageSpeed = distanceInCurrentLap / elapsedSeconds
        
        if (averageSpeed > 0) {
            val projectedSeconds = (trackDistance / averageSpeed).toLong()
            _uiState.update { it.copy(projectedFinishTime = projectedSeconds) }
        }
    }

    private fun onLapCompleted(completedLoops: Int) {
        val lapTimeMs = System.currentTimeMillis() - currentLapStartTime
        val lapTimeSeconds = lapTimeMs / 1000
        
        // Yerel kaydet
        stepRepository.saveBestLapTime(trackId, lapTimeSeconds)
        
        // Timer Reset
        currentLapStartTime = System.currentTimeMillis()
        activeSectorIndex = 0
        sectorStartTime = System.currentTimeMillis()
        
         _uiState.update { 
             it.copy(
                 bestLapTime = if (it.bestLapTime == 0L || lapTimeSeconds < it.bestLapTime) lapTimeSeconds else it.bestLapTime,
                 currentSectorIndex = 0
             ) 
         }

        // Submit score to Cloud Leaderboard
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId()
            if (!userId.isNullOrEmpty()) {
                val progress = com.pace.legends.domain.model.UserProgress(
                    trackId = trackId,
                    userId = userId,
                    displayName = "Racer ${userId.take(4)}",
                    totalSteps = stepRepository.currentSteps.value.toLong(),
                    completedLoops = completedLoops,
                    totalActiveTimeMillis = System.currentTimeMillis() - startTimeMillis,
                    lastUpdateTimestamp = System.currentTimeMillis(),
                    bestLapTimeSeconds = lapTimeSeconds
                )
                leaderboardRepository.submitScore(progress)
            }
        }
    }

    fun endRace() {
        viewModelScope.launch {
            stepRepository.clearActiveRace()
        }
    }
}
