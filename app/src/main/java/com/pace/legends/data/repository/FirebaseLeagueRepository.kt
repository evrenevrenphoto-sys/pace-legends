package com.pace.legends.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.pace.legends.domain.model.LeagueTier
import com.pace.legends.domain.model.UserLeagueInfo
import com.pace.legends.domain.repository.LeaderboardEntry
import com.pace.legends.domain.repository.LeagueRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase Lig Sistemi Repository
 * 
 * Firestore Yapısı:
 * - users/{userId} → leagueTier, leagueId alanları
 * - qualifying/{trackId}_{periodId}/entries/{userId} → Eleme Havuzu
 * - leagues/{leagueId}/members/{userId} → Lig üyeleri
 */
@Singleton
class FirebaseLeagueRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val remoteConfigManager: com.pace.legends.domain.manager.RemoteConfigManager // 🆕 Dinamik league_size için
) : LeagueRepository {

    companion object {
        // 🔄 LEAGUE_SIZE artık remoteConfigManager.leagueSize.value'dan geliyor
        private const val TAG = "LeagueRepo"
    }
    
    // Helper property for league size
    private val leagueSize: Int
        get() = remoteConfigManager.leagueSize.value

    override suspend fun getUserLeagueInfo(userId: String): Result<UserLeagueInfo> {
        return try {
            val userDoc = firestore.collection("users")
                .document(userId)
                .get()
                .await()
            
            val tierString = userDoc.getString("leagueTier") ?: "QUALIFYING"
            val leagueId = userDoc.getString("leagueId")
            
            val info = UserLeagueInfo(
                tier = LeagueTier.fromString(tierString),
                leagueId = leagueId
            )
            Result.success(info)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "getUserLeagueInfo failed: ${e.message}")
            Result.failure(e)
        }
    }





    override suspend fun getQualifyingPoolLeaderboard(
        trackId: String, 
        periodId: String, 
        limit: Int,
        lastSteps: Long?,
        lastUserId: String?
    ): Result<List<LeaderboardEntry>> {
        return try {
            var query = firestore
                .collection("qualifying")
                .document("${trackId}_${periodId}")
                .collection("entries")
                .orderBy("steps", Query.Direction.DESCENDING)
                .orderBy("userId", Query.Direction.ASCENDING)
                .limit(limit.toLong())
                
            if (lastSteps != null && lastUserId != null) {
                query = query.startAfter(lastSteps, lastUserId)
            }
            
            val result = query.get().await()
            
            val entries = result.documents.map { doc ->
                LeaderboardEntry(
                    userId = doc.getString("userId") ?: "",
                    displayName = doc.getString("displayName") ?: "Racer",
                    steps = doc.getLong("steps") ?: 0L,
                    rank = 0 // UI should calculate rank based on position + offset
                )
            }
            Result.success(entries)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "getQualifyingPoolLeaderboard failed: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun registerToQualifyingPool(userId: String, trackId: String, periodId: String) {
        try {
            val qualifyingRef = firestore
                .collection("qualifying")
                .document("${trackId}_${periodId}")
                .collection("entries")
                .document(userId)

            qualifyingRef.set(mapOf(
                "userId" to userId,
                "joinedAt" to FieldValue.serverTimestamp()
            ), SetOptions.merge()).await()
            
            android.util.Log.d(TAG, "✅ Registered to qualifying pool: $userId")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "registerToQualifyingPool failed: ${e.message}")
        }
    }

    override suspend fun getLeagueLeaderboard(
        leagueId: String, 
        limit: Int,
        lastSteps: Long?,
        lastUserId: String?
    ): Result<List<LeaderboardEntry>> {
        return try {
            var query = firestore
                .collection("leagues")
                .document(leagueId)
                .collection("members")
                .orderBy("steps", Query.Direction.DESCENDING)
                .orderBy("userId", Query.Direction.ASCENDING) // Stable sort for pagination
                .limit(limit.toLong())
                
            if (lastSteps != null && lastUserId != null) {
                query = query.startAfter(lastSteps, lastUserId)
            }
                
            val result = query.get().await()
            
            var localRankCounter = 0
            val entries = result.documents.map { doc ->
                localRankCounter++
                LeaderboardEntry(
                    userId = doc.getString("userId") ?: "",
                    displayName = doc.getString("displayName") ?: "Racer",
                    steps = doc.getLong("steps") ?: 0L,
                    rank = 0 // Rank handled by UI or separate count query
                )
            }
            Result.success(entries)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "getLeagueLeaderboard failed: ${e.message} (Check Indexes!)")
            Result.failure(e)
        }
    }

    override suspend fun updateUserLeague(
        userId: String, 
        newTier: LeagueTier, 
        newLeagueId: String?
    ): Result<Unit> {
        return try {
            firestore.collection("users")
                .document(userId)
                .set(mapOf(
                    "leagueTier" to newTier.name,
                    "leagueId" to newLeagueId
                ), SetOptions.merge())
                .await()
            
            android.util.Log.d(TAG, "✅ Updated user league: $userId -> ${newTier.name}")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "updateUserLeague failed: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun findAvailableLeague(tier: LeagueTier, trackId: String, maxMembers: Int): Result<String?> {
        return try {
            val result = firestore
                .collection("leagues")
                .whereEqualTo("tier", tier.name)
                .whereEqualTo("trackId", trackId)
                .whereLessThan("memberCount", maxMembers)
                .limit(1)
                .get()
                .await()
            
            Result.success(result.documents.firstOrNull()?.id)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "findAvailableLeague failed: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun createLeague(tier: LeagueTier, trackId: String): Result<String> {
        return try {
            val leagueRef = firestore.collection("leagues").document()
            
            leagueRef.set(mapOf(
                "tier" to tier.name,
                "trackId" to trackId,
                "memberCount" to 0,
                "createdAt" to FieldValue.serverTimestamp()
            )).await()
            
            android.util.Log.d(TAG, "✅ Created new league: ${leagueRef.id}")
            Result.success(leagueRef.id)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "createLeague failed: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * 🆕 Kullanıcıyı lig üyesi olarak ekle ve memberCount'u artır
     * BUG #2 ve BUG #3 düzeltmesi
     */
    override suspend fun addUserToLeague(userId: String, leagueId: String, displayName: String): Result<Unit> {
        return try {
            val batch = firestore.batch()
            
            // 1. leagues/{leagueId}/members/{userId} oluştur
            val memberRef = firestore
                .collection("leagues")
                .document(leagueId)
                .collection("members")
                .document(userId)
            
            batch.set(memberRef, mapOf(
                "userId" to userId,
                "displayName" to displayName,
                "steps" to 0L,
                "joinedAt" to FieldValue.serverTimestamp()
            ), SetOptions.merge())
            
            // 2. leagues/{leagueId} memberCount artır
            val leagueRef = firestore.collection("leagues").document(leagueId)
            batch.update(leagueRef, "memberCount", FieldValue.increment(1))
            
            batch.commit().await()
            
            android.util.Log.d(TAG, "✅ Added user $userId to league $leagueId and incremented memberCount")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "addUserToLeague failed: ${e.message}")
            Result.failure(e)
        }
    }



    override suspend fun getUserLeagueRank(userId: String, leagueId: String): Result<Int> {
        return try {
            val userDoc = firestore
                .collection("leagues")
                .document(leagueId)
                .collection("members")
                .document(userId)
                .get()
                .await()
            
            val userSteps = userDoc.getLong("steps") ?: 0L
            
            val higherCount = firestore
                .collection("leagues")
                .document(leagueId)
                .collection("members")
                .whereGreaterThan("steps", userSteps)
                .get()
                .await()
                .size()
            
            Result.success(higherCount + 1)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "getUserLeagueRank failed: ${e.message}")
            Result.failure(e)
        }
    }
    override suspend fun getUserQualifyingRank(userId: String, trackId: String, periodId: String): Int {
        return try {
            val userDoc = firestore
                .collection("qualifying")
                .document("${trackId}_${periodId}")
                .collection("entries")
                .document(userId)
                .get()
                .await()
            
            val userSteps = userDoc.getLong("steps") ?: 0L
            
            val higherCount = firestore
                .collection("qualifying")
                .document("${trackId}_${periodId}")
                .collection("entries")
                .whereGreaterThan("steps", userSteps)
                .get()
                .await()
                .size()
            
            higherCount + 1
        } catch (e: Exception) {
            android.util.Log.e(TAG, "getUserQualifyingRank failed: ${e.message}")
            0
        }
    }
    
    /**
     * 🆕 Kullanıcıyı ligden çıkar ve memberCount'u azalt
     */
    override suspend fun removeUserFromLeague(userId: String, leagueId: String): Result<Unit> {
        return try {
            val batch = firestore.batch()
            
            // 1. leagues/{leagueId}/members/{userId} sil
            val memberRef = firestore
                .collection("leagues")
                .document(leagueId)
                .collection("members")
                .document(userId)
            
            batch.delete(memberRef)
            
            // 2. leagues/{leagueId} memberCount azalt
            val leagueRef = firestore.collection("leagues").document(leagueId)
            batch.update(leagueRef, "memberCount", FieldValue.increment(-1))
            
            batch.commit().await()
            
            android.util.Log.d(TAG, "✅ Removed user $userId from league $leagueId and decremented memberCount")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "removeUserFromLeague failed: ${e.message}")
            Result.failure(e)
        }
    }
}
