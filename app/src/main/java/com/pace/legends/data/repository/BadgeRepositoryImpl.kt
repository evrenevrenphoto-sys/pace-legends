package com.pace.legends.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.pace.legends.data.local.UserBadgeDao
import com.pace.legends.domain.model.BadgeType
import com.pace.legends.domain.repository.BadgeRepository
import com.pace.legends.domain.repository.ChampionBadge
import com.pace.legends.domain.repository.EarnedBadge
import com.pace.legends.domain.repository.PeriodHistoryEntry
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of BadgeRepository.
 * 
 * Combines Room (local badges) and Firestore (champion badges) data sources.
 */
@Singleton
class BadgeRepositoryImpl @Inject constructor(
    private val badgeDao: UserBadgeDao,
    private val firestore: FirebaseFirestore
) : BadgeRepository {
    
    override suspend fun getEarnedBadges(): List<EarnedBadge> {
        return try {
            badgeDao.getEarnedBadges().map { entity ->
                EarnedBadge(
                    badgeId = entity.badgeId,
                    badgeType = BadgeType.entries.find { it.id == entity.badgeId } ?: BadgeType.FORMATION_LAP,
                    earnedTimestamp = entity.earnedTimestamp
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("BadgeRepo", "Failed to get earned badges: ${e.message}")
            emptyList()
        }
    }
    
    override suspend fun getChampionBadges(userId: String): List<ChampionBadge> {
        return try {
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("championBadges")
                .orderBy("earnedAt", Query.Direction.DESCENDING)
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                try {
                    ChampionBadge(
                        badgeId = doc.getString("badgeId") ?: "",
                        periodId = doc.getString("periodId") ?: "",
                        periodDisplayName = doc.getString("periodDisplayName") ?: "Period",
                        trackId = doc.getString("trackId") ?: "",
                        trackDisplayName = doc.getString("trackDisplayName") ?: "Track",
                        rank = doc.getLong("rank")?.toInt() ?: 0,
                        earnedAt = doc.getLong("earnedAt") ?: 0L
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BadgeRepo", "Failed to get champion badges: ${e.message}")
            emptyList()
        }
    }
    
    override suspend fun awardChampionBadge(userId: String, badge: ChampionBadge): Result<Unit> {
        return try {
            val badgeData = mapOf(
                "badgeId" to badge.badgeId,
                "periodId" to badge.periodId,
                "periodDisplayName" to badge.periodDisplayName,
                "trackId" to badge.trackId,
                "trackDisplayName" to badge.trackDisplayName,
                "rank" to badge.rank,
                "earnedAt" to badge.earnedAt
            )
            
            firestore.collection("users")
                .document(userId)
                .collection("championBadges")
                .document(badge.badgeId)
                .set(badgeData)
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun hasChampionBadge(userId: String, badgeId: String): Boolean {
        return try {
            val doc = firestore.collection("users")
                .document(userId)
                .collection("championBadges")
                .document(badgeId)
                .get()
                .await()
            doc.exists()
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun getPeriodHistory(userId: String): List<PeriodHistoryEntry> {
        return try {
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("periodHistory")
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                try {
                    PeriodHistoryEntry(
                        periodId = doc.getString("periodId") ?: return@mapNotNull null,
                        trackId = doc.getString("trackId") ?: return@mapNotNull null,
                        displayName = doc.getString("displayName"),
                        finalRank = doc.getLong("finalRank")?.toInt()
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BadgeRepo", "Failed to get period history: ${e.message}")
            emptyList()
        }
    }
    
    override suspend fun getRankFromLeaderboard(trackId: String, periodId: String, userId: String): Int? {
        return try {
            val snapshot = firestore
                .collection("leaderboards")
                .document(trackId)
                .collection("monthly")
                .document(periodId)
                .collection("entries")
                .orderBy("steps", Query.Direction.DESCENDING)
                .limit(10)
                .get()
                .await()
            
            var rank = 1
            for (doc in snapshot.documents) {
                if (doc.id == userId) {
                    return rank
                }
                rank++
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
