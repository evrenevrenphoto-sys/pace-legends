package com.pace.legends.domain.usecase.sync

import com.pace.legends.domain.repository.AuthRepository
import com.pace.legends.domain.repository.StepSyncRepository
import javax.inject.Inject

/**
 * 🆕 UseCase for initializing sync state on app startup.
 * 
 * This MUST be called ONCE after successful login/auth restoration,
 * BEFORE any sync operations occur.
 * 
 * Handles the "fresh install" problem:
 * - App reinstalled -> SharedPreferences empty (0 steps)
 * - Health Connect has 100k steps
 * - Without initialization: delta = 100k (WRONG!)
 * - With initialization: fetch cloud state first, delta = actual new steps
 * 
 * Usage in MainActivity or MainViewModel:
 * ```
 * viewModelScope.launch {
 *     syncInitializer()
 * }
 * ```
 */
class SyncInitializer @Inject constructor(
    private val authRepository: AuthRepository,
    private val stepSyncRepository: StepSyncRepository
) {
    /**
     * Initialize sync state from cloud.
     * 
     * @return true if initialization succeeded (or was skipped because state exists)
     * @return false if initialization failed (network error, etc.)
     */
    suspend operator fun invoke(): Boolean {
        val userId = authRepository.getCurrentUserId()
        
        if (userId == null) {
            android.util.Log.w("SyncInitializer", "⚠️ No user logged in, skipping sync init")
            return false
        }
        
        return stepSyncRepository.initializeSyncState(userId).fold(
            onSuccess = {
                android.util.Log.i("SyncInitializer", "✅ Sync state initialized for $userId")
                true
            },
            onFailure = { error ->
                android.util.Log.e("SyncInitializer", "❌ Failed to init sync state: ${error.message}")
                false
            }
        )
    }
}
