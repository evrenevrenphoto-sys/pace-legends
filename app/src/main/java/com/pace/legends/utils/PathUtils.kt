package com.pace.legends.utils

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import javax.inject.Inject

class PathUtils @Inject constructor() {

    /**
     * Calculates the current position on the track based on the total distance walked.
     * @param path List of LatLng coordinates representing the track.
     * @param distanceMeters Total distance walked by the user in meters.
     * @return The interpolated LatLng position on the track.
     */
    fun calculateCurrentPosition(path: List<LatLng>, distanceMeters: Double): LatLng {
        if (path.isEmpty()) return LatLng(0.0, 0.0)
        if (distanceMeters <= 0) return path.first()

        var remainingDistance = distanceMeters
        val totalTrackLength = SphericalUtil.computeLength(path)

        // Handle loops (if user walked more than track length)
        remainingDistance %= totalTrackLength

        // Iterate through segments to find the current segment
        for (i in 0 until path.size - 1) {
            val start = path[i]
            val end = path[i + 1]
            val segmentDistance = SphericalUtil.computeDistanceBetween(start, end)

            if (remainingDistance <= segmentDistance) {
                // Determine the fraction of the segment covered
                val fraction = remainingDistance / segmentDistance
                return SphericalUtil.interpolate(start, end, fraction)
            } else {
                remainingDistance -= segmentDistance
            }
        }

        return path.last()
    }
    /**
     * Calculates the path segments covered by the user.
     */
    fun calculateWalkedPath(path: List<LatLng>, distanceMeters: Double): List<LatLng> {
        if (path.isEmpty() || distanceMeters <= 0) return emptyList()

        val walkedPath = mutableListOf<LatLng>()
        walkedPath.add(path.first())

        var remainingDistance = distanceMeters
        val totalTrackLength = SphericalUtil.computeLength(path)
        
        // If multiple loops, just show full path as walked or handle logic differently
        // For simplicity, we show current loop progress
        remainingDistance %= totalTrackLength

        for (i in 0 until path.size - 1) {
            val start = path[i]
            val end = path[i + 1]
            val segmentDistance = SphericalUtil.computeDistanceBetween(start, end)

            if (remainingDistance <= segmentDistance) {
                val fraction = remainingDistance / segmentDistance
                val endPoint = SphericalUtil.interpolate(start, end, fraction)
                walkedPath.add(endPoint)
                break
            } else {
                walkedPath.add(end)
                remainingDistance -= segmentDistance
            }
        }
        return walkedPath
    }

    /**
     * Smooths a path using Chaikin's Algorithm (Corner Cutting).
     * @param path Original list of LatLng points
     * @param iterations Number of smoothing iterations (higher = smoother)
     */
    fun smoothPath(path: List<LatLng>, iterations: Int = 2): List<LatLng> {
        if (path.size < 3) return path
        
        var currentPath = path
        
        repeat(iterations) {
            val newPath = mutableListOf<LatLng>()
            // Always keep the first point
            newPath.add(currentPath.first())
            
            for (i in 0 until currentPath.size - 1) {
                val p0 = currentPath[i]
                val p1 = currentPath[i+1]
                
                // Cut 25% from start and 25% from end of segment
                val q = SphericalUtil.interpolate(p0, p1, 0.25)
                val r = SphericalUtil.interpolate(p0, p1, 0.75)
                
                newPath.add(q)
                newPath.add(r)
            }
            
            // Should close the loop if it's a circuit
            if (currentPath.first() == currentPath.last()) {
                newPath.add(currentPath.last())
            } else {
               // For open paths, add the last point
               newPath.add(currentPath.last())
            }
            
            currentPath = newPath
        }
        
        return currentPath
    }
}
