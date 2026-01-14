package com.pace.legends.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pace.legends.domain.repository.StepRepository
import com.pace.legends.domain.usecase.sync.CheckPeriodTransitionUseCase
import com.pace.legends.domain.usecase.sync.SyncResult
import com.pace.legends.domain.usecase.sync.SyncStepsUseCase
import com.pace.legends.domain.usecase.sync.TransitionResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periyodik arka plan sync worker'ı
 * 
 * Clean Architecture: Worker sadece UseCase'leri çağırır.
 * İş mantığı içermez ("Dumb Worker" pattern).
 * 
 * Her 15 dakikada bir çalışır:
 * 1. Dönem geçişi kontrolü (lig yükselme/düşme)
 * 2. Health Connect sync
 * 3. Firestore sync (UseCase üzerinden)
 */
@HiltWorker
class DataSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val stepRepository: StepRepository,
    private val syncStepsUseCase: SyncStepsUseCase,
    private val checkPeriodTransitionUseCase: CheckPeriodTransitionUseCase
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            android.util.Log.i("DataSyncWorker", "🔄 Background sync started")
            
            // 1. Check period transition (league promotion/demotion)
            when (val transitionResult = checkPeriodTransitionUseCase()) {
                is TransitionResult.PeriodChanged -> {
                    android.util.Log.i("DataSyncWorker", "🎉 Period changed! Archival processed.")
                }
                is TransitionResult.Error -> {
                    android.util.Log.w("DataSyncWorker", "⚠️ Period check error: ${transitionResult.message}")
                }
                else -> { /* No change */ }
            }
            
            // 2. Sync Health Connect data
            try {
                stepRepository.syncHealthConnectSteps(force = true)
                android.util.Log.d("DataSyncWorker", "✅ Health Connect synced")
            } catch (e: Exception) {
                android.util.Log.e("DataSyncWorker", "❌ Health Connect sync failed: ${e.message}")
                // Continue with Firestore sync even if HC fails
            }
            
            // 3. Sync to Firestore via UseCase
            val monthlySteps = stepRepository.monthlySteps.value
            val activeTrackId = stepRepository.currentTrackId.value
            
            val syncResult = syncStepsUseCase(
                monthlySteps = monthlySteps,
                activeTrackId = activeTrackId,
                force = true // Background sync always forces
            )
            
            // 🆕 P3: Propagate success to local DB
            if (syncResult is SyncResult.Success && activeTrackId != null) {
                stepRepository.markSynced(activeTrackId)
            }
            
            // 4. Map result to Worker Result
            mapSyncResultToWorkerResult(syncResult)
            
        } catch (e: Exception) {
            android.util.Log.e("DataSyncWorker", "❌ Worker failed: ${e.message}", e)
            handleWorkerException(e)
        }
    }
    
    /**
     * Map SyncResult to WorkManager Result.
     * 
     * - Success/Throttled/Skipped -> Result.success()
     * - Failed (Network) -> Result.retry() with backoff
     * - CheatRejected -> Result.failure() (permanent, no retry)
     */
    private fun mapSyncResultToWorkerResult(syncResult: SyncResult): Result {
        return when (syncResult) {
            is SyncResult.Success -> {
                android.util.Log.d("DataSyncWorker", "✅ Sync success: ${syncResult.syncedSteps} steps")
                Result.success()
            }
            is SyncResult.Throttled -> {
                android.util.Log.d("DataSyncWorker", "⏸️ Sync throttled")
                Result.success() // Not an error, just nothing to do
            }
            is SyncResult.Skipped -> {
                android.util.Log.d("DataSyncWorker", "⏭️ Sync skipped: ${syncResult.reason}")
                Result.success()
            }
            is SyncResult.Failed -> {
                android.util.Log.w("DataSyncWorker", "❌ Sync failed: ${syncResult.error}")
                if (runAttemptCount < 3) {
                    android.util.Log.w("DataSyncWorker", "🔄 Retry attempt ${runAttemptCount + 1}/3")
                    Result.retry()
                } else {
                    android.util.Log.e("DataSyncWorker", "🛑 Max retries reached")
                    Result.failure()
                }
            }
            is SyncResult.CheatRejected -> {
                // Permanent failure - do NOT retry (anti-cheat)
                android.util.Log.w("DataSyncWorker", "🛡️ Cheat detected, sync rejected (no retry)")
                Result.failure()
            }
        }
    }
    
    private fun handleWorkerException(e: Exception): Result {
        return if (runAttemptCount < 3) {
            android.util.Log.w("DataSyncWorker", "🔄 Retry attempt ${runAttemptCount + 1}/3")
            Result.retry()
        } else {
            android.util.Log.e("DataSyncWorker", "🛑 Max retries reached, giving up")
            Result.failure()
        }
    }
}
