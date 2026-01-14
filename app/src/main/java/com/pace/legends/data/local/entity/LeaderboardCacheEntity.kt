package com.pace.legends.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "leaderboard_cache",
    primaryKeys = ["trackId", "periodId", "userId"]
)
data class LeaderboardCacheEntity(
    val trackId: String,
    val periodId: String,
    val userId: String,
    val displayName: String,
    val steps: Long,
    val rank: Int,
    val timestamp: Long // When this data was fetched
)
