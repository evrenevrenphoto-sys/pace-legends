package com.pace.legends.domain.repository

/**
 * Step Sync Repository Interface
 * 
 * Handles sync operations to Firestore with idempotency support.
 * All sync state (throttling) is managed here.
 */
interface StepSyncRepository {
    
    /**
     * Sync steps to remote with idempotency token.
     * 
     * Implementation MUST use Firestore transaction to atomically:
     * 1. Check/write stepSyncEvents/{token}
     * 2. Update users/{uid} monthlySteps (Increment)
     * 
     * @param userId User ID
     * @param stepDelta Steps to ADD (Increment)
     * @param trackId Active track ID
     * @param syncToken UUID for idempotency
     * @param displayName User display name for leaderboard
     * @return Result with SyncOutcome
     */
    suspend fun syncStepsToRemote(
        userId: String,
        stepDelta: Long,
        trackId: String,
        syncToken: String,
        displayName: String
    ): Result<SyncOutcome>
    
    /**
     * Check if a sync token already exists (was already processed).
     */
    suspend fun checkSyncTokenExists(userId: String, syncToken: String): Boolean
    
    // Throttle state management
    fun getLastSyncedSteps(): Long
    fun getLastSyncTime(): Long
    fun updateSyncState(steps: Long, time: Long)
    
    // Remote Config
    fun getSyncInterval(): Long
    fun getStepThreshold(): Long
    
    /**
     * Log anti-cheat violation to server.
     */
    suspend fun logCheatAttempt(
        userId: String,
        type: String,
        steps: Long,
        durationMs: Long,
        speedKmph: Double
    )
    
    /**
     * 🆕 Initialize sync state from cloud on fresh install/reinstall.
     * 
     * MUST be called ONCE after successful login, before any sync operations.
     * This ensures local state matches cloud state, preventing false deltas.
     * 
     * @param userId Current user ID
     * @return Result indicating success or failure
     */
    suspend fun initializeSyncState(userId: String): Result<Unit>
}

/**
 * Outcome of a sync operation.
 */
sealed class SyncOutcome {
    /** Sync completed successfully */
    data object Success : SyncOutcome()
    
    /** Sync was skipped because token already exists (idempotent) */
    data object AlreadySynced : SyncOutcome()
    
    /** Sync was rejected due to anti-cheat violation */
    data class CheatDetected(val reason: String) : SyncOutcome()
}
