package com.pace.legends.domain.model

import java.time.LocalDate

/**
 * Yarışma Dönemi Bilgisi
 * 
 * StepSyncManager'dan ayrılmış domain model.
 */
data class PeriodInfo(
    val periodId: String,
    val displayName: String,
    val description: String,
    val emoji: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val daysRemaining: Int,
    val hoursRemaining: Int,
    // 🆕 Localization: String yerine sealed class
    val timeRemaining: TimeRemaining,
    val remainingTimeDisplay: String, // Backward compat için tutuldu
    val isLastDay: Boolean,
    val isExpired: Boolean
) {
    companion object {
        fun empty() = PeriodInfo(
            "", "", "", "", LocalDate.MIN, LocalDate.MIN, 0, 0, 
            TimeRemaining.Expired, "", false, false
        )
    }
}

