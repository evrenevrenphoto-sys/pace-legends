package com.pace.legends.utils

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import org.junit.Assert.*
import org.junit.Test

/**
 * MapExtensions için Unit Testler
 * 
 * Bu testler harita kamera bounds hesaplama mantığını doğrular.
 */
class MapBoundsTest {

    // ==================== TEST 1: Bounds Calculation ====================

    @Test
    fun `test_WhenMapLoaded_ThenCameraBoundsMatchPolyline`() {
        // Given: Bilinen koordinatlarla bir path
        val path = listOf(
            LatLng(40.0, 29.0),  // SW corner
            LatLng(41.0, 30.0),  // NE corner
            LatLng(40.5, 29.5)   // Middle point
        )

        // When: Bounds hesapla
        val bounds = calculateBoundsForPath(path)!!

        // Then: Bounds tüm noktaları içermeli
        assertTrue(bounds.contains(path[0]))
        assertTrue(bounds.contains(path[1]))
        assertTrue(bounds.contains(path[2]))

        // SW ve NE köşeleri doğru olmalı
        assertEquals(40.0, bounds.southwest.latitude, 0.001)
        assertEquals(29.0, bounds.southwest.longitude, 0.001)
        assertEquals(41.0, bounds.northeast.latitude, 0.001)
        assertEquals(30.0, bounds.northeast.longitude, 0.001)
    }

    // ==================== TEST 2: Empty Path Handling ====================

    @Test
    fun `test_WhenPathIsEmpty_ThenBoundsIsNull`() {
        // Given
        val emptyPath = emptyList<LatLng>()

        // When
        val bounds = calculateBoundsForPath(emptyPath)

        // Then
        assertNull(bounds)
    }

    // ==================== TEST 3: Single Point Path ====================

    @Test
    fun `test_WhenPathHasSinglePoint_ThenBoundsContainsPoint`() {
        // Given
        val singlePointPath = listOf(LatLng(40.95, 29.40))

        // When
        val bounds = calculateBoundsForPath(singlePointPath)!!

        // Then
        assertTrue(bounds.contains(singlePointPath[0]))
        assertEquals(singlePointPath[0].latitude, bounds.southwest.latitude, 0.001)
        assertEquals(singlePointPath[0].longitude, bounds.southwest.longitude, 0.001)
    }

    // ==================== TEST 4: Camera Update Creation ====================

    @Test
    fun `test_WhenPathIsValid_ThenCameraUpdateIsNotNull`() {
        // Given
        val path = listOf(
            LatLng(40.9522, 29.4061),
            LatLng(40.9630, 29.4173)
        )

        // When
        val cameraUpdate = createCameraUpdateForPolyline(path, 100)

        // Then
        assertNotNull(cameraUpdate)
    }

    @Test
    fun `test_WhenPathIsEmpty_ThenCameraUpdateIsNull`() {
        // Given
        val emptyPath = emptyList<LatLng>()

        // When
        val cameraUpdate = createCameraUpdateForPolyline(emptyPath, 100)

        // Then
        assertNull(cameraUpdate)
    }

    // ==================== TEST 5: Large Track Bounds ====================

    @Test
    fun `test_WhenTrackSpansLargeArea_ThenBoundsAreCorrect`() {
        // Given: İstanbul Park benzeri geniş pist
        val istanbulParkPath = listOf(
            LatLng(40.9522, 29.4061),
            LatLng(40.9526, 29.4087),
            LatLng(40.9630, 29.4173),
            LatLng(40.9516, 29.4012)
        )

        // When
        val bounds = calculateBoundsForPath(istanbulParkPath)!!

        // Then: Min/max değerler doğru olmalı
        // Min lat: 40.9516, Max lat: 40.9630
        // Min lng: 29.4012, Max lng: 29.4173
        assertEquals(40.9516, bounds.southwest.latitude, 0.0001)
        assertEquals(29.4012, bounds.southwest.longitude, 0.0001)
        assertEquals(40.9630, bounds.northeast.latitude, 0.0001)
        assertEquals(29.4173, bounds.northeast.longitude, 0.0001)
    }
}
