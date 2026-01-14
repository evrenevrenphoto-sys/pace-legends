package com.pace.legends.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pace.legends.BuildConfig
import com.pace.legends.domain.manager.RaceLocationManager

/**
 * 🐞 Debug Overlay: GPS Hız Göstergesi
 * 
 * Sadece DEBUG build'lerde görünür.
 * Saha testlerinde GPS hızını ve Anti-Cheat durumunu izlemek için kullanılır.
 * 
 * Kullanım: TrackDetailScreen'in harita üzerine yerleştirin.
 */
@Composable
fun DebugSpeedOverlay(
    locationManager: RaceLocationManager,
    stepCadence: Float = 0f,
    modifier: Modifier = Modifier
) {
    // Sadece DEBUG buildlerde göster
    if (!BuildConfig.DEBUG) return
    
    val speedMps by locationManager.currentSpeedMps.collectAsState()
    val accuracy by locationManager.accuracy.collectAsState()
    val isTracking by locationManager.isTracking.collectAsState()
    
    val speedKmh = speedMps * 3.6f
    
    // Renk kodlaması
    val backgroundColor = when {
        !isTracking -> Color.Gray.copy(alpha = 0.7f)
        speedKmh > 25f && stepCadence < 0.5f -> Color.Red.copy(alpha = 0.8f) // Hile şüphesi
        speedKmh > 15f -> Color.Yellow.copy(alpha = 0.7f) // Dikkat
        else -> Color.Green.copy(alpha = 0.7f) // Normal
    }
    
    Card(
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        modifier = modifier.padding(8.dp)
    ) {
        Text(
            text = buildString {
                append("🐞 DEBUG MODE\n")
                append("GPS: ${String.format("%.1f", speedKmh)} km/h\n")
                append("Acc: ${String.format("%.0f", accuracy)}m\n")
                append("Cadence: ${String.format("%.2f", stepCadence)} step/s\n")
                append(if (isTracking) "📍 Tracking" else "⏸️ Stopped")
            },
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(8.dp)
        )
    }
}
