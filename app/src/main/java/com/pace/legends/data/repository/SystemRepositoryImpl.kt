package com.pace.legends.data.repository

import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.pace.legends.domain.repository.SystemRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of SystemRepository.
 * 
 * Handles system-level operations that require Context.
 * This keeps Context confined to the data layer as per Clean Architecture.
 */
@Singleton
class SystemRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : SystemRepository {
    
    override suspend fun clearAllLocalData(clearCloudData: Boolean): Result<Unit> {
        return try {
            // 1. Optionally clear Firestore user data
            if (clearCloudData) {
                val userId = auth.currentUser?.uid
                if (userId != null) {
                    try {
                        firestore.collection("users").document(userId).delete().await()
                    } catch (e: Exception) {
                        android.util.Log.w("SystemRepo", "Firestore cleanup partial fail: ${e.message}")
                    }
                }
            }
            
            // 2. Delete Room Database
            context.deleteDatabase("pace_legends_db")
            
            // 3. Clear all SharedPreferences
            val prefNames = listOf(
                "pace_legends_race",
                "step_sync",
                "health_connect_cache"
            )
            prefNames.forEach { prefName ->
                context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .apply()
            }
            
            // 4. Set "fresh start" flag to prevent zombie data restoration
            context.getSharedPreferences("pace_legends_race", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("is_fresh_start", true)
                .apply()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun restartApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Runtime.getRuntime().exit(0)
        }
    }
}
