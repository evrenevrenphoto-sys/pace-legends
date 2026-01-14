package com.pace.legends.utils

import com.google.android.gms.maps.CameraUpdate
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds

/**
 * Google Maps için yardımcı extension fonksiyonları.
 */

/**
 * Verilen Polyline noktalarını kapsayan bir CameraUpdate oluşturur.
 *
 * @param path Polyline noktaları listesi
 * @param paddingPx Harita kenarlarından bırakılacak boşluk (piksel)
 * @return CameraUpdate veya null (path boşsa)
 */
fun createCameraUpdateForPolyline(path: List<LatLng>, paddingPx: Int = 100): CameraUpdate? {
    if (path.isEmpty()) return null
    
    val boundsBuilder = LatLngBounds.Builder()
    path.forEach { boundsBuilder.include(it) }
    val bounds = boundsBuilder.build()
    
    return CameraUpdateFactory.newLatLngBounds(bounds, paddingPx)
}

/**
 * Polyline sınırlarını hesaplar.
 *
 * @param path Polyline noktaları listesi
 * @return LatLngBounds veya null (path boşsa)
 */
fun calculateBoundsForPath(path: List<LatLng>): LatLngBounds? {
    if (path.isEmpty()) return null
    
    val boundsBuilder = LatLngBounds.Builder()
    path.forEach { boundsBuilder.include(it) }
    return boundsBuilder.build()
}
