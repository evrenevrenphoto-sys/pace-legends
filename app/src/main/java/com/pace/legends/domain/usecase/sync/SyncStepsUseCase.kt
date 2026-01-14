package com.pace.legends.domain.usecase.sync

import com.pace.legends.domain.repository.AuthRepository
import com.pace.legends.domain.repository.StepSyncRepository
import com.pace.legends.domain.repository.SyncOutcome
import java.util.UUID
import javax.inject.Inject

/**
 * UseCase for syncing steps to Firestore.
 * 
 * Implements:
 * - Throttling (500 steps / 15 min)
 * - Idempotency tokens (prevent double-counting)
 * - Anti-cheat speed validation
 * 
 * Based on Report Section 2.2.1: "Domain-First & UseCase Odaklı Yapı"
 */
class SyncStepsUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val stepSyncRepository: StepSyncRepository
) {
    companion object {
        // Constants replaced by Remote Config
        private const val SPEED_LIMIT_KMPH = 25.0             // Max sustained speed
        private const val SPEED_LIMIT_BURST_KMPH = 35.0       // Burst speed (< 60s)
        private const val AVG_STEP_LENGTH_METERS = 0.762      // Average step length
        
        /**
         * 🔒 MUST MATCH firestore.rules isValidStepDelta() limit
         * Firestore rejects writes with delta > 5000
         */
        private const val MAX_STEPS_PER_BATCH = 5000L
    }
    
    /**
     * Sync steps to Firestore if conditions are met.
     * 
     * 🆕 BATCHING: If step delta > MAX_STEPS_PER_BATCH (5000),
     * syncs will be split into multiple batches to comply with
     * Firestore security rules (offline/catchup scenarios).
     * 
     * @param monthlySteps Current monthly step count
     * @param activeTrackId Active track ID
     * @param force If true, skip throttling checks
     * @return SyncResult indicating outcome
     */
    suspend operator fun invoke(
        monthlySteps: Long,
        activeTrackId: String?,
        force: Boolean = false
    ): SyncResult {
        // Validation
        val userId = authRepository.getCurrentUserId()
            ?: return SyncResult.Skipped("No user signed in")
        
        if (activeTrackId == null) {
            return SyncResult.Skipped("No active track")
        }
        
        val now = System.currentTimeMillis()
        val lastSyncedSteps = stepSyncRepository.getLastSyncedSteps()
        val lastSyncTime = stepSyncRepository.getLastSyncTime()
        
        val stepDelta = monthlySteps - lastSyncedSteps
        val timeDelta = now - lastSyncTime
        
        // Nothing to sync
        if (stepDelta <= 0) {
            return SyncResult.Skipped("No new steps to sync")
        }
        
        // Throttle check (unless forced)
        if (!force) {
            val syncInterval = stepSyncRepository.getSyncInterval()
            val stepThreshold = stepSyncRepository.getStepThreshold()
            
            val shouldSync = stepDelta >= stepThreshold || timeDelta >= syncInterval
            if (!shouldSync) {
                return SyncResult.Throttled(
                    stepDelta = stepDelta,
                    requiredDelta = stepThreshold,
                    timeRemainingMs = syncInterval - timeDelta
                )
            }
        }
        
        // Anti-cheat: Speed validation (only for non-batched, real-time syncs)
        // Skip speed check for large deltas (offline catchup mode)
        if (stepDelta <= MAX_STEPS_PER_BATCH && timeDelta > 0) {
            val speedViolation = checkSpeedViolation(stepDelta, timeDelta)
            if (speedViolation != null) {
                stepSyncRepository.logCheatAttempt(
                    userId = userId,
                    type = "SPEED_VIOLATION",
                    steps = stepDelta,
                    durationMs = timeDelta,
                    speedKmph = speedViolation
                )
                return SyncResult.CheatRejected(speedViolation)
            }
        }
        
        // Get display name for leaderboard
        val displayName = authRepository.getCurrentUser()?.displayName 
            ?: "Racer ${userId.take(4)}"
        
        // 🆕 BATCHING: Split large syncs into chunks of MAX_STEPS_PER_BATCH
        // This handles offline scenarios where user accumulated > 5000 steps
        return if (stepDelta > MAX_STEPS_PER_BATCH) {
            syncInBatches(
                userId = userId,
                targetSteps = monthlySteps,
                lastSyncedSteps = lastSyncedSteps,
                trackId = activeTrackId,
                displayName = displayName,
                timestamp = now
            )
        } else {
            // Single sync
            performSingleSync(
                userId = userId,
                stepDelta = stepDelta,
                trackId = activeTrackId,
                displayName = displayName,
                timestamp = now,
                totalSteps = monthlySteps
            )
        }
    }
    
    /**
     * 🆕 Batch sync for large step deltas (offline catchup).
     * 
     * Splits the total delta into chunks of MAX_STEPS_PER_BATCH
     * and syncs each batch sequentially.
     */
    private suspend fun syncInBatches(
        userId: String,
        targetSteps: Long,
        lastSyncedSteps: Long,
        trackId: String,
        displayName: String,
        timestamp: Long
    ): SyncResult {
        var currentSyncedSteps = lastSyncedSteps
        var batchCount = 0
        
        android.util.Log.d("SyncSteps", "🔄 Starting batched sync: $lastSyncedSteps -> $targetSteps")
        
        while (currentSyncedSteps < targetSteps) {
            val nextBatchSteps = minOf(
                currentSyncedSteps + MAX_STEPS_PER_BATCH,
                targetSteps
            )
            val chunkDelta = nextBatchSteps - currentSyncedSteps
            
            val syncToken = UUID.randomUUID().toString()
            
            val result = stepSyncRepository.syncStepsToRemote(
                userId = userId,
                stepDelta = chunkDelta,
                trackId = trackId,
                syncToken = syncToken,
                displayName = displayName
            )
            
            if (result.isFailure) {
                android.util.Log.e("SyncSteps", "❌ Batch $batchCount failed at $currentSyncedSteps")
                // Update state to last successful batch
                stepSyncRepository.updateSyncState(currentSyncedSteps, timestamp)
                return SyncResult.Failed("Batch sync failed at step $currentSyncedSteps")
            }
            
            currentSyncedSteps = nextBatchSteps
            batchCount++
            
            android.util.Log.d("SyncSteps", "✅ Batch $batchCount complete: $currentSyncedSteps steps")
        }
        
        // All batches complete
        stepSyncRepository.updateSyncState(targetSteps, timestamp)
        android.util.Log.d("SyncSteps", "🎉 Batched sync complete: $batchCount batches, $targetSteps total steps")
        
        return SyncResult.Success(targetSteps)
    }
    
    /**
     * Single sync for normal (non-batched) operations.
     */
    private suspend fun performSingleSync(
        userId: String,
        stepDelta: Long,
        trackId: String,
        displayName: String,
        timestamp: Long,
        totalSteps: Long
    ): SyncResult {
        val syncToken = UUID.randomUUID().toString()
        
        return when (val outcome = stepSyncRepository.syncStepsToRemote(
            userId = userId,
            stepDelta = stepDelta,
            trackId = trackId,
            syncToken = syncToken,
            displayName = displayName
        ).getOrNull()) {
            is SyncOutcome.Success -> {
                stepSyncRepository.updateSyncState(totalSteps, timestamp)
                SyncResult.Success(totalSteps)
            }
            is SyncOutcome.AlreadySynced -> {
                SyncResult.Skipped("Already synced (idempotent)")
            }
            is SyncOutcome.CheatDetected -> {
                SyncResult.CheatRejected(0.0)
            }
            null -> {
                SyncResult.Failed("Sync failed")
            }
        }
    }
    
    /**
     * Check for speed violation.
     * @return Speed in km/h if violation detected, null otherwise
     */
    private fun checkSpeedViolation(stepDelta: Long, timeDeltaMs: Long): Double? {
        // Skip very short intervals with minimal steps (GPS glitch)
        if (timeDeltaMs < 5000 && stepDelta < 50) return null
        
        val timeSeconds = timeDeltaMs / 1000.0
        val distanceMeters = stepDelta * AVG_STEP_LENGTH_METERS
        val speedMps = distanceMeters / timeSeconds
        val speedKmph = speedMps * 3.6
        
        // Use burst threshold for short intervals, sustained for longer
        val threshold = if (timeSeconds < 60) SPEED_LIMIT_BURST_KMPH else SPEED_LIMIT_KMPH
        
        return if (speedKmph > threshold) speedKmph else null
    }
}

/**
 * Result of sync operation.
 */
sealed class SyncResult {
    /** Sync completed successfully */
    data class Success(val syncedSteps: Long) : SyncResult()
    
    /** Sync was throttled (not yet time/steps) */
    data class Throttled(
        val stepDelta: Long,
        val requiredDelta: Long,
        val timeRemainingMs: Long
    ) : SyncResult()
    
    /** Sync was skipped for a reason */
    data class Skipped(val reason: String) : SyncResult()
    
    /** Sync failed due to error */
    data class Failed(val error: String) : SyncResult()
    
    /** Sync rejected due to anti-cheat (silent) */
    data class CheatRejected(val detectedSpeedKmph: Double) : SyncResult()
    
    /** Check if this is a retryable failure (for Worker) */
    fun isRetryable(): Boolean = this is Failed
    
    /** Check if this is a permanent failure (for Worker) */
    fun isPermanentFailure(): Boolean = this is CheatRejected
}
