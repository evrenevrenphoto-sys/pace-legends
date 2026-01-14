package com.pace.legends.domain.model

/**
 * Coin Ödül Türleri
 * 
 * Her ödül türü için sabit miktarlar tanımlanmıştır.
 * Bu değerler Remote Config ile değiştirilebilir hale getirilebilir.
 */
enum class CoinRewardType(
    val amount: Int,
    val description: String,
    val emoji: String
) {
    LEAGUE_PROMOTION(500, "Lig Yükselme Bonusu", "🎉"),
    PERIOD_CHAMPION(300, "Dönem Şampiyonu", "🥇"),
    PERIOD_SECOND(200, "Dönem 2. Sıra", "🥈"),
    PERIOD_THIRD(100, "Dönem 3. Sıra", "🥉"),
    DAILY_GOAL(10, "Günlük Hedef Tamamlandı", "✅"),
    WEEKLY_STREAK(50, "Haftalık Seri", "🔥"),
    FIRST_LAP(25, "İlk Tur Tamamlandı", "🏁"),
    DEBUG_BONUS(100, "DEBUG: Test Coin", "💰"), // 🆕 Debug için
    PURCHASE(0, "Mağaza Harcaması", "🛒");
    
    companion object {
        /**
         * Sıralamaya göre ödül türünü döndür
         */
        fun forRank(rank: Int): CoinRewardType? {
            return when (rank) {
                1 -> PERIOD_CHAMPION
                2 -> PERIOD_SECOND
                3 -> PERIOD_THIRD
                else -> null
            }
        }
    }
}

/**
 * Coin İşlem Kaydı
 * 
 * Her coin kazanma/harcama işlemi için log tutulur.
 * Firestore'da users/{userId}/coinHistory koleksiyonunda saklanır.
 */
data class CoinTransaction(
    val id: String = "",
    val userId: String = "",
    val type: String = "", // CoinRewardType.name
    val amount: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val description: String = "",
    val periodId: String? = null,
    val trackId: String? = null
) {
    /**
     * Firestore'a yazılacak Map
     */
    fun toMap(): Map<String, Any?> = mapOf(
        "userId" to userId,
        "type" to type,
        "amount" to amount,
        "timestamp" to timestamp,
        "description" to description,
        "periodId" to periodId,
        "trackId" to trackId
    )
}
