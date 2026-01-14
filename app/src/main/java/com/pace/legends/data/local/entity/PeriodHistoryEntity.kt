package com.pace.legends.data.local.entity

import androidx.room.Entity
import com.pace.legends.domain.model.PeriodHistory

/**
 * Room Entity for period history storage.
 * Maps to/from domain PeriodHistory model.
 */
@Entity(
    tableName = "period_history",
    primaryKeys = ["periodId", "userId", "trackId"]
)
data class PeriodHistoryEntity(
    val periodId: String,
    val userId: String,
    val trackId: String,
    val displayName: String?,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val totalSteps: Long,
    val completedLaps: Int,
    val bestLapTimeSeconds: Long,
    val finalRank: Int?,
    val totalParticipants: Int?,
    val archivedAt: Long
) {
    fun toDomain(): PeriodHistory = PeriodHistory(
        periodId = periodId,
        userId = userId,
        trackId = trackId,
        displayName = displayName,
        startTimestamp = startTimestamp,
        endTimestamp = endTimestamp,
        totalSteps = totalSteps,
        completedLaps = completedLaps,
        bestLapTimeSeconds = bestLapTimeSeconds,
        finalRank = finalRank,
        totalParticipants = totalParticipants,
        archivedAt = archivedAt
    )

    companion object {
        fun fromDomain(domain: PeriodHistory): PeriodHistoryEntity = PeriodHistoryEntity(
            periodId = domain.periodId,
            userId = domain.userId,
            trackId = domain.trackId,
            displayName = domain.displayName,
            startTimestamp = domain.startTimestamp,
            endTimestamp = domain.endTimestamp,
            totalSteps = domain.totalSteps,
            completedLaps = domain.completedLaps,
            bestLapTimeSeconds = domain.bestLapTimeSeconds,
            finalRank = domain.finalRank,
            totalParticipants = domain.totalParticipants,
            archivedAt = domain.archivedAt
        )
    }
}
