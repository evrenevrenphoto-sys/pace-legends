package com.pace.legends.domain.model

import com.google.gson.annotations.SerializedName

/**
 * Tek bir pit stop bildirim mesajı
 */
data class PitStopMessage(
    val title: String,
    val body: String
)

/**
 * Firebase Remote Config'den çekilen pit stop mesaj grupları
 * 
 * @param warning_level %80 (8.000 adım) uyarı mesajları
 * @param critical_level %100 (10.000 adım) kritik uyarı mesajları
 */
data class PitStopMessagesConfig(
    @SerializedName("warning_level")
    val warningLevel: List<PitStopMessage> = emptyList(),
    
    @SerializedName("critical_level")
    val criticalLevel: List<PitStopMessage> = emptyList()
) {
    companion object {
        /**
         * Remote Config fetch başarısız olursa kullanılacak varsayılan mesajlar
         */
        fun getDefaults(): PitStopMessagesConfig = PitStopMessagesConfig(
            warningLevel = listOf(
                PitStopMessage(
                    title = "⚠️ Hız Kaybediyorsun!",
                    body = "Lastiklerin aşınmaya başladı. Temponu korumak için pite gel."
                ),
                PitStopMessage(
                    title = "🏎️ Telsiz Mesajı",
                    body = "Mühendislerin verilerini göremiyor. Pite gelip telemetriyi aç."
                ),
                PitStopMessage(
                    title = "📉 Sıralama Riski",
                    body = "Verilerin bayatladığı için sıralamada düşüyor olabilirsin."
                )
            ),
            criticalLevel = listOf(
                PitStopMessage(
                    title = "🛑 Kritik Aşınma!",
                    body = "Lastikler bitti, şu an boşuna tur atıyorsun. Hemen değişim yap!"
                ),
                PitStopMessage(
                    title = "🚫 Puanlar Sayılmıyor",
                    body = "Depon doldu taştı. Emeğin boşa gitmesin, hemen uygulamayı aç."
                )
            )
        )
    }
}
