package com.pace.legends.domain.model

import androidx.room.Entity

@Entity(
    tableName = "daily_step_log",
    primaryKeys = ["epochDay", "trackId", "userId"]
)
data class DailyStepLog(
    val epochDay: Long, // Days since epoch (Integer for performance)
    val trackId: String,
    val userId: String,
    val steps: Long,
    val distance: Double,
    val dateString: String // For display/debug only, not indexed
)
