package com.pace.legends.domain.repository

import com.pace.legends.domain.model.PeriodHistory
import java.time.Instant

data class AllTimeStats(
    val totalSteps: Long,
    val totalLaps: Int,
    val totalDistance: Double
)

interface StatsRepository {
    suspend fun getAllTimeStats(): Result<AllTimeStats>
    
    // 🆕 Refactoring: DAO erişimini sarmalamak için eklendi
    suspend fun getPeriodHistory(userId: String): Result<List<PeriodHistory>>
    
    // 🆕 İstatistik grafikleri için (opsiyonel, şimdilik sadece count dönebiliriz)
    // Şimdilik sadece adımları döndürüyoruz, ilerde domain model (DailyLog) 'a çevirebiliriz
    suspend fun getStepsByTimeRange(startTime: Instant, endTime: Instant): Result<Long>
}
