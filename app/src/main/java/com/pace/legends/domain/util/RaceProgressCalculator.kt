package com.pace.legends.domain.util

/**
 * F1 temalı yarış ilerleme hesapları
 * 
 * Hesaplama mantığı:
 * 1. Adım → Mesafe: steps * STEP_TO_METERS (0.75m ortalama adım)
 * 2. Mesafe → Tur: distance / trackDistanceMeters
 * 
 * Track.totalDistanceMeters Firebase/Remote Config'den gelir.
 */
object RaceProgressCalculator {
    
    /**
     * Ortalama adım uzunluğu (metre)
     * Araştırmalara göre yetişkin ortalaması 0.7 - 0.8 metre
     */
    const val STEP_TO_METERS = 0.75
    
    /**
     * Sektör renkleri (F1 standardı)
     */
    object SectorColors {
        const val SECTOR_1 = 0xFF4CAF50 // Yeşil
        const val SECTOR_2 = 0xFFFFEB3B // Sarı  
        const val SECTOR_3 = 0xFF9C27B0 // Mor
    }
    
    /**
     * Yarış ilerleme durumu
     */
    data class RaceProgress(
        val totalLaps: Int,              // Tamamlanan tur sayısı
        val currentLapSteps: Long,       // Mevcut turdaki adım (metre olarak gösterim için)
        val trackLength: Long,           // Pistin uzunluğu (metre)
        val progressPercentage: Float,   // 0.0 - 1.0 arası
        val currentSector: Int,          // 1, 2 veya 3
        val sectorProgress: Float,       // Sektör içindeki ilerleme 0.0 - 1.0
        val motivationMessage: String    // Dinamik motivasyon mesajı
    )
    
    /**
     * Aylık adımdan yarış durumunu hesapla
     * 
     * @param monthlySteps Aylık toplam adım sayısı
     * @param trackDistanceMeters Pist uzunluğu (metre) - Track.totalDistanceMeters'dan gelir
     */
    fun calculateProgress(monthlySteps: Long, trackDistanceMeters: Int): RaceProgress {
        // Güvenlik kontrolü
        if (trackDistanceMeters <= 0) {
            return createEmptyProgress()
        }
        
        // Adım → Mesafe dönüşümü
        val totalDistanceMeters = (monthlySteps * STEP_TO_METERS).toLong()
        val trackLength = trackDistanceMeters.toLong()
        
        // 🔄 P2 FIX: Integer overflow koruması ve Modulo
        val totalLaps = (totalDistanceMeters / trackLength)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        
        // 🏁 2. Action Item: Modulo işlemi (Kalan mesafe = Turdaki konumu)
        val currentLapDistance = totalDistanceMeters % trackLength
        
        val progressPercentage = (currentLapDistance.toFloat() / trackLength.toFloat()).coerceIn(0f, 1f)
        
        // Sektör hesaplama (3 eşit parça)
        val currentSector = when {
            progressPercentage < 0.33f -> 1
            progressPercentage < 0.66f -> 2
            else -> 3
        }
        
        // Sektör içindeki ilerleme
        val sectorProgress = when (currentSector) {
            1 -> progressPercentage / 0.33f
            2 -> (progressPercentage - 0.33f) / 0.33f
            else -> (progressPercentage - 0.66f) / 0.34f
        }.coerceIn(0f, 1f)
        
        // Motivasyon mesajı
        val motivationMessage = getMotivationMessage(progressPercentage, currentSector, totalLaps)
        
        return RaceProgress(
            totalLaps = totalLaps,
            currentLapSteps = currentLapDistance,  // Artık metre cinsinden
            trackLength = trackLength,
            progressPercentage = progressPercentage,
            currentSector = currentSector,
            sectorProgress = sectorProgress,
            motivationMessage = motivationMessage
        )
    }
    
    /**
     * Eski API uyumluluğu için - trackId ile çağrı (DEPRECATED)
     * Yeni kod trackDistanceMeters parametresini kullanmalı
     */
    @Deprecated("Use calculateProgress(monthlySteps, trackDistanceMeters) instead")
    fun calculateProgress(monthlySteps: Long, trackId: String): RaceProgress {
        // Eski hardcoded değerler - sadece backward compatibility için
        val fallbackDistances = mapOf(
            "monaco_circuit" to 3337,
            "istanbul_park" to 5338,
            "monza" to 5793,
            "silverstone" to 5891,
            "spa" to 7004,
            "suzuka" to 5807
        )
        val trackDistanceMeters = fallbackDistances[trackId] ?: 5000
        return calculateProgress(monthlySteps, trackDistanceMeters)
    }
    
    /**
     * Boş/hatalı durum için varsayılan progress
     */
    private fun createEmptyProgress(): RaceProgress {
        return RaceProgress(
            totalLaps = 0,
            currentLapSteps = 0,
            trackLength = 1,
            progressPercentage = 0f,
            currentSector = 1,
            sectorProgress = 0f,
            motivationMessage = "🏁 Pist bilgisi yükleniyor..."
        )
    }
    
    /**
     * Dinamik motivasyon mesajı
     */
    private fun getMotivationMessage(progress: Float, sector: Int, laps: Int): String {
        return when {
            progress < 0.05f && laps == 0 -> "🏁 Yarış başlasın! İlk adımını at."
            progress < 0.05f -> "🆕 Yeni tur, yeni şans!"
            sector == 1 && progress < 0.15f -> "🚦 Işıklar söndü, hızlan!"
            sector == 1 -> "💨 Sektör 1'de iyi gidiyorsun!"
            sector == 2 && progress < 0.5f -> "⚡ Orta sektördesin, tempoyu koru!"
            sector == 2 -> "🔥 Harika tempo! Sektör 3'e yaklaşıyorsun."
            sector == 3 && progress < 0.9f -> "🏎️ Son düzlük! Gaza bas!"
            sector == 3 -> "🎯 Bitiş çizgisi görünüyor! Son sprint!"
            else -> "💪 Devam et, harika gidiyorsun!"
        }
    }
    
    /**
     * Sektör rengini al
     */
    fun getSectorColor(sector: Int): Long {
        return when (sector) {
            1 -> SectorColors.SECTOR_1
            2 -> SectorColors.SECTOR_2
            else -> SectorColors.SECTOR_3
        }
    }
}
