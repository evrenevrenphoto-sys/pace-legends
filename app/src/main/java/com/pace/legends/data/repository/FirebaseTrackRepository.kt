package com.pace.legends.data.repository

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pace.legends.domain.model.Track
import com.pace.legends.domain.repository.TrackRepository
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseTrackRepository @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig,
    private val gson: Gson,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : TrackRepository {

    override suspend fun getTracks(): List<Track> {
        return try {
            // Fetch remote config with timeout (5 seconds)
            kotlinx.coroutines.withTimeout(5000L) {
                remoteConfig.fetchAndActivate().await()
            }
            
            val json = remoteConfig.getString("track_config_v1")
            
            if (json.isBlank()) {
                println("Remote Config 'track_config_v1' is empty. Using defaults.")
                return getDefaultTracks()
            }

            val type = object : TypeToken<List<Track>>() {}.type
            val tracks: List<Track> = gson.fromJson(json, type)
            
            // P4 FIX: Telif Hakları için İsim Düzenlemesi (Sanitization)
            // Remote Config'den eski isimler gelse bile yerel güvenli isimlerle değiştir.
            val safeNameMapping = mapOf(
                "istanbul_park" to mapOf("tr" to "Boğaziçi Pisti", "en" to "Bosphorus Circuit"),
                "spa" to mapOf("tr" to "Orman Pisti", "en" to "Forest Apex"),
                "monaco" to mapOf("tr" to "Liman Pisti", "en" to "Harbor Circuit"),
                "monza" to mapOf("tr" to "Hız Tapınağı", "en" to "Speed Temple"),
                "silverstone" to mapOf("tr" to "Kraliyet Pisti", "en" to "Royal Circuit"),
                "imola" to mapOf("tr" to "Nehir Pisti", "en" to "River Circuit"),
                "interlagos" to mapOf("tr" to "Göl Pisti", "en" to "Lake Circuit"),
                "baku" to mapOf("tr" to "Kale Pisti", "en" to "Castle Circuit")
            )
            
            return tracks.map { track ->
                safeNameMapping[track.id]?.let { safeName ->
                    track.copy(genericName = safeName)
                } ?: track
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to defaults on error or timeout
            getDefaultTracks()
        }
    }

    override suspend fun getTrack(trackId: String): Track? {
        return getTracks().find { it.id == trackId }
    }

    override fun prepareTrackFile(track: Track): kotlinx.coroutines.flow.Flow<com.pace.legends.domain.model.DownloadState> = kotlinx.coroutines.flow.flow {
        val fileName = "track_${track.id}_v${track.version}.geojson"
        val file = java.io.File(context.filesDir, fileName)

        // 1. Senaryo A: Dosya var (Cache Hit)
        if (file.exists() && file.length() > 0) {
            // P8 FIX: Dosya boyutu 0 ise bozuk demektir, sil
            emit(com.pace.legends.domain.model.DownloadState.Success(file))
            return@flow
        }
        
        // Bozuk dosyayı sil (varsa)
        if (file.exists() && file.length() == 0L) {
            file.delete()
        }

        // 2. Senaryo B: İndirme Gerekli (Update/New)
        // Eski versiyonları temizle
        val filesDir = context.filesDir
        filesDir.listFiles { _, name -> 
            name.startsWith("track_${track.id}_v") && name != fileName 
        }?.forEach { it.delete() }

        // Fallback for fallback tracks (assets)
        if (track.geoJsonUrl.endsWith(".json") && !track.geoJsonUrl.startsWith("http")) {
            try {
                emit(com.pace.legends.domain.model.DownloadState.Downloading(0))
                // Asset dosyası ise kopyala
                context.assets.open(track.geoJsonUrl).use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                emit(com.pace.legends.domain.model.DownloadState.Success(file))
                return@flow
            } catch (e: Exception) {
                file.delete() // Bozuk dosyayı sil
                emit(com.pace.legends.domain.model.DownloadState.Error("Asset yüklenemedi: ${e.message}"))
                return@flow
            }
        }

        // P8 FIX: Retry mekanizması ile Remote Download
        val maxRetries = 3
        var lastError: Exception? = null
        
        for (attempt in 1..maxRetries) {
            try {
                emit(com.pace.legends.domain.model.DownloadState.Downloading(0))
                android.util.Log.d("TrackRepo", "Download attempt $attempt for ${track.id}")
                android.util.Log.d("TrackRepo", "URL length: ${track.geoJsonUrl.length}")
                android.util.Log.d("TrackRepo", "URL starts: ${track.geoJsonUrl.take(50)}")
                android.util.Log.d("TrackRepo", "URL ends: ${track.geoJsonUrl.takeLast(50)}")
                
                // 🔒 SECURITY: Only HTTPS allowed (prevents MITM attacks)
                val urlString = track.geoJsonUrl
                if (!urlString.startsWith("https://")) {
                    throw java.net.MalformedURLException("Security Error: Only HTTPS URLs are allowed. Got: $urlString")
                }
                
                val url = java.net.URL(urlString)
                
                // 🆕 Host validation
                if (url.host.isNullOrEmpty()) {
                    throw java.net.MalformedURLException("Invalid host in URL: $urlString")
                }
                
                val connection = url.openConnection() as java.net.HttpURLConnection
                
                // P8 FIX: Timeout ayarları
                connection.connectTimeout = 15000 // 15 saniye bağlantı timeout
                connection.readTimeout = 60000    // 60 saniye okuma timeout (büyük dosyalar için)
                connection.setRequestProperty("Accept", "application/json, */*")
                connection.connect()
                
                if (connection.responseCode != 200) {
                    throw java.io.IOException("HTTP ${connection.responseCode}: ${connection.responseMessage}")
                }
                
                val length = connection.contentLength
                val input = java.io.BufferedInputStream(connection.inputStream, 8192)
                val output = java.io.FileOutputStream(file)
                
                val data = ByteArray(1024)
                var total: Long = 0
                var count: Int
                
                while (input.read(data).also { count = it } != -1) {
                    total += count
                    output.write(data, 0, count)
                    
                    if (length > 0) {
                        val progress = (total * 100 / length).toInt()
                        emit(com.pace.legends.domain.model.DownloadState.Downloading(progress))
                    }
                }
                
                output.flush()
                output.close()
                input.close()
                connection.disconnect()
                
                // P8 FIX: İndirme sonrası doğrulama
                if (file.length() == 0L) {
                    file.delete()
                    throw java.io.IOException("Downloaded file is empty")
                }
                
                emit(com.pace.legends.domain.model.DownloadState.Success(file))
                return@flow
                
            } catch (e: Exception) {
                lastError = e
                file.delete() // Bozuk/kısmi dosyayı sil
                // 🆕 Detaylı hata logu
                android.util.Log.w("TrackRepo", "Download attempt $attempt failed for URL: ${track.geoJsonUrl}")
                android.util.Log.w("TrackRepo", "Error type: ${e.javaClass.simpleName}, Message: ${e.message}")
                
                if (attempt < maxRetries) {
                    kotlinx.coroutines.delay(1000L * attempt) // Exponential backoff
                }
            }
        }
        
        // Tüm denemeler başarısız
        val msg = lastError?.message ?: "Bilinmeyen Hata"
        emit(com.pace.legends.domain.model.DownloadState.Error("İndirme başarısız (3 deneme): $msg"))
        
    }.flowOn(kotlinx.coroutines.Dispatchers.IO)

    private fun getDefaultTracks(): List<Track> {
        // 🆕 Load from assets/default_tracks.json (easier to update without app release)
        return try {
            val jsonString = context.assets.open("default_tracks.json").bufferedReader().use { it.readText() }
            val type = object : com.google.gson.reflect.TypeToken<List<Track>>() {}.type
            gson.fromJson(jsonString, type)
        } catch (e: Exception) {
            android.util.Log.e("TrackRepo", "Failed to load default_tracks.json: ${e.message}")
            // Ultimate fallback: empty list (should never happen if assets are bundled correctly)
            emptyList()
        }
    }
}
