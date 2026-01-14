package com.pace.legends.domain.model

import androidx.room.Entity

@Entity(
    tableName = "daily_step_log",
    primaryKeys = ["epochDay", "trackId", "userId"],
    indices = [
        androidx.room.Index(value = ["userId", "trackId"]),
        androidx.room.Index(value = ["epochDay"]) // Range queries
    ],
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = com.pace.legends.data.local.entity.UserProgressEntity::class,
            parentColumns = ["userId", "trackId"],
            childColumns = ["userId", "trackId"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ]
)
data class DailyStepLog(
    val epochDay: Long, // Days since epoch (Integer for performance)
    val trackId: String,
    val userId: String,
    val steps: Long,
    val distance: Double,
    val dateString: String // For display/debug only, not indexed
)
