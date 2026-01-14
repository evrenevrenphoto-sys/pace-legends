package com.pace.legends.domain.model

/**
 * 🆕 Kalan Zaman Durumu (Localization için)
 * 
 * Domain katmanı String döndürmez, durum döndürür.
 * UI katmanı bu durumu Resource'lara çevirir.
 * 
 * ⚠️ Tüm count değerleri Int (Long gereksiz - milyarlarca saat kalmayacak)
 */
sealed class TimeRemaining {
    data class Days(val count: Int) : TimeRemaining()
    data class Hours(val count: Int) : TimeRemaining()  // 🆕 Long → Int
    data class Minutes(val count: Int) : TimeRemaining() // 🆕 Long → Int
    data object Expired : TimeRemaining()
    data object Upcoming : TimeRemaining()
    
    /**
     * Debug için default formatter.
     * Production'da strings.xml kullanılmalı.
     */
    fun toDisplayString(): String = when (this) {
        is Days -> "$count gün kaldı"
        is Hours -> "$count saat kaldı"
        is Minutes -> "$count dakika kaldı"
        Expired -> "Dönem bitti"
        Upcoming -> "Yakında başlıyor"
    }
}
