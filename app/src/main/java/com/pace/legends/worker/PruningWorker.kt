package com.pace.legends.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pace.legends.data.local.AppDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate

@HiltWorker
class PruningWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val db: AppDatabase
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val now = System.currentTimeMillis()
            val threeMonthsAgo = now - (90L * 24 * 60 * 60 * 1000)
            
            // 1. Prune Lap History (older than 3 months)
            val deletedLaps = db.lapHistoryDao().deleteOldLaps(threeMonthsAgo)
            
            // 2. Prune Daily Logs (older than 3 months)
            // Calculate Epoch Day for 3 months ago
            val pruneDate = LocalDate.now().minusDays(90)
            val pruneEpochDay = pruneDate.toEpochDay()
            
            db.dailyStepLogDao().deleteOldLogs(pruneEpochDay)
            
            android.util.Log.i("PruningWorker", "✂️ Data pruning completed. Cleared info older than 90 days.")
            
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("PruningWorker", "❌ Pruning failed: ${e.message}")
            Result.retry()
        }
    }
}
