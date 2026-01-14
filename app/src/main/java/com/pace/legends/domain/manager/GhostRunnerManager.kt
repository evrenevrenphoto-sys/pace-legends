package com.pace.legends.domain.manager

import com.google.android.gms.maps.model.LatLng
import com.pace.legends.domain.model.Opponent
import com.pace.legends.domain.model.Track
import com.pace.legends.domain.repository.LeaderboardRepository
import com.pace.legends.utils.PathUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GhostRunnerManager @Inject constructor(
    private val leaderboardRepository: LeaderboardRepository,
    private val pathUtils: PathUtils
) {
    private val _opponents = MutableStateFlow<List<Opponent>>(emptyList())
    val opponents: StateFlow<List<Opponent>> = _opponents.asStateFlow()

    private var raceStartTime: Long = 0

    suspend fun loadGhosts(trackId: String, track: Track, path: List<LatLng>) {
        val topRacers = leaderboardRepository.getTopRacers(trackId, limit = 3)
        val trackMeters = track.totalDistanceMeters.toDouble()
        val startPos = path.firstOrNull() ?: LatLng(0.0, 0.0)

        if (topRacers.isNotEmpty()) {
            val realOpponents = topRacers.mapIndexed { index, racer ->
                val lapTime = racer.bestLapTimeSeconds
                val speedMps = if (lapTime > 0) trackMeters / lapTime else 1.5 
                
                Opponent(
                    id = racer.userId,
                    displayName = racer.displayName.takeIf { it.isNotBlank() } ?: "Racer",
                    currentPosition = startPos,
                    leagueTier = when(index) {
                        0 -> "GOLD"
                        1 -> "SILVER"
                        2 -> "BRONZE"
                        else -> "DIAMOND"
                    },
                    speedKmph = (speedMps * 3.6).toFloat(),
                    totalDistance = 0.0
                )
            }
            _opponents.value = realOpponents
        } else {
             _opponents.value = listOf(
                Opponent(
                    id = "bot_1",
                    displayName = "Pace Bot 🤖",
                    currentPosition = startPos,
                    leagueTier = "BRONZE",
                    speedKmph = 5.0f
                )
             )
        }
    }

    fun startRaceSimulation(scope: CoroutineScope, path: List<LatLng>, trackLength: Double) {
        raceStartTime = System.currentTimeMillis()
        
        scope.launch {
            while (isActive) {
                val elapsedSeconds = (System.currentTimeMillis() - raceStartTime) / 1000.0
                
                if (path.isNotEmpty() && trackLength > 0) {
                    _opponents.value = _opponents.value.map { ghost ->
                        val speedMps = ghost.speedKmph / 3.6
                        val totalDist = speedMps * elapsedSeconds
                        val currentLapDist = totalDist % trackLength
                        
                        val newPos = try {
                             pathUtils.calculateCurrentPosition(path, currentLapDist)
                        } catch (e: Exception) {
                            path.firstOrNull() ?: LatLng(0.0, 0.0)
                        }
                        
                        ghost.copy(
                            currentPosition = newPos,
                            totalDistance = totalDist
                        )
                    }
                }
                delay(1000)
            }
        }
    }
    
    fun reset() {
        _opponents.value = emptyList()
    }
}
