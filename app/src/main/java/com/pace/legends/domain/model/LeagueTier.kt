package com.pace.legends.domain.model

/**
 * Lig Kademeleri
 * 
 * QUALIFYING: Eleme Havuzu (Sınırsız kullanıcı)
 * BRONZE → LEGEND: Ana ligler (Her biri 50 kişi)
 */
enum class LeagueTier(
    val displayName: String,
    val emoji: String,
    val order: Int // Sıralama için (0 = en düşük)
) {
    QUALIFYING("Eleme Havuzu", "🏁", 0),
    BRONZE("Bronz Lig", "🔶", 1),
    SILVER("Gümüş Lig", "🥉", 2),
    GOLD("Altın Lig", "🥈", 3),
    PLATINUM("Platin Lig", "🥇", 4),
    DIAMOND("Elmas Lig", "💎", 5),
    LEGEND("Efsane Lig", "🏆", 6);

    fun canPromote(): Boolean = this != LEGEND
    fun canDemote(): Boolean = this != QUALIFYING
    
    fun nextTier(): LeagueTier? = entries.find { it.order == this.order + 1 }
    fun previousTier(): LeagueTier? = entries.find { it.order == this.order - 1 }
    
    companion object {
        fun fromString(value: String): LeagueTier {
            return entries.find { it.name == value } ?: QUALIFYING
        }
    }
}

/**
 * Kullanıcının Lig Durumu
 */
data class UserLeagueInfo(
    val tier: LeagueTier = LeagueTier.QUALIFYING,
    val leagueId: String? = null, // Eleme'de null, liglerde "bronze_42" gibi
    val rankInLeague: Int = 0,    // Lig/Eleme içindeki sıralama
    val totalInLeague: Int = 0    // Lig/Eleme'deki toplam kişi
) {
    val isInQualifying: Boolean get() = tier == LeagueTier.QUALIFYING
    
    val promotionZone: Boolean get() = rankInLeague in 1..5
    val relegationZone: Boolean get() = rankInLeague > (totalInLeague - 5)
    
    val displayRank: String get() = if (rankInLeague > 0) "#$rankInLeague" else "-"
}

/**
 * Dönem sonu yükselme/düşme sonucu
 */
data class PromotionResult(
    val previousTier: LeagueTier,
    val newTier: LeagueTier,
    val isPromotion: Boolean,
    val isDemotion: Boolean = !isPromotion && previousTier != newTier
) {
    val stayedSame: Boolean get() = previousTier == newTier
}
