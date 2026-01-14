package com.pace.legends.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pace.legends.domain.model.Sector
import com.pace.legends.domain.model.Track

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey
    val id: String,
    val genericName: Map<String, String>,
    val description: Map<String, String>,
    val geoJsonUrl: String,
    val totalDistanceMeters: Int,
    val isActive: Boolean,
    val isPremium: Boolean,
    val recordTimeSeconds: Long,
    val version: Int,
    val reverseDirection: Boolean
) {
    fun toDomain(): Track {
        val track = Track(
            id = id,
            genericName = genericName,
            description = description,
            geoJsonUrl = geoJsonUrl,
            totalDistanceMeters = totalDistanceMeters,
            isActive = isActive,
            isPremium = isPremium,
            recordTimeSeconds = recordTimeSeconds,
            version = version,
            reverseDirection = reverseDirection
        )
        // Sectors and transient fields are set at runtime via external logic (JsonParsing)
        return track
    }

    companion object {
        fun fromDomain(track: Track): TrackEntity {
            return TrackEntity(
                id = track.id,
                genericName = track.genericName,
                description = track.description,
                geoJsonUrl = track.geoJsonUrl,
                totalDistanceMeters = track.totalDistanceMeters,
                isActive = track.isActive,
                isPremium = track.isPremium,
                recordTimeSeconds = track.recordTimeSeconds,
                version = track.version,
                reverseDirection = track.reverseDirection
            )
        }
    }
}
