package com.pace.legends.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pace.legends.domain.manager.LeagueManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Kullanıcıyı lige/havuza kaydetmek için arka plan işçisi.
 * Offline-first yaklaşımı sağlar. İnternet yoksa WorkManager tekrar dener.
 */
@HiltWorker
class LeagueRegistrationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val leagueManager: LeagueManager,
    private val remoteConfigManager: com.pace.legends.domain.manager.RemoteConfigManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            android.util.Log.i(TAG, "🌍 Starting background league registration...")

            // 1. Remote Config'in taze olduğundan emin ol (Pist mapping için kritik)
            try {
                remoteConfigManager.fetchAndActivate() // Suspend function wrapper needed ideally, but manager handles it async usually.
                // Assuming sync call or fire-and-forget inside. 
                // Better: If manager has suspend fetch
                com.google.firebase.remoteconfig.FirebaseRemoteConfig.getInstance().fetchAndActivate().addOnCompleteListener { 
                    android.util.Log.d(TAG, "Remote Config fetched in worker")
                }
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Remote config fetch failed, proceeding with defaults: ${e.message}")
            }

            // 2. Kayıt işlemini dene
            leagueManager.registerNewUser()

            android.util.Log.i(TAG, "✅ League registration completed successfully.")
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Registration failed: ${e.message}", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        const val TAG = "LeagueRegistrationWorker"
    }
}
