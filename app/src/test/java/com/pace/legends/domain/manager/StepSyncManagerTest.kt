package com.pace.legends.domain.manager

import android.content.Context
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import com.google.android.gms.tasks.Task
import com.google.firebase.functions.HttpsCallableResult
import com.google.firebase.firestore.FirebaseFirestore
import com.pace.legends.data.local.AppDatabase
import com.pace.legends.data.local.DailyStepLogDao
import com.pace.legends.domain.repository.LeaderboardRepository
import com.pace.legends.domain.repository.AuthRepository
import com.pace.legends.domain.repository.TrackRepository
import com.pace.legends.domain.repository.LeagueRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

class StepSyncManagerTest {

    private lateinit var authRepository: AuthRepository
    private lateinit var dailyStepLogDao: DailyStepLogDao
    private lateinit var appDatabase: AppDatabase
    private lateinit var remoteConfigManager: RemoteConfigManager
    private lateinit var healthConnectManager: HealthConnectManager
    private lateinit var leaderboardRepository: LeaderboardRepository
    private lateinit var functions: FirebaseFunctions
    private lateinit var firestore: FirebaseFirestore
    private lateinit var context: Context
    private lateinit var trackRepository: TrackRepository
    private lateinit var badgeManager: BadgeManager
    private lateinit var leagueManager: LeagueManager
    private lateinit var leagueRepository: LeagueRepository
    private lateinit var periodCalculator: PeriodCalculator
    
    private lateinit var stepSyncManager: StepSyncManager

    private lateinit var raceLocationManager: RaceLocationManager
    private lateinit var externalScope: kotlinx.coroutines.CoroutineScope
    private lateinit var syncStepsUseCase: com.pace.legends.domain.usecase.sync.SyncStepsUseCase

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.v(any(), any()) } returns 0

        authRepository = mockk(relaxed = true)
        dailyStepLogDao = mockk(relaxed = true)
        appDatabase = mockk(relaxed = true)
        every { appDatabase.dailyStepLogDao() } returns dailyStepLogDao
        
        remoteConfigManager = mockk(relaxed = true)
        healthConnectManager = mockk(relaxed = true)
        leaderboardRepository = mockk(relaxed = true)
        functions = mockk(relaxed = true)
        firestore = mockk(relaxed = true)
        context = mockk(relaxed = true)
        raceLocationManager = mockk(relaxed = true)
        externalScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        
        trackRepository = mockk(relaxed = true)
        badgeManager = mockk(relaxed = true)
        leagueManager = mockk(relaxed = true)
        leagueRepository = mockk(relaxed = true)
        periodCalculator = mockk(relaxed = true)
        syncStepsUseCase = mockk(relaxed = true)
        
        // 🆕 P2 FIX: Constructor injection ile tüm bağımlılıklar enjekte edildi
        stepSyncManager = StepSyncManager(
            firestore,
            context,
            appDatabase,
            authRepository,
            remoteConfigManager,
            healthConnectManager,
            leaderboardRepository,
            functions,
            raceLocationManager,
            externalScope,
            trackRepository,
            badgeManager,
            dagger.Lazy { leagueManager },
            leagueRepository,
            syncStepsUseCase
        )
    }

    @Test
    fun `syncIfNeeded should delegate to syncStepsUseCase`() = runBlocking {
        // Arrange
        val monthlySteps = 1000L
        val activeTrackId = "track_1"
        val force = true
        
        coEvery { syncStepsUseCase(any(), any(), any()) } returns com.pace.legends.domain.usecase.sync.SyncResult.Success(100L)

        // Act
        stepSyncManager.syncIfNeeded(monthlySteps, activeTrackId, force)

        // Assert
        coVerify { syncStepsUseCase(monthlySteps, activeTrackId, force) }
    }
}
