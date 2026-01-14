package com.pace.legends.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pace.legends.domain.model.UserProgress

@Entity(
    tableName = "user_progress",
    primaryKeys = ["userId", "trackId"],
    indices = [
        Index(value = ["isSynced"]),  // 🆕 Sync sorguları için kritik (Full Table Scan önleme)
        Index(value = ["trackId"])     // 🆕 Pist detay sayfası açılışı için kritik
    ]
)
data class UserProgressEntity(
    val trackId: String,
    val userId: String,
    val displayName: String,
    val totalSteps: Long,
    val completedLoops: Int,
    val totalActiveTimeMillis: Long,
    val lastUpdateTimestamp: Long,
    val bestLapTimeSeconds: Long,
    val isSynced: Boolean,
    val lastSyncedTimestamp: Long,
    val totalDistanceWalked: Double,
    val currentLapStartTime: Long,
    val periodStartTime: Long,
    val currentLapNumber: Int,
    val allTimeSteps: Long,
    val allTimeLaps: Int,
    val allTimeDistanceMeters: Double
) {
    fun toDomain(): UserProgress {
        return UserProgress(
            trackId = trackId,
            userId = userId,
            displayName = displayName,
            totalSteps = totalSteps,
            completedLoops = completedLoops,
            totalActiveTimeMillis = totalActiveTimeMillis,
            lastUpdateTimestamp = lastUpdateTimestamp,
            bestLapTimeSeconds = bestLapTimeSeconds,
            isSynced = isSynced,
            lastSyncedTimestamp = lastSyncedTimestamp,
            totalDistanceWalked = totalDistanceWalked,
            currentLapStartTime = currentLapStartTime,
            periodStartTime = periodStartTime,
            currentLapNumber = currentLapNumber,
            allTimeSteps = allTimeSteps,
            allTimeLaps = allTimeLaps,
            allTimeDistanceMeters = allTimeDistanceMeters
        )
    }

    companion object {
        fun fromDomain(domain: UserProgress): UserProgressEntity {
            return UserProgressEntity(
                trackId = domain.trackId,
                userId = domain.userId,
                displayName = domain.displayName,
                totalSteps = domain.totalSteps,
                completedLoops = domain.completedLoops,
                totalActiveTimeMillis = domain.totalActiveTimeMillis,
                lastUpdateTimestamp = domain.lastUpdateTimestamp,
                bestLapTimeSeconds = domain.bestLapTimeSeconds,
                isSynced = domain.isSynced,
                lastSyncedTimestamp = domain.lastSyncedTimestamp,
                totalDistanceWalked = domain.totalDistanceWalked,
                currentLapStartTime = domain.currentLapStartTime,
                periodStartTime = domain.periodStartTime,
                currentLapNumber = domain.currentLapNumber,
                allTimeSteps = domain.allTimeSteps,
                allTimeLaps = domain.allTimeLaps,
                allTimeDistanceMeters = domain.allTimeDistanceMeters
            )
        }
    }
}
