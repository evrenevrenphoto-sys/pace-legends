package com.pace.legends.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "lap_history",
    indices = [
        androidx.room.Index(value = ["userId", "trackId"]),
        androidx.room.Index(value = ["endTime"])
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
data class LapHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val trackId: String,
    val userId: String,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Long,
    val totalSteps: Long, // Steps in this specific lap
    val lapNumber: Int,
    val periodId: String // e.g. "2026-01" or "season_1"
)
