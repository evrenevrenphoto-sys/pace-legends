package com.pace.legends.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.pace.legends.domain.repository.StepSyncRepository
import com.pace.legends.domain.repository.SyncOutcome
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of StepSyncRepository.
 * 
 * Uses Firestore transactions for atomic sync operations
 * to prevent double-counting via idempotency tokens.
 */
@Singleton
class StepSyncRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val remoteConfig: FirebaseRemoteConfig,
    @ApplicationContext private val context: Context
) : StepSyncRepository {
    
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("step_sync", Context.MODE_PRIVATE)
    
    companion object {
        private const val PREF_LAST_SYNCED_STEPS = "last_synced_steps"
        private const val PREF_LAST_SYNC_TIME = "last_sync_time"
    }
    
    /**
     * Sync steps to Firestore using a transaction for atomicity.
     * 
     * The transaction:
     * 1. Checks if syncToken already exists (idempotency)
     * 2. Writes the sync event
     * 3. Updates user document with new step count
     * 
     * All or nothing - prevents partial writes.
     */
    override suspend fun syncStepsToRemote(
        userId: String,
        stepDelta: Long,
        trackId: String,
        syncToken: String,
        displayName: String
    ): Result<SyncOutcome> {
        return try {
            val userRef = firestore.collection("users").document(userId)
            val syncEventRef = userRef.collection("stepSyncEvents").document(syncToken)
            
            firestore.runTransaction { transaction ->
                // 1. Check if token already exists (idempotency)
                val syncEventSnapshot = transaction.get(syncEventRef)
                if (syncEventSnapshot.exists()) {
                    // Already processed - idempotent return
                    return@runTransaction SyncOutcome.AlreadySynced
                }
                
                // 2. Write sync event (for idempotency tracking)
                // Stores DELTA now, not total
                val syncEventData = mapOf(
                    "stepDelta" to stepDelta,
                    "trackId" to trackId,
                    "timestamp" to FieldValue.serverTimestamp(),
                    "processed" to true
                )
                transaction.set(syncEventRef, syncEventData)
                
                // 3. Update user document (Increment + Merge)
                // Use FieldValue.increment to resolve Race Conditions
                val userData = mapOf(
                    "monthlySteps" to FieldValue.increment(stepDelta),
                    "activeTrackId" to trackId,
                    "lastSyncTimestamp" to FieldValue.serverTimestamp(),
                    "displayName" to displayName
                )
                transaction.set(userRef, userData, SetOptions.merge())
                
                SyncOutcome.Success
            }.await()
            
            Result.success(SyncOutcome.Success)
            
        } catch (e: Exception) {
            android.util.Log.e("StepSyncRepo", "Sync transaction failed: ${e.message}")
            Result.failure(e)
        }
    }
    
    override suspend fun checkSyncTokenExists(userId: String, syncToken: String): Boolean {
        return try {
            val doc = firestore
                .collection("users")
                .document(userId)
                .collection("stepSyncEvents")
                .document(syncToken)
                .get()
                .await()
            doc.exists()
        } catch (e: Exception) {
            false
        }
    }
    
    override fun getLastSyncedSteps(): Long {
        return prefs.getLong(PREF_LAST_SYNCED_STEPS, 0L)
    }
    
    override fun getLastSyncTime(): Long {
        return prefs.getLong(PREF_LAST_SYNC_TIME, 0L)
    }
    
    override fun updateSyncState(steps: Long, time: Long) {
        prefs.edit()
            .putLong(PREF_LAST_SYNCED_STEPS, steps)
            .putLong(PREF_LAST_SYNC_TIME, time)
            .apply()
    }
    
    /**
     * 🆕 Initialize sync state from cloud on fresh install/reinstall.
     * 
     * Problem: When app is reinstalled, SharedPreferences is empty (0 steps)
     * but Health Connect may have 100k steps. This causes a massive false delta.
     * 
     * Solution: On first launch, fetch user's monthlySteps from Firestore
     * and initialize local state to match cloud state.
     * 
     * Call this ONCE after successful login, before any sync operations.
     */
    override suspend fun initializeSyncState(userId: String): Result<Unit> {
        return try {
            // Check if we already have local state (not a fresh install)
            val localSteps = getLastSyncedSteps()
            val localTime = getLastSyncTime()
            
            // If local state exists and is recent (within 7 days), skip cloud fetch
            val sevenDaysMs = 7 * 24 * 60 * 60 * 1000L
            if (localSteps > 0 && (System.currentTimeMillis() - localTime) < sevenDaysMs) {
                android.util.Log.d("StepSyncRepo", "📱 Local state exists, skipping cloud restore")
                return Result.success(Unit)
            }
            
            // Fetch cloud state
            val userDoc = firestore.collection("users")
                .document(userId)
                .get()
                .await()
            
            if (userDoc.exists()) {
                val cloudSteps = userDoc.getLong("monthlySteps") ?: 0L
                val cloudTimestamp = userDoc.getTimestamp("lastSyncTimestamp")
                    ?.toDate()?.time ?: System.currentTimeMillis()
                
                // Update local state to match cloud
                if (cloudSteps > 0) {
                    updateSyncState(cloudSteps, cloudTimestamp)
                    android.util.Log.i("StepSyncRepo", "☁️ Restored cloud state: $cloudSteps steps")
                } else {
                    android.util.Log.d("StepSyncRepo", "☁️ Cloud state is 0, fresh user")
                }
            } else {
                android.util.Log.d("StepSyncRepo", "☁️ No cloud document, new user")
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("StepSyncRepo", "❌ Failed to restore cloud state: ${e.message}")
            // Don't fail silently - this is critical
            Result.failure(e)
        }
    }

    override fun getSyncInterval(): Long {
        return remoteConfig.getLong("sync_interval_ms").takeIf { it > 0 } ?: (15 * 60 * 1000L)
    }

    override fun getStepThreshold(): Long {
        return remoteConfig.getLong("sync_step_threshold").takeIf { it > 0 } ?: 500L
    }
    
    override suspend fun logCheatAttempt(
        userId: String,
        type: String,
        steps: Long,
        durationMs: Long,
        speedKmph: Double
    ) {
        try {
            val data = hashMapOf(
                "userId" to userId,
                "type" to type,
                "steps" to steps,
                "durationMs" to durationMs,
                "speedKmph" to speedKmph,
                "timestamp" to System.currentTimeMillis()
            )
            
            functions
                .getHttpsCallable("logCheatAttempt")
                .call(data)
                .await()
                
            android.util.Log.w("AntiCheat", "🛡️ Cheat attempt logged: $type")
            
        } catch (e: Exception) {
            // Silent fail - don't block sync for logging failure
            android.util.Log.e("AntiCheat", "Failed to log cheat: ${e.message}")
        }
    }
}
