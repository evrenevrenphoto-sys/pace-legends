package com.pace.legends.domain.manager

import com.google.firebase.auth.FirebaseUser
import com.pace.legends.domain.model.LeagueTier
import com.pace.legends.domain.repository.LeagueRepository
import com.pace.legends.domain.repository.AuthRepository
// Fixed Import
import com.pace.legends.domain.manager.RemoteConfigManager
import com.pace.legends.domain.manager.RewardManager
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

class LeagueManagerTest {

    private lateinit var leagueRepository: LeagueRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var stepSyncManager: StepSyncManager
    private lateinit var remoteConfigManager: RemoteConfigManager
    private lateinit var rewardManager: RewardManager
    
    private lateinit var leagueManager: LeagueManager

    @Before
    fun setup() {
        leagueRepository = mockk(relaxed = true)
        authRepository = mockk(relaxed = true)
        stepSyncManager = mockk(relaxed = true)
        remoteConfigManager = mockk(relaxed = true)
        rewardManager = mockk(relaxed = true)
        
        leagueManager = LeagueManager(
            leagueRepository,
            authRepository,
            stepSyncManager,
            remoteConfigManager,
            rewardManager
        )
    }

    @Test
    fun `registerNewUser should assign to QUALIFYING bucket`() = runBlocking {
        // Arrange
        val userId = "user123"
        val qualifyingId = "qualifying_bucket_1"
        val qualifyingTrack = "istanbul_park"
        
        // Mock Auth
        every { authRepository.getCurrentUserId() } returns userId
        val mockUser = mockk<FirebaseUser>(relaxed = true)
        every { mockUser.displayName } returns "Test User"
        every { authRepository.getCurrentUser() } returns mockUser
        
        // Mock Managers
        every { stepSyncManager.getCurrentPeriod() } returns "period_1"
        
        // Mock Config
        // Mocking generic any() call can be tricky, be specific if possible
        every { remoteConfigManager.getTrackForTier(any()) } returns qualifyingTrack
        
        // Mock Repo
        coEvery { leagueRepository.getUserLeagueInfo(userId) } returns com.pace.legends.domain.model.UserLeagueInfo() // Default: Qualifying, no league
        coEvery { leagueRepository.findAvailableLeague(LeagueTier.QUALIFYING, any(), any()) } returns qualifyingId
        coEvery { leagueRepository.addUserToLeague(any(), any(), any()) } just Runs

        // Act
        // Corrected: No arguments
        leagueManager.registerNewUser()

        // Assert
        // Verify we searched with maxMembers=100 (Qualifying Bucket Size)
        coVerify { leagueRepository.findAvailableLeague(LeagueTier.QUALIFYING, qualifyingTrack, 100) }
        
        // Verify user was added to that bucket
        coVerify { leagueRepository.addUserToLeague(userId, qualifyingId, any()) }
    }
}
