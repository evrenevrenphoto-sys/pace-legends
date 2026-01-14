package com.pace.legends.domain.model

/**
 * F1 tarzı sektör tanımı.
 * Her pist 3 sektöre bölünür (S1, S2, S3).
 * Performans karşılaştırması sektör bazında yapılır.
 */
data class Sector(
    val id: String = "",
    val name: String = "Sector 1",
    val startIndex: Int = 0,  // GeoJSON koordinat listesindeki başlangıç indeksi
    val endIndex: Int = 0,    // GeoJSON koordinat listesindeki bitiş indeksi
    val parTimeSeconds: Long = 0  // Bu sektör için hedeflenen ideal süre
) {
    // No-arg constructor for Firestore
    constructor() : this("", "Sector 1", 0, 0, 0)
}
