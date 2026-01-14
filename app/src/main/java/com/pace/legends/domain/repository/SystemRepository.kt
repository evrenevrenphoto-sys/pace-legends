package com.pace.legends.domain.repository

/**
 * System-level operations repository.
 * Handles app-wide data clearing and restart operations.
 * 
 * Implementation lives in data layer with Context access.
 */
interface SystemRepository {
    /**
     * Clears all local data including:
     * - Room Database
     * - SharedPreferences
     * - Firestore user document (optional, for full reset)
     * 
     * @param clearCloudData If true, also deletes Firestore user data.
     * @return Result indicating success or failure.
     */
    suspend fun clearAllLocalData(clearCloudData: Boolean = false): Result<Unit>
    
    /**
     * Restarts the application.
     * Should be called after data clear for a clean state.
     */
    fun restartApp()
}
