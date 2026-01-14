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
    /**
     * Calculates All-Time stats by aggregating Firestore history and adding current period live data.
     */
    override suspend fun getAllTimeStats(): Result<AllTimeStats> {
        val userId = authRepository.getCurrentUserId() 
            ?: return Result.failure(Exception("User not found"))

        // 1. Fetch all completed periods from Firestore (The History Source of Truth)
        val historyRef = firestore
            .collection("users")
            .document(userId)
            .collection("periodHistory")
            
        return try {
            val historySnapshots = historyRef.get().await()
    
            var historySteps = 0L
            var historyLaps = 0
            var historyDistance = 0.0
    
            historySnapshots?.forEach { doc ->
                historySteps += doc.getLong("totalSteps") ?: 0L
                historyLaps += doc.getLong("completedLaps")?.toInt() ?: 0
                historyDistance += doc.getDouble("totalDistance") ?: 0.0
            }
    
            // 2. Add Current Period Data (The Live Source of Truth)
            // Note: If StepRepository methods fail (throw), they will be caught below.
            // If they return safe values/defaults internally, we get those.
            val currentPeriodSteps = stepRepository.getFreshCurrentPeriodSteps()
            val activeTrackId = stepRepository.currentTrackId.value
            val currentProgress = if (activeTrackId != null) stepRepository.getProgressByTrack(activeTrackId) else null
            val currentLaps = currentProgress?.completedLoops ?: 0
            
            val currentDistance = currentPeriodSteps * STEP_TO_METERS
    
            Result.success(AllTimeStats(
                totalSteps = historySteps + currentPeriodSteps,
                totalLaps = historyLaps + currentLaps,
                totalDistance = historyDistance + currentDistance
            ))
        } catch (e: Exception) {
            android.util.Log.e("StatsRepository", "Failed to fetch stats: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun getPeriodHistory(userId: String): Result<List<com.pace.legends.domain.model.PeriodHistory>> {
        return try {
            // Entity → Domain mapping
            val history = periodHistoryDao.getAllPeriods(userId).map { it.toDomain() }
            Result.success(history)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getStepsByTimeRange(startTime: java.time.Instant, endTime: java.time.Instant): Result<Long> {
         // StepRepository zaten bu işi yapıyor, onu delegate ediyoruz
         // Ancak DAO'ya doğrudan erişim gerekirse dailyStepLogDao burada kullanılabilir.
         // Şimdilik consistency için StepRepository kullanıyoruz.
         return try {
             val steps = stepRepository.getStepsByTimeRange(startTime, endTime)
             Result.success(steps)
         } catch (e: Exception) {
             Result.failure(e)
         }
    }
}
