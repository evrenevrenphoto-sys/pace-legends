package com.pace.legends.domain.manager

import com.pace.legends.domain.model.PeriodInfo
import com.pace.legends.domain.model.TimeRemaining
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 🆕 Period Hesaplama Yöneticisi
 * 
 * StepSyncManager'dan ayrılmış Single Responsibility sınıfı.
 * Yarışma dönemlerini hesaplar ve dönem geçişlerini tespit eder.
 * 
 * ⏱️ Time Travel Test: Clock inject edilerek zaman dondurulabilir.
 * 🌍 Localization: String yerine TimeRemaining sealed class döndürür.
 */
@Singleton
class PeriodCalculator @Inject constructor(
    private val remoteConfigManager: RemoteConfigManager,
    // 🆕 Mükemmellik: Clock injection - test için zaman kontrolü
    private val clock: Clock = Clock.systemDefaultZone()
) {
    // Clock'tan türetilmiş helper'lar
    private fun today(): LocalDate = LocalDate.now(clock)
    private fun now(): ZonedDateTime = ZonedDateTime.now(clock)
    private fun instant(): java.time.Instant = clock.instant()

    /**
     * Mevcut yarışma dönemini detaylı hesapla (Remote Config Tabanlı)
     */
    fun getCurrentPeriodInfo(): PeriodInfo {
        return try {
            val durationDays = remoteConfigManager.raceDurationDays.value
            val startDateStr = remoteConfigManager.raceStartDate.value

            // OTOMATİK MOD: Eğer Remote Config "AUTO" dönerse, Takvim Ayını kullan
            if (startDateStr.equals("AUTO", ignoreCase = true)) {
                return calculateAutoModePeriod()
            }

            val displayName = remoteConfigManager.raceDisplayName.value
            val description = remoteConfigManager.raceDescription.value
            val emoji = remoteConfigManager.raceEmoji.value
            
            val startDate = LocalDate.parse(startDateStr, DateTimeFormatter.ISO_LOCAL_DATE)
            val today = today()
            val now = now()
            
            // Yarış henüz başlamadıysa
            if (today.isBefore(startDate)) {
                return calculateUpcomingPeriod(startDate, description, durationDays)
            }
            
            // Period hesaplama
            val daysSinceStart = java.time.temporal.ChronoUnit.DAYS.between(startDate, today)
            val periodNumber = (daysSinceStart / durationDays).toInt().coerceAtLeast(0)
            
            val periodStartDate = startDate.plusDays((periodNumber * durationDays).toLong())
            val periodEndDate = periodStartDate.plusDays(durationDays.toLong())
            
            // Kalan süre hesaplama
            val raceEndDateTime = periodEndDate.atStartOfDay(java.time.ZoneId.systemDefault())
            val duration = java.time.Duration.between(now, raceEndDateTime)
            val totalMinutesRemaining = duration.toMinutes()
            val totalHoursRemaining = duration.toHours()
            
            val timeRemaining = calculateTimeRemaining(totalMinutesRemaining, totalHoursRemaining)
            val daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(today, periodEndDate).toInt()
            val hoursRemaining = (totalHoursRemaining % 24).toInt()
            
            PeriodInfo(
                periodId = "period_$periodNumber",
                displayName = displayName,
                description = description,
                emoji = emoji,
                startDate = periodStartDate,
                endDate = periodEndDate,
                daysRemaining = daysRemaining,
                hoursRemaining = hoursRemaining,
                timeRemaining = timeRemaining,
                remainingTimeDisplay = timeRemaining.toDisplayString(),
                isLastDay = totalHoursRemaining in 0..23,
                isExpired = totalMinutesRemaining <= 0
            )
        } catch (e: Exception) {
            createFallbackPeriod()
        }
    }

    /**
     * Mevcut yarışma dönemi ID'sini döndür
     */
    fun getCurrentPeriod(): String = getCurrentPeriodInfo().periodId

    /**
     * Takvim ayı bazlı otomatik dönem hesaplama
     */
    private fun calculateAutoModePeriod(): PeriodInfo {
        val today = today()
        val yearMonth = YearMonth.from(today)
        
        val startDate = yearMonth.atDay(1)
        val endDate = yearMonth.atEndOfMonth()
        
        val periodId = yearMonth.format(DateTimeFormatter.ofPattern("yyyy_MM"))
        
        val nowInstant = instant()
        val raceEndDateTime = endDate.atStartOfDay(java.time.ZoneId.systemDefault()).plusDays(1)
        val duration = java.time.Duration.between(nowInstant, raceEndDateTime.toInstant())
        
        val totalHoursRemaining = duration.toHours()
        val totalMinutesRemaining = duration.toMinutes()
        
        val timeRemaining = calculateTimeRemaining(totalMinutesRemaining, totalHoursRemaining)

        return PeriodInfo(
            periodId = periodId,
            displayName = remoteConfigManager.raceDisplayName.value.ifEmpty { "${yearMonth.monthValue}. Dönem" },
            description = remoteConfigManager.raceDescription.value,
            emoji = remoteConfigManager.raceEmoji.value,
            startDate = startDate,
            endDate = endDate,
            daysRemaining = (totalHoursRemaining / 24).toInt(),
            hoursRemaining = (totalHoursRemaining % 24).toInt(),
            timeRemaining = timeRemaining,
            remainingTimeDisplay = timeRemaining.toDisplayString(),
            isLastDay = totalHoursRemaining in 0L..23L,
            isExpired = totalMinutesRemaining <= 0L
        )
    }

    /**
     * Yaklaşan dönem bilgisi oluştur
     */
    private fun calculateUpcomingPeriod(startDate: LocalDate, description: String, durationDays: Int): PeriodInfo {
        val today = today()
        val daysUntilStart = java.time.temporal.ChronoUnit.DAYS.between(today, startDate).toInt()
        
        return PeriodInfo(
            periodId = "upcoming",
            displayName = "Yakında Başlıyor",
            description = description,
            emoji = "⏳",
            startDate = startDate,
            endDate = startDate.plusDays(durationDays.toLong()),
            daysRemaining = daysUntilStart,
            hoursRemaining = 0,
            timeRemaining = TimeRemaining.Upcoming,
            remainingTimeDisplay = "$daysUntilStart gün sonra başlıyor",
            isLastDay = false,
            isExpired = false
        )
    }

    /**
     * 🆕 Localization: TimeRemaining sealed class döndür
     */
    private fun calculateTimeRemaining(totalMinutes: Long, totalHours: Long): TimeRemaining {
        return when {
            totalMinutes <= 0 -> TimeRemaining.Expired
            totalMinutes < 60 -> TimeRemaining.Minutes(totalMinutes.toInt()) // Long → Int
            totalHours < 24 -> TimeRemaining.Hours(totalHours.toInt())       // Long → Int
            else -> TimeRemaining.Days((totalHours / 24).toInt())
        }
    }

    /**
     * Hata durumunda fallback dönem oluştur
     */
    private fun createFallbackPeriod(): PeriodInfo {
        val today = today()
        return PeriodInfo(
            periodId = YearMonth.now(clock).format(DateTimeFormatter.ofPattern("yyyy-MM")),
            displayName = "Aylık Mod (Sistem Hatası)",
            description = "",
            emoji = "⚠️",
            startDate = today.withDayOfMonth(1),
            endDate = today.withDayOfMonth(1).plusMonths(1),
            daysRemaining = 0,
            hoursRemaining = 0,
            timeRemaining = TimeRemaining.Expired,
            remainingTimeDisplay = "Hata",
            isLastDay = false,
            isExpired = false
        )
    }
}
