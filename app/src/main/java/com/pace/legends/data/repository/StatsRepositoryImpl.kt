package com.pace.legends.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.pace.legends.domain.repository.AllTimeStats
import com.pace.legends.domain.repository.AuthRepository
import com.pace.legends.domain.repository.StatsRepository
import com.pace.legends.domain.repository.StepRepository
import com.pace.legends.domain.util.RaceProgressCalculator.STEP_TO_METERS
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val stepRepository: StepRepository,
    private val authRepository: AuthRepository,
    // 🆕 Refactoring: DAO'lar buraya taşındı
    private val periodHistoryDao: com.pace.legends.data.local.PeriodHistoryDao,
    private val dailyStepLogDao: com.pace.legends.data.local.DailyStepLogDao
) : StatsRepository {
    /**
     * Calculates All-Time stats by aggregating Firestore history and adding current period live data.
     */
    override suspend fun getAllTimeStats(): AllTimeStats {
        val userId = authRepository.getCurrentUserId() ?: return AllTimeStats(0, 0, 0.0)

        // 1. Fetch all completed periods from Firestore (The History Source of Truth)
        val historyRef = firestore
            .collection("users")
            .document(userId)
            .collection("periodHistory")
            
        val historySnapshots = try {
            historyRef.get().await()
        } catch (e: Exception) {
            android.util.Log.e("StatsRepository", "Failed to fetch history: ${e.message}")
            null
        }

        var historySteps = 0L
        var historyLaps = 0
        var historyDistance = 0.0

        historySnapshots?.forEach { doc ->
            historySteps += doc.getLong("totalSteps") ?: 0L
            historyLaps += doc.getLong("completedLaps")?.toInt() ?: 0
            historyDistance += doc.getDouble("totalDistance") ?: 0.0
        }

        // 2. Add Current Period Data (The Live Source of Truth)
        val currentPeriodSteps = stepRepository.getFreshCurrentPeriodSteps()
        val activeTrackId = stepRepository.currentTrackId.value
        val currentProgress = if (activeTrackId != null) stepRepository.getProgressByTrack(activeTrackId) else null
        val currentLaps = currentProgress?.completedLoops ?: 0
        
        val currentDistance = currentPeriodSteps * STEP_TO_METERS

        return AllTimeStats(
            totalSteps = historySteps + currentPeriodSteps,
            totalLaps = historyLaps + currentLaps,
            totalDistance = historyDistance + currentDistance
        )
    }

    override suspend fun getPeriodHistory(userId: String): List<com.pace.legends.domain.model.PeriodHistory> {
        // Entity → Domain mapping
        return periodHistoryDao.getAllPeriods(userId).map { it.toDomain() }
    }

    override suspend fun getStepsByTimeRange(startTime: java.time.Instant, endTime: java.time.Instant): Long {
         // StepRepository zaten bu işi yapıyor, onu delegate ediyoruz
         // Ancak DAO'ya doğrudan erişim gerekirse dailyStepLogDao burada kullanılabilir.
         // Şimdilik consistency için StepRepository kullanıyoruz.
         return stepRepository.getStepsByTimeRange(startTime, endTime)
    }
}
