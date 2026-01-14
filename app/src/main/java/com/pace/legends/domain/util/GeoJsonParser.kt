package com.pace.legends.domain.util

import com.google.android.gms.maps.model.LatLng
import com.pace.legends.domain.model.Sector
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeoJsonParser @Inject constructor() {

    data class GeoJsonResult(
        val path: List<LatLng>,
        val sectors: List<Sector>
    )

    fun parse(jsonString: String): GeoJsonResult {
        return try {
            val root = JSONObject(jsonString)
            val features = root.getJSONArray("features")
            
            if (features.length() > 0) {
                val feature = features.getJSONObject(0)
                val geometry = feature.getJSONObject("geometry")
                val coordinates = geometry.getJSONArray("coordinates")
                
                val latLngList = mutableListOf<LatLng>()
                for (i in 0 until coordinates.length()) {
                    val coord = coordinates.getJSONArray(i)
                    val lon = coord.getDouble(0)
                    val lat = coord.getDouble(1)
                    latLngList.add(LatLng(lat, lon))
                }
                
                // Close the loop
                if (latLngList.isNotEmpty()) {
                    val first = latLngList.first()
                    val last = latLngList.last()
                    val dist = FloatArray(1)
                    android.location.Location.distanceBetween(
                        first.latitude, first.longitude,
                        last.latitude, last.longitude,
                        dist
                    )
                    if (dist[0] > 10) {
                        latLngList.add(first)
                    }
                }
                
                val sectors = parseSectors(feature, latLngList.size)
                GeoJsonResult(latLngList, sectors)
            } else {
                GeoJsonResult(emptyList(), emptyList())
            }
        } catch (e: Exception) {
            android.util.Log.e("GeoJsonParser", "Parse error: ${e.message}", e)
            GeoJsonResult(emptyList(), emptyList())
        }
    }

    private fun parseSectors(feature: JSONObject, totalPoints: Int): List<Sector> {
        return try {
            if (feature.has("properties")) {
                val properties = feature.getJSONObject("properties")
                if (properties.has("sectors")) {
                    val sectorsArray = properties.getJSONArray("sectors")
                    val sectorList = mutableListOf<Sector>()
                    for (i in 0 until sectorsArray.length()) {
                        val sectorObj = sectorsArray.getJSONObject(i)
                        sectorList.add(
                            Sector(
                                id = sectorObj.optString("id", "sector_${i + 1}"),
                                name = sectorObj.optString("name", "Sector ${i + 1}"),
                                startIndex = sectorObj.optInt("startIndex", 0),
                                endIndex = sectorObj.optInt("endIndex", totalPoints - 1),
                                parTimeSeconds = sectorObj.optLong("parTimeSeconds", 0)
                            )
                        )
                    }
                    return sectorList
                }
            }
            createDefaultSectors(totalPoints)
        } catch (e: Exception) {
            createDefaultSectors(totalPoints)
        }
    }

    private fun createDefaultSectors(totalPoints: Int): List<Sector> {
        if (totalPoints < 3) return listOf(
            Sector("sector_1", "Sector 1", 0, totalPoints - 1, 0)
        )
        val sectorSize = totalPoints / 3
        return listOf(
            Sector("sector_1", "Sector 1", 0, sectorSize, 0),
            Sector("sector_2", "Sector 2", sectorSize, sectorSize * 2, 0),
            Sector("sector_3", "Sector 3", sectorSize * 2, totalPoints - 1, 0)
        )
    }
}
