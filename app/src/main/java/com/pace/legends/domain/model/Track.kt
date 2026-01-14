package com.pace.legends.domain.model


import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName

data class Track(
    @get:PropertyName("id")
    val id: String = "",
    
    @get:PropertyName("genericName")
    val genericName: Map<String, String> = emptyMap(),

    @get:PropertyName("description")
    val description: Map<String, String> = emptyMap(),
    
    @get:PropertyName("geoJsonUrl")
    val geoJsonUrl: String = "",
    
    @get:PropertyName("totalDistanceMeters")
    val totalDistanceMeters: Int = 0,
    
    @get:PropertyName("isActive")
    val isActive: Boolean = true,
    
    @get:PropertyName("isPremium")
    val isPremium: Boolean = false,

    @get:PropertyName("recordTimeSeconds")
    val recordTimeSeconds: Long = 0,

    @get:PropertyName("version")
    val version: Int = 1,
    
    // Pist yönünü tersine çevirmek için (Saat yönü vs tersi)
    @get:PropertyName("reverseDirection")
    val reverseDirection: Boolean = false
) {
    // F1 tarzı sektörler - Room tarafından ignore edilir, runtime'da set edilir
    // F1 tarzı sektörler - Room tarafından ignore edilir, runtime'da set edilir
    @get:PropertyName("sectors")
    var sectors: List<Sector> = emptyList()

    // Harita çizimi ve interpolasyon için nokta listesi (Runtime)
    @get:Exclude
    var path: List<com.google.android.gms.maps.model.LatLng>? = null
    
    // Harita merkezi (Runtime)
    @get:Exclude
    var center: com.google.android.gms.maps.model.LatLng? = null
    
    // No-arg constructor for Firestore
    constructor() : this("", emptyMap(), emptyMap(), "", 0, true, false, 0, 1, false)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Track

        if (id != other.id) return false
        if (genericName != other.genericName) return false
        if (description != other.description) return false
        if (geoJsonUrl != other.geoJsonUrl) return false
        if (totalDistanceMeters != other.totalDistanceMeters) return false
        if (isActive != other.isActive) return false
        if (isPremium != other.isPremium) return false
        if (recordTimeSeconds != other.recordTimeSeconds) return false
        if (version != other.version) return false
        if (reverseDirection != other.reverseDirection) return false
        if (sectors != other.sectors) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + genericName.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + geoJsonUrl.hashCode()
        result = 31 * result + totalDistanceMeters
        result = 31 * result + isActive.hashCode()
        result = 31 * result + isPremium.hashCode()
        result = 31 * result + recordTimeSeconds.hashCode()
        result = 31 * result + version
        result = 31 * result + reverseDirection.hashCode()
        result = 31 * result + sectors.hashCode()
        return result
    }
}

