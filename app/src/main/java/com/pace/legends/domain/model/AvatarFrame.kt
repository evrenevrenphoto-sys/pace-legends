package com.pace.legends.domain.model

/**
 * Avatar Çerçevesi (Kozmetik Ödül)
 * 
 * Her lig kademesine özel çerçeveler tanımlanmıştır.
 * Kullanıcı bir lige yükseldiğinde o ligin çerçevesi açılır.
 */
data class AvatarFrame(
    val id: String,
    val name: String,
    val emoji: String,
    val requiredTier: LeagueTier,
    val isUnlocked: Boolean = false,
    val unlockedAt: Long? = null,
    val price: Int? = null // 💰 Satış fiyatı (null ise satılamaz)
) {
    companion object {
        /**
         * Varsayılan çerçeve (herkes için açık)
         */
        val DEFAULT = AvatarFrame(
            id = "default",
            name = "Standart",
            emoji = "⬜",
            requiredTier = LeagueTier.QUALIFYING,
            isUnlocked = true
        )
        
        /**
         * Tüm çerçeveler
         */
        val ALL_FRAMES = listOf(
            DEFAULT,
            // Lig Ödülleri
            AvatarFrame("bronze", "Bronz Çerçeve", "🔶", LeagueTier.BRONZE),
            AvatarFrame("silver", "Gümüş Çerçeve", "⚪", LeagueTier.SILVER),
            AvatarFrame("gold", "Altın Çerçeve", "🟡", LeagueTier.GOLD),
            AvatarFrame("platinum", "Platin Çerçeve", "💠", LeagueTier.PLATINUM),
            AvatarFrame("diamond", "Elmas Çerçeve", "💎", LeagueTier.DIAMOND),
            AvatarFrame("legend", "Efsane Çerçeve", "🏆", LeagueTier.LEGEND),
            
            // Mağaza Özel (Premium)
            AvatarFrame("neon", "Neon Yarışçı", "🟣", LeagueTier.QUALIFYING, price = 1000),
            AvatarFrame("fire", "Alevlerin Gücü", "🔥", LeagueTier.QUALIFYING, price = 2500),
            AvatarFrame("glitch", "Siber Hata", "👾", LeagueTier.QUALIFYING, price = 5000)
        )
        
        /**
         * Lig kademesine göre çerçeve getir
         */
        fun forTier(tier: LeagueTier): AvatarFrame {
            return ALL_FRAMES.find { it.requiredTier == tier } ?: DEFAULT
        }
        
        /**
         * ID'ye göre çerçeve getir
         */
        fun byId(id: String): AvatarFrame {
            return ALL_FRAMES.find { it.id == id } ?: DEFAULT
        }
    }
}
