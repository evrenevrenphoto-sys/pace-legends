package com.pace.legends.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.pace.legends.MainActivity
import com.pace.legends.R
import com.pace.legends.domain.repository.StepRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Pace Legends Ön Plan Servisi
 * 
 * 🔋 PİL OPTİMİZASYONU:
 * Bu servis artık donanım sensörünü DİNLEMİYOR.
 * Adım verisi tamamen Health Connect'ten alınıyor.
 * 
 * Servisin amacı:
 * 1. Kullanıcıya "Aktif" hissi veren bildirim göstermek
 * 2. Arka plan işlemleri için process'i canlı tutmak
 * 
 * @see StepRepository - Health Connect entegrasyonu için
 */
@AndroidEntryPoint
class StepCounterService : LifecycleService() {

    @Inject
    lateinit var stepRepository: StepRepository

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("StepService", "✅ Service created (Battery Optimized - No Sensor)")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        startForegroundServiceNotification()
        
        return START_STICKY
    }
    
    private fun startForegroundServiceNotification() {
        val channelId = "pace_legends_service_channel"
        val channelName = "Step Counter Service"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // İlk bildirimi başlat
        val notification = buildNotification(channelId, pendingIntent, null)
        startForeground(1, notification)
        
        // Aktif pist değiştiğinde bildirimi güncelle
        lifecycleScope.launch {
            stepRepository.currentTrackId.collect { trackId ->
                val trackName = getTrackDisplayName(trackId)
                val updatedNotification = buildNotification(channelId, pendingIntent, trackName)
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(1, updatedNotification)
            }
        }
    }
    
    private fun buildNotification(channelId: String, pendingIntent: PendingIntent, trackName: String?): Notification {
        val contentText = if (trackName != null) {
            "🏎️ $trackName için Health Connect ile senkronize"
        } else {
            "Health Connect ile adımlarınız senkronize ediliyor..."
        }
        
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Pace Legends Aktif")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    // 🔄 Track isimleri artık dinamik olarak TrackRepository'den geliyor
    // Bu fonksiyon sadece fallback için basit formatlamayapıyor
    private fun getTrackDisplayName(trackId: String?): String? {
        if (trackId == null) return null
        // Basit formatlama: snake_case -> Title Case
        return trackId.replace("_", " ").split(" ").joinToString(" ") { 
            it.replaceFirstChar { char -> char.uppercase() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        android.util.Log.d("StepService", "Service destroyed")
    }
}
