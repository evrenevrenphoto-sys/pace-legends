package com.pace.legends.domain.manager

import com.pace.legends.domain.model.PitStopMessage
import com.pace.legends.domain.model.PitStopMessagesConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * 🏎️ Safety Car Manager
 * 
 * F1 temalı pit stop bildirimlerini yönetir.
 * Mesajlar ve threshold'lar Firebase Remote Config'den dinamik olarak çekilir.
 * 
 * Threshold'lar (Remote Config'den):
 * - Critical: background_step_limit değeri (varsayılan 10.000)
 * - Warning: Bu değerin %80'i (otomatik hesaplanır)
 */
@Singleton
class SafetyCarManager @Inject constructor(
    private val remoteConfigManager: RemoteConfigManager
) {
    
    /**
     * 🆕 Kritik eşik değerini al (Remote Config'den)
     * Test için Firebase'den düşürülebilir
     */
    fun getCriticalThreshold(): Long {
        return remoteConfigManager.backgroundStepLimit.value
    }
    
    /**
     * 🆕 Uyarı eşik değerini al (Kritik'in %80'i)
     * Otomatik hesaplanır
     */
    fun getWarningThreshold(): Long {
        val critical = getCriticalThreshold()
        return (critical * 0.8).toLong()
    }
    
    /**
     * Uyarı seviyesi mesajı getir
     * Remote Config'deki listeden rastgele bir mesaj döner
     */
    fun getWarningMessage(): PitStopMessage {
        val messages = remoteConfigManager.pitStopMessages.value
        val warningList = messages.warningLevel.ifEmpty { 
            PitStopMessagesConfig.getDefaults().warningLevel 
        }
        return warningList.randomOrNull() ?: getDefaultWarningMessage()
    }
    
    /**
     * Kritik seviye mesajı getir
     * Remote Config'deki listeden rastgele bir mesaj döner
     */
    fun getCriticalMessage(): PitStopMessage {
        val messages = remoteConfigManager.pitStopMessages.value
        val criticalList = messages.criticalLevel.ifEmpty { 
            PitStopMessagesConfig.getDefaults().criticalLevel 
        }
        return criticalList.randomOrNull() ?: getDefaultCriticalMessage()
    }
    
    /**
     * Tüm uyarı mesajlarını getir (debug için)
     */
    fun getAllWarningMessages(): List<PitStopMessage> {
        return remoteConfigManager.pitStopMessages.value.warningLevel
    }
    
    /**
     * Tüm kritik mesajları getir (debug için)
     */
    fun getAllCriticalMessages(): List<PitStopMessage> {
        return remoteConfigManager.pitStopMessages.value.criticalLevel
    }
    
    private fun getDefaultWarningMessage(): PitStopMessage = PitStopMessage(
        title = "⚠️ Hız Kaybediyorsun!",
        body = "Lastiklerin aşınmaya başladı. Temponu korumak için pite gel."
    )
    
    private fun getDefaultCriticalMessage(): PitStopMessage = PitStopMessage(
        title = "🛑 Kritik Aşınma!",
        body = "Lastikler bitti, şu an boşuna tur atıyorsun. Hemen değişim yap!"
    )
}

