package com.pace.legends.domain.manager

import com.pace.legends.domain.model.TimeRemaining
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * PeriodCalculator Unit Test
 * 
 * ⏱️ Time Travel: Clock injection sayesinde tarih dondurulabilir.
 * 🧪 MockK: RemoteConfigManager class olduğu için mock kullanılıyor.
 */
class PeriodCalculatorTest {

    private fun createMockConfig(startDate: String = "AUTO"): RemoteConfigManager {
        val mockConfig = mockk<RemoteConfigManager>(relaxed = true)
        
        // StateFlow mocking - value property'si için
        every { mockConfig.raceStartDate.value } returns startDate
        every { mockConfig.raceDurationDays.value } returns 30
        every { mockConfig.raceDisplayName.value } returns "Test Race"
        every { mockConfig.raceDescription.value } returns "Test Description"
        every { mockConfig.raceEmoji.value } returns "🏃"
        
        return mockConfig
    }

    @Test
    fun `TEST TIME TRAVEL - 15 Ocak tarihinde kalan sureyi dogru hesaplar`() {
        // 1. ZAMANI DONDUR (Time Travel) ⏱️
        // Senaryo: Bugün 15 Ocak 2026, Saat 12:00
        val fixedDate = LocalDate.of(2026, 1, 15)
        val fixedTime = fixedDate.atTime(12, 0).atZone(ZoneId.systemDefault())
        val fixedClock = Clock.fixed(fixedTime.toInstant(), ZoneId.systemDefault())

        // 2. Mock Config
        val mockConfig = createMockConfig("AUTO")

        // 3. Calculator'ı başlat
        val calculator = PeriodCalculator(mockConfig, fixedClock)

        // 4. Test Et
        val info = calculator.getCurrentPeriodInfo()

        // 15 Ocak -> 31 Ocak sonu. Kalan tam gün sayısı matematiken 16'dır.
        // (31-15 = 16 gün, 12 saat daha var ama tam gün olarak 16)
        assertTrue("Dönen tip Days olmalı, ama ${info.timeRemaining}", 
            info.timeRemaining is TimeRemaining.Days)
        assertEquals(16, (info.timeRemaining as TimeRemaining.Days).count)
    }

    @Test
    fun `donem degisince yeni periodId donmeli`() {
        // Senaryo: 1 Şubat gece yarısı (yeni ay başladı)
        val fixedDate = LocalDate.of(2026, 2, 1)
        val fixedTime = fixedDate.atTime(0, 1).atZone(ZoneId.systemDefault())
        val fixedClock = Clock.fixed(fixedTime.toInstant(), ZoneId.systemDefault())

        val mockConfig = createMockConfig("AUTO")
        val calculator = PeriodCalculator(mockConfig, fixedClock)
        val info = calculator.getCurrentPeriodInfo()

        // Şubat ayına geçtik, AUTO mod yeni ayı hesaplamalı
        assertEquals("2026_02", info.periodId)
    }

    @Test
    fun `son 24 saat kaldiysa Hours donmeli`() {
        // Senaryo: 31 Ocak saat 10:00 (Son gün, 14 saat kaldı)
        val fixedDate = LocalDate.of(2026, 1, 31)
        val fixedTime = fixedDate.atTime(10, 0).atZone(ZoneId.systemDefault())
        val fixedClock = Clock.fixed(fixedTime.toInstant(), ZoneId.systemDefault())

        val mockConfig = createMockConfig("AUTO")
        val calculator = PeriodCalculator(mockConfig, fixedClock)
        val info = calculator.getCurrentPeriodInfo()

        // Son gün, 14 saat kaldı
        assertTrue("Son gün olmalı", info.isLastDay)
        assertTrue("Hours tipi bekleniyor, ama ${info.timeRemaining}", 
            info.timeRemaining is TimeRemaining.Hours)
    }
}

