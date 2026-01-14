package com.pace.legends.domain.model

/**
 * Domain model for period history.
 * Pure Kotlin data class - no framework dependencies.
 */
data class PeriodHistory(
    val periodId: String,           // "period_0", "period_1", ...
    val userId: String,
    val trackId: String,
    val displayName: String?,       // Firebase'den: "Sevgililer Günü Sprintu"
    val startTimestamp: Long,       // Period başlangıç
    val endTimestamp: Long,         // Period bitiş
    val totalSteps: Long,           // O period'daki toplam adım
    val completedLaps: Int,         // O period'daki toplam tur
    val bestLapTimeSeconds: Long,   // O period'daki en iyi tur süresi
    val finalRank: Int? = null,     // Leaderboard'daki son sıralama
    val totalParticipants: Int? = null,
    val archivedAt: Long = System.currentTimeMillis()
)
