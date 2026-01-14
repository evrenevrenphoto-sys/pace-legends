package com.pace.legends.domain.manager

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 🛡️ GPS Anti-Cheat Manager
 * 
 * FusedLocationProvider kullanarak anlık GPS hızını takip eder.
 * StepSyncManager tarafından adım kadansı ile çapraz doğrulama için kullanılır.
 * 
 * Kullanım Senaryoları:
 * - GPS hızı > 25km/h VE adım kadansı < 0.5 → Araçta (Hile!)
 * - GPS hızı > 15km/h VE adım kadansı < 0.3 → Bisiklet (Hile!)
 * - GPS hızı < 10km/h VE adım kadansı > 1.0 → Normal yürüyüş ✅
 */
@Singleton
class RaceLocationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fusedLocationClient: FusedLocationProviderClient = 
        LocationServices.getFusedLocationProviderClient(context)

    // Anlık Hız (Metre/Saniye) - UI ve Anti-Cheat için
    private val _currentSpeedMps = MutableStateFlow(0f)
    val currentSpeedMps: StateFlow<Float> = _currentSpeedMps.asStateFlow()
    
    // GPS Hızı km/h (Debug UI için)
    val currentSpeedKmh: Float
        get() = _currentSpeedMps.value * 3.6f

    // GPS Doğruluğu (Hatalı ölçümleri elemek için)
    private val _accuracy = MutableStateFlow(100f) // Başlangıçta düşük doğruluk
    val accuracy: StateFlow<Float> = _accuracy.asStateFlow()
    
    // Konum izleme aktif mi?
    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private var locationCallback: LocationCallback? = null

    /**
     * İzin kontrolü
     */
    fun hasLocationPermission(): Boolean {
        return android.Manifest.permission.ACCESS_FINE_LOCATION.let { permission ->
            context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * GPS takibini başlat
     * 
     * NOT: Bu fonksiyonu çağırmadan önce konum izninin alındığından emin olun!
     * İzin kontrolü UI katmanında (MainActivity/PermissionScreen) yapılmalıdır.
     */
    @SuppressLint("MissingPermission") // İzin kontrolü UI tarafında yapılmalı
    fun startLocationUpdates() {
        if (locationCallback != null) {
            android.util.Log.w("RaceLocation", "⚠️ Location updates already running")
            return // Zaten çalışıyor
        }
        
        if (!hasLocationPermission()) {
            android.util.Log.e("RaceLocation", "❌ Location permission not granted!")
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L) // 5 saniyede bir
            .setMinUpdateIntervalMillis(2000L) // En sık 2 saniye
            .setMaxUpdateDelayMillis(10000L) // Batch için max 10 saniye
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    // GPS doğruluğunu kaydet
                    _accuracy.value = location.accuracy
                    
                    // Sadece güvenilir veriyi kullan (doğruluk < 50 metre)
                    if (location.accuracy > 50f) {
                        android.util.Log.d("RaceLocation", "⚠️ Low accuracy: ${location.accuracy}m, ignoring")
                        return
                    }
                    
                    if (location.hasSpeed()) {
                        _currentSpeedMps.value = location.speed
                        android.util.Log.d("RaceLocation", "📍 Speed: ${String.format("%.1f", location.speed * 3.6)} km/h, Acc: ${location.accuracy}m")
                    } else {
                        // Hız verisi yoksa 0 varsay
                        _currentSpeedMps.value = 0f
                        android.util.Log.d("RaceLocation", "📍 No speed data, position updated")
                    }
                }
            }
            
            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable) {
                    android.util.Log.w("RaceLocation", "⚠️ GPS unavailable")
                    _currentSpeedMps.value = 0f
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback!!,
            Looper.getMainLooper()
        )
        
        _isTracking.value = true
        android.util.Log.i("RaceLocation", "✅ GPS tracking started")
    }

    /**
     * GPS takibini durdur
     * 
     * Yarış bittiğinde veya uygulama arka plana geçtiğinde çağrılmalı.
     * Pil ömrünü korumak için kritik!
     */
    fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            android.util.Log.i("RaceLocation", "🛑 GPS tracking stopped")
        }
        locationCallback = null
        _currentSpeedMps.value = 0f
        _isTracking.value = false
    }
    
    /**
     * 🛡️ Anti-Cheat: Şüpheli hareket kontrolü
     * 
     * GPS hızı ile adım kadansını karşılaştırır.
     * 
     * @param stepCadence Adım/Saniye oranı
     * @return true = Şüpheli (Muhtemel hile), false = Normal
     */
    fun isMovementSuspicious(stepCadence: Float): Boolean {
        val gpsSpeedMps = _currentSpeedMps.value
        val gpsAccuracy = _accuracy.value
        
        // GPS verisi yoksa veya güvenilir değilse şüphe yok (sadece adım kontrolüne güven)
        if (gpsSpeedMps <= 0f || gpsAccuracy > 50f) {
            return false
        }
        
        // KURAL 1: Araç Modu
        // GPS hızı > 7 m/s (~25 km/h) VE adım kadansı < 0.5 (Neredeyse adım yok)
        val isInVehicle = gpsSpeedMps > 7.0f && stepCadence < 0.5f
        
        // KURAL 2: Bisiklet/Scooter Modu  
        // GPS hızı > 4 m/s (~15 km/h) VE adım kadansı < 0.3
        val isOnBicycle = gpsSpeedMps > 4.0f && stepCadence < 0.3f
        
        if (isInVehicle || isOnBicycle) {
            android.util.Log.w("RaceLocation", "🚨 SUSPICIOUS: GPS=${String.format("%.1f", gpsSpeedMps * 3.6)} km/h, Cadence=$stepCadence step/s")
            return true
        }
        
        return false
    }
}
