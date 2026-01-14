package com.pace.legends.domain.manager

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: com.google.firebase.firestore.FirebaseFirestore,
    private val authRepository: com.pace.legends.domain.repository.AuthRepository
) {
    @Volatile
    private var _client: HealthConnectClient? = null
    private val clientLock = Any()

    /**
     * Health Connect Client (Available only if supported)
     * Thread-safe getter with double-checked locking.
     */
    val healthConnectClient: HealthConnectClient?
        get() = _client ?: synchronized(clientLock) {
            _client ?: try {
                HealthConnectClient.getOrCreate(context).also { _client = it }
            } catch (e: Exception) {
                android.util.Log.e("HealthConnectManager", "❌ Health Connect Client creation failed: ${e.message}")
                null
            }
        }

    /**
     * Gerekli tüm izinler
     */
    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
    )

    /**
     * Tüm izinlerin verilip verilmediğini kontrol et
     */
    suspend fun hasAllPermissions(): Boolean {
        return try {
            val granted = healthConnectClient?.permissionController?.getGrantedPermissions() ?: emptySet()
            granted.containsAll(permissions)
        } catch (e: Exception) {
            false
        }
    }

    // 🔄 FIX: Son başarılı okuma değeri - SharedPreferences ile persist
    private val prefs = context.getSharedPreferences("health_connect_cache", Context.MODE_PRIVATE)
    
    private var lastSuccessfulSteps: Long
        get() = prefs.getLong("hc_last_steps", 0L)
        set(value) = prefs.edit().putLong("hc_last_steps", value).apply()
    
    // 🆕 Son sync zamanı (UI'da gösterilecek)
    var lastSyncTimestamp: Long
        get() = prefs.getLong("hc_last_sync_time", 0L)
        private set(value) = prefs.edit().putLong("hc_last_sync_time", value).apply()
    
    /**
     * 🆕 Son sync'den bu yana geçen süreyi döndür (dakika cinsinden)
     */
    fun getMinutesSinceLastSync(): Int {
        val lastSync = lastSyncTimestamp
        if (lastSync == 0L) return -1 // Henüz sync yapılmadı
        return ((System.currentTimeMillis() - lastSync) / 60000).toInt()
    }
    
    /**
     * Belirli bir tarih aralığındaki toplam adım sayısını getir
     * 
     * 🛡️ ANTI-CHEAT: Manuel girişler filtrelenir (Health Connect recordingMethod)
     * ⚠️ Android 14+: Arka planda okuma izni yok, sadece ön planda çalışır.
     * 
     * @param startTime Başlangıç zamanı
     * @param endTime Bitiş zamanı
     * @return Doğrulanmış adım sayısı (manuel girişler hariç)
     */
    suspend fun readStepsByTimeRange(startTime: Instant, endTime: Instant): Long {
        val client = healthConnectClient ?: return lastSuccessfulSteps
        
        android.util.Log.d("DEBUG_HC", "👓 Reading steps from $startTime to $endTime")
        
        // 🆕 P0 FIX: aggregate() ile dedup edilmiş toplam al
        // readRecords() overlapping kayıtları çift sayıyor (telefon + saat gibi)
        try {
            val response = client.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            val aggregatedSteps = response[StepsRecord.COUNT_TOTAL] ?: 0L
            
            android.util.Log.d("DEBUG_HC", "📊 Aggregate (deduped): $aggregatedSteps steps")
            
            // 🛡️ ANTI-CHEAT: Manuel giriş kontrolü (opsiyonel)
            // aggregate() manuel girişleri de sayar, kontrol için readRecords kullanabiliriz
            try {
                val manualSteps = checkForManualEntries(client, startTime, endTime)
                if (manualSteps > 0) {
                    android.util.Log.w("AntiCheat", "🛡️ Detected $manualSteps manual steps in aggregate")
                    logCheatAttempt(manualSteps, aggregatedSteps, startTime, endTime)
                    // Manuel girişleri çıkar
                    val verifiedSteps = (aggregatedSteps - manualSteps).coerceAtLeast(0)
                    lastSuccessfulSteps = verifiedSteps
                    lastSyncTimestamp = System.currentTimeMillis() // 🆕 Sync zamanını kaydet
                    return verifiedSteps
                }
            } catch (e: Exception) {
                android.util.Log.d("DEBUG_HC", "⚠️ Anti-cheat check skipped: ${e.message}")
                // Anti-cheat başarısız olursa aggregate değerini kullan
            }
            
            lastSuccessfulSteps = aggregatedSteps
            lastSyncTimestamp = System.currentTimeMillis() // 🆕 Sync zamanını kaydet
            return aggregatedSteps
            
        } catch (e: Exception) {
            val errorMsg = e.message ?: ""
            
            // Arka plan hatası - cache döndür
            if (errorMsg.contains("foreground") || errorMsg.contains("BACKGROUND")) {
                android.util.Log.d("DEBUG_HC", "📱 Background blocked, using cache: $lastSuccessfulSteps")
                return lastSuccessfulSteps
            }
            
            android.util.Log.e("DEBUG_HC", "❌ Aggregate error: ${e.message}")
            return lastSuccessfulSteps
        }
    }
    
    /**
     * 🛡️ ANTI-CHEAT: Manuel giriş sayısını hesapla
     */
    private suspend fun checkForManualEntries(
        client: HealthConnectClient,
        startTime: Instant,
        endTime: Instant
    ): Long {
        val allRecords = mutableListOf<StepsRecord>()
        var pageToken: String? = null
        
        do {
            val request = ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                pageToken = pageToken
            )
            val response = client.readRecords(request)
            allRecords.addAll(response.records)
            pageToken = response.pageToken
        } while (pageToken != null)
        
        val manualRecords = allRecords.filter { record ->
            record.metadata.recordingMethod == Metadata.RECORDING_METHOD_MANUAL_ENTRY
        }
        
        return manualRecords.sumOf { it.count }
    }
    
    /**
     * Fallback: aggregate() ile toplam al (anti-cheat YOK)
     */
    private suspend fun readStepsWithAggregateFallback(
        client: HealthConnectClient,
        startTime: Instant,
        endTime: Instant
    ): Long {
        return try {
            val response = client.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            val steps = response[StepsRecord.COUNT_TOTAL] ?: 0L
            lastSuccessfulSteps = steps
            android.util.Log.d("DEBUG_HC", "📊 Aggregate fallback: $steps (anti-cheat bypassed)")
            steps
        } catch (e: Exception) {
            android.util.Log.e("DEBUG_HC", "❌ Aggregate failed: ${e.message}")
            lastSuccessfulSteps
        }
    }
    
    /**
     * 🛡️ ANTI-CHEAT: Belirli bir tarih aralığındaki adım istatistiklerini getir
     * Debug/Admin amaçlı - Manuel vs Otomatik kayıt dağılımını gösterir
     */
    suspend fun getStepRecordStats(startTime: Instant, endTime: Instant): StepRecordStats {
        return try {
            val client = healthConnectClient ?: return StepRecordStats.empty()
            
            // Pagination ile tüm kayıtları oku
            val allRecords = mutableListOf<StepsRecord>()
            var pageToken: String? = null
            
            do {
                val request = ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                    pageToken = pageToken
                )
                val response = client.readRecords(request)
                allRecords.addAll(response.records)
                pageToken = response.pageToken
            } while (pageToken != null)
            
            val manualRecords = allRecords.filter { 
                it.metadata.recordingMethod == Metadata.RECORDING_METHOD_MANUAL_ENTRY 
            }
            val autoRecords = allRecords.filter { 
                it.metadata.recordingMethod != Metadata.RECORDING_METHOD_MANUAL_ENTRY 
            }
            
            StepRecordStats(
                totalRecords = allRecords.size,
                manualRecords = manualRecords.size,
                autoRecords = autoRecords.size,
                totalSteps = allRecords.sumOf { it.count },
                manualSteps = manualRecords.sumOf { it.count },
                verifiedSteps = autoRecords.sumOf { it.count }
            )
        } catch (e: Exception) {
            android.util.Log.e("HealthConnect", "Error getting step stats: ${e.message}")
            StepRecordStats.empty()
        }
    }
    
    /**
     * Adım kayıt istatistikleri (Anti-Cheat Debug)
     */
    data class StepRecordStats(
        val totalRecords: Int,
        val manualRecords: Int,
        val autoRecords: Int,
        val totalSteps: Long,
        val manualSteps: Long,
        val verifiedSteps: Long
    ) {
        val filteredPercentage: Float
            get() = if (totalSteps > 0) (manualSteps.toFloat() / totalSteps * 100) else 0f
            
        companion object {
            fun empty() = StepRecordStats(0, 0, 0, 0L, 0L, 0L)
        }
    }
    
    /**
     * Belirli bir tarih aralığındaki toplam kaloriyi getir
     */
    suspend fun readCaloriesByTimeRange(startTime: Instant, endTime: Instant): Long {
        return try {
            val client = healthConnectClient ?: return 0L

            val response = client.aggregate(
                AggregateRequest(
                    metrics = setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            response[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories?.toLong() ?: 0L
        } catch (e: Exception) {
            android.util.Log.e("HealthConnect", "Error reading calories: ${e.message}")
            0L
        }
    }
    
    /**
     * Cihazın Health Connect destekleyip desteklemediğini kontrol et
     */
    fun isHealthConnectAvailable(): Boolean {
        return healthConnectClient != null
    }

    /**
     * 🛡️ Şüpheli aktiviteyi Firestore'a bildir
     */
    private fun logCheatAttempt(manualSteps: Long, totalSteps: Long, startTime: Instant, endTime: Instant) {
        val userId = authRepository.getCurrentUserId() ?: return
        
        // Basit flood protection: Sadece kayda değer (ör. > 100 adım) manuel girişleri logla
        if (manualSteps < 100) return

        try {
            val logData = mapOf(
                "userId" to userId,
                "type" to "MANUAL_ENTRY_DETECTED",
                "manualSteps" to manualSteps,
                "totalSteps" to totalSteps,
                "startTime" to startTime.toEpochMilli(),
                "endTime" to endTime.toEpochMilli(),
                "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "deviceModel" to android.os.Build.MODEL,
                "androidVersion" to android.os.Build.VERSION.SDK_INT
            )

            firestore.collection("antiCheatLogs")
                .add(logData)
                .addOnSuccessListener { 
                    android.util.Log.d("AntiCheat", "📝 Cheat attempt logged to Firestore") 
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("AntiCheat", "❌ Failed to log cheat attempt: ${e.message}")
                }
        } catch (e: Exception) {
            android.util.Log.e("AntiCheat", "❌ Error preparing cheat log: ${e.message}")
        }
    }
}
