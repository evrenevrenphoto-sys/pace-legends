package com.pace.legends.ui.track

import android.content.Context
import androidx.lifecycle.SavedStateHandle
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

import com.pace.legends.utils.MainDispatcherRule
import com.pace.legends.utils.PathUtils
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

@ExperimentalCoroutinesApi
class TrackDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: TrackDetailViewModel

    // Mocks
    private val stepRepository: StepRepository = mockk(relaxed = true)
    private val trackRepository: TrackRepository = mockk(relaxed = true)
    private val authRepository: AuthRepository = mockk(relaxed = true)
    private val leaderboardRepository: LeaderboardRepository = mockk(relaxed = true)
    private val healthConnectManager: HealthConnectManager = mockk(relaxed = true)
    private val raceLocationManager: RaceLocationManager = mockk(relaxed = true)
    private val pathUtils: PathUtils = mockk(relaxed = true)
    private val geoJsonParser: GeoJsonParser = mockk(relaxed = true)
    private val ghostRunnerManager: GhostRunnerManager = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private val savedStateHandle: SavedStateHandle = SavedStateHandle().apply {
        set("trackId", "test_track_id")
    }

    @Before
    fun setup() {
        // Default mocks
        every { stepRepository.currentSteps } returns MutableStateFlow(0)
        every { ghostRunnerManager.opponents } returns MutableStateFlow(emptyList())
        
        viewModel = TrackDetailViewModel(
            stepRepository,
            trackRepository,
            authRepository,
            leaderboardRepository,
            healthConnectManager,
            raceLocationManager,
            pathUtils,
            geoJsonParser,
            ghostRunnerManager,
            context,
            savedStateHandle
        )
    }

    @Test
    fun `loadTrackData_success updates UI state with track`() = runTest {
        // GIVEN
        val mockTrack = Track(
            id = "test_track_id",
            genericName = mapOf("en" to "Istanbul Park"),
            totalDistanceMeters = 5338,
            description = mapOf("en" to "F1 Track")
        )
        val mockFile: File = mockk()
        every { mockFile.readText() } returns "{}" // Dummy Json
        
        coEvery { trackRepository.getTrack("test_track_id") } returns mockTrack
        coEvery { trackRepository.prepareTrackFile(mockTrack) } returns flowOf(DownloadState.Success(mockFile))
        coEvery { geoJsonParser.parse(any()) } returns GeoJsonParser.GeoJsonResult(
            path = listOf(com.google.android.gms.maps.model.LatLng(0.0, 0.0)),
            sectors = emptyList()
        )
        every { pathUtils.smoothPath(any(), any()) } returns listOf(com.google.android.gms.maps.model.LatLng(0.0, 0.0))

        // WHEN (Re-trigger loadTrackData manually or rely on init if accessible, 
        // but loadTrackData is private called in init. 
        // Since we mock before init, init should pick it up. 
        // Wait for coroutines to settle is needed usually, runTest handles StandardDispatcher.
        // We might need to advanceUntilIdle() if operations are pending)
        
        // Assert initial state might be default, need to wait for collection
        
        // Let's rely on the fact that init called it. 
        // Since we use UnconfinedTestDispatcher in rule (default for MainDispatcherRule usually), it might execute immediately.
        
        // THEN
        val state = viewModel.uiState.value
        assertEquals(mockTrack, state.currentTrack)
        assertTrue(state.downloadState is DownloadState.Success)
    }

    @Test
    fun `loadTrackData_error updates UI state with error message`() = runTest {
        // GIVEN
        coEvery { trackRepository.getTrack("test_track_id") } returns null

        // Re-init VM to trigger logic again with new mock
        viewModel = TrackDetailViewModel(
            stepRepository, trackRepository, authRepository, leaderboardRepository,
            healthConnectManager, raceLocationManager, pathUtils, geoJsonParser,
            ghostRunnerManager, context, savedStateHandle
        )

        // THEN
        val state = viewModel.uiState.value
        assertEquals("Pist bulunamadı", state.errorUserMessage)
    }
    @Test
    fun `monitorSteps_lapCompletion submits score and saves best time`() = runTest {
        // GIVEN
        val mockTrack = Track(
            id = "test_track_id",
            genericName = mapOf("en" to "Track"),
            totalDistanceMeters = 1000,
            description = mapOf("en" to "Desc")
        )
        val mockFile: File = mockk()
        every { mockFile.readText() } returns "{}"

        // Setup successful track load
        coEvery { trackRepository.getTrack("test_track_id") } returns mockTrack
        coEvery { trackRepository.prepareTrackFile(mockTrack) } returns flowOf(DownloadState.Success(mockFile))
        coEvery { geoJsonParser.parse(any()) } returns GeoJsonParser.GeoJsonResult(
            path = listOf(com.google.android.gms.maps.model.LatLng(0.0, 0.0), com.google.android.gms.maps.model.LatLng(0.01, 0.01)),
            sectors = emptyList()
        )
        every { pathUtils.smoothPath(any(), any()) } returns listOf(com.google.android.gms.maps.model.LatLng(0.0, 0.0))
        every { pathUtils.calculateCurrentPosition(any(), any()) } returns com.google.android.gms.maps.model.LatLng(0.0, 0.0)
        every { pathUtils.calculateWalkedPath(any(), any()) } returns emptyList()

        // Setup User & Steps
        coEvery { authRepository.getCurrentUserId() } returns "user_123"
        val stepsFlow = MutableStateFlow(0)
        every { stepRepository.currentSteps } returns stepsFlow
        every { stepRepository.getActiveRace() } returns null
        coEvery { stepRepository.getBestLapTime("test_track_id") } returns 0L

        // RE-INIT VM to start flows
        viewModel = TrackDetailViewModel(
            stepRepository, trackRepository, authRepository, leaderboardRepository,
            healthConnectManager, raceLocationManager, pathUtils, geoJsonParser,
            ghostRunnerManager, context, savedStateHandle
        )
        
        // Wait for setup (In Unconfined dispatcher, this happens eagerly mostly, but we trigger flow emission)
        
        // WHEN - Simulate enough steps for 1 lap (1000m / 0.75 = 1334 steps)
        stepsFlow.value = 1500 // 1125 meters -> 1 Lap completed

        // THEN
        // Verify Best Lap Saved (triggered by onLapCompleted)
        coVerify(atLeast = 1) { stepRepository.saveBestLapTime("test_track_id", any()) }
        
        // Verify Score Submitted
        coVerify(atLeast = 1) { leaderboardRepository.submitScore(any()) }
    }
}
