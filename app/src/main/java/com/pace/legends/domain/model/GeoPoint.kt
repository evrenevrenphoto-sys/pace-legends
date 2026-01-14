package com.pace.legends.domain.model

/**
 * Platform-independent geographic point representation.
 * 
 * Used in domain layer to avoid Google Maps SDK dependency.
 * Converted to/from platform-specific types (LatLng) in UI/Data layers.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double
) {
    companion object {
        val ZERO = GeoPoint(0.0, 0.0)
        
        /**
         * Creates GeoPoint from Google Maps LatLng.
         * Should only be called from UI/Data layers.
         */
        fun fromLatLng(latLng: com.google.android.gms.maps.model.LatLng): GeoPoint {
            return GeoPoint(latLng.latitude, latLng.longitude)
        }
    }
    
    /**
     * Converts to Google Maps LatLng.
     * Should only be called from UI/Data layers.
     */
    fun toLatLng(): com.google.android.gms.maps.model.LatLng {
        return com.google.android.gms.maps.model.LatLng(latitude, longitude)
    }
    
    /**
     * Distance calculation using Haversine formula (in meters).
     */
    fun distanceTo(other: GeoPoint): Double {
        val earthRadius = 6371000.0 // meters
        val lat1Rad = Math.toRadians(latitude)
        val lat2Rad = Math.toRadians(other.latitude)
        val deltaLat = Math.toRadians(other.latitude - latitude)
        val deltaLng = Math.toRadians(other.longitude - longitude)
        
        val a = kotlin.math.sin(deltaLat / 2) * kotlin.math.sin(deltaLat / 2) +
                kotlin.math.cos(lat1Rad) * kotlin.math.cos(lat2Rad) *
                kotlin.math.sin(deltaLng / 2) * kotlin.math.sin(deltaLng / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        
        return earthRadius * c
    }
}
