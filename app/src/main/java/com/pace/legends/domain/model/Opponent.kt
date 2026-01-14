package com.pace.legends.domain.model

import com.google.android.gms.maps.model.LatLng

data class Opponent(
    val id: String,           // Firestore User ID
    val displayName: String,
    val currentPosition: LatLng,
    val leagueTier: String,   // Araç rengini belirlemek için (GOLD, SILVER, BRONZE...)
    val speedKmph: Float = 0f,
    val totalDistance: Double = 0.0
)
