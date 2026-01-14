package com.pace.legends.domain.util

import com.google.android.gms.maps.model.LatLng
import com.pace.legends.domain.model.Track
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.pow
import kotlin.math.PI

object MapUtils {

    /**
     * Pist üzerindeki mesafeye denk gelen koordinatı hesaplar.
     * 
     * @param track Pist bilgisi (koordinatları içermeli)
     * @param distanceMeters Başlangıçtan itibaren katedilen mesafe
     * @return Harita üzerindeki LatLng noktası veya pistin başlangıç noktası
     */
    fun calculatePositionOnTrack(track: Track, distanceMeters: Double): LatLng {
        val path = track.path
        if (path.isNullOrEmpty()) {
            // Path yoksa center'ı dön veya 0,0
            return track.center ?: LatLng(0.0, 0.0)
        }

        val points = path!!
        
        // Eğer mesafe <= 0 ise başlangıç noktası
        if (distanceMeters <= 0) return points.first()

        var remainingDistance = distanceMeters
        
        // Pist noktaları arasında iterasyon yap
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            
            val segmentDistance = calculateDistance(p1, p2)
            
            if (remainingDistance <= segmentDistance) {
                // Aradığımız nokta bu segment üzerinde
                return interpolate(p1, p2, remainingDistance / segmentDistance)
            }
            
            remainingDistance -= segmentDistance
        }
        
        // Mesafe pistten uzunsa son noktayı dön
        return points.last()
    }

    /**
     * İki nokta arasındaki mesafeyi hesaplar (Haversine formülü veya basit öklid - burada küçük mesafeler için)
     * Android'in Location.distanceTo metodunun basitleştirilmiş hali.
     */
    private fun calculateDistance(p1: LatLng, p2: LatLng): Double {
        val R = 6371000.0 // Dünya yarıçapı (metre)
        val lat1 = Math.toRadians(p1.latitude)
        val lat2 = Math.toRadians(p2.latitude)
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLng = Math.toRadians(p2.longitude - p1.longitude)

        val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLng / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return R * c
    }

    /**
     * İki nokta arasında, orana (fraction) göre ara nokta bulur.
     * @param fraction 0.0 (p1) ile 1.0 (p2) arası
     */
    private fun interpolate(p1: LatLng, p2: LatLng, fraction: Double): LatLng {
        val lat = p1.latitude + (p2.latitude - p1.latitude) * fraction
        val lng = p1.longitude + (p2.longitude - p1.longitude) * fraction
        return LatLng(lat, lng)
    }
}
