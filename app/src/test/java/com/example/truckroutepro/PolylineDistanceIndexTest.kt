package com.example.truckroutepro

import android.location.Location
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PolylineDistanceIndexTest {

    @Test
    fun `initialization computes cumulative array accurately`() {
        val p1 = LatLng(30.0, -90.0)
        val p2 = LatLng(30.1, -90.0)
        val p3 = LatLng(30.2, -90.0)

        val polyline = listOf(p1, p2, p3)
        val index = PolylineDistanceIndex(polyline)

        assertEquals(3, index.cumulativeMeters.size)
        assertEquals(0.0, index.cumulativeMeters[0], 0.001)

        val res1 = FloatArray(1)
        Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, res1)
        assertEquals(res1[0].toDouble(), index.cumulativeMeters[1], 0.1)

        val res2 = FloatArray(1)
        Location.distanceBetween(p2.latitude, p2.longitude, p3.latitude, p3.longitude, res2)
        assertEquals((res1[0] + res2[0]).toDouble(), index.cumulativeMeters[2], 0.1)
    }

    @Test
    fun `getPointAtDistance returns correct points along the route`() {
        val p1 = LatLng(30.0, -90.0)
        val p2 = LatLng(30.1, -90.0)
        val p3 = LatLng(30.2, -90.0)

        val polyline = listOf(p1, p2, p3)
        val index = PolylineDistanceIndex(polyline)

        // Point at negative or 0 distance should be the first point
        assertEquals(p1, index.getPointAtDistance(-5.0))
        assertEquals(p1, index.getPointAtDistance(0.0))

        // Get total distance in miles
        val totalMeters = index.cumulativeMeters.last()
        val totalMiles = totalMeters / 1609.34

        // Point at distances larger than total distance should return the last point
        assertEquals(p3, index.getPointAtDistance(totalMiles + 10.0))

        // Find mid point
        val midMiles = index.cumulativeMeters[1] / 1609.34
        
        // Due to integer arithmetic in binary search, it returns the index before the exact match if target < current.
        // It returns (low - 1). Let's test a point slightly before the mid.
        val beforeMidPoint = index.getPointAtDistance(midMiles - 0.001)
        assertEquals(p1, beforeMidPoint)
        
        val afterMidPoint = index.getPointAtDistance(midMiles + 0.001)
        assertEquals(p2, afterMidPoint)
    }

    @Test
    fun `calculateStopMileMarkerAndCorridor rejects points off-route`() {
        val p1 = LatLng(30.0, -90.0)
        val p2 = LatLng(30.1, -90.0)
        val p3 = LatLng(30.2, -90.0)

        val polyline = listOf(p1, p2, p3)
        val index = PolylineDistanceIndex(polyline)

        // Point very far away
        val offRoutePoint = LatLng(40.0, -100.0)
        
        val result = index.calculateStopMileMarkerAndCorridor(offRoutePoint, maxOffRouteMiles = 10.0)
        assertNull(result)
    }

    @Test
    fun `calculateStopMileMarkerAndCorridor returns correct exact mile marker along polyline for points within corridor`() {
        val p1 = LatLng(30.0, -90.0)
        val p2 = LatLng(30.1, -90.0)
        val p3 = LatLng(30.2, -90.0)

        val polyline = listOf(p1, p2, p3)
        val index = PolylineDistanceIndex(polyline)

        // A point near p2, but within corridor
        val nearP2 = LatLng(30.1, -90.01)

        val result = index.calculateStopMileMarkerAndCorridor(nearP2, maxOffRouteMiles = 10.0)
        assertNotNull(result)

        val (mileMarker, distOffRouteMiles) = result!!

        // Expected mile marker is the distance up to p2
        val expectedMileMarker = index.cumulativeMeters[1] / 1609.34
        assertEquals(expectedMileMarker, mileMarker, 0.01)

        // distOffRouteMiles should be distance between p2 and nearP2
        val res = FloatArray(1)
        Location.distanceBetween(p2.latitude, p2.longitude, nearP2.latitude, nearP2.longitude, res)
        val expectedOffRouteMiles = res[0] / 1609.34
        
        assertEquals(expectedOffRouteMiles, distOffRouteMiles, 0.01)
    }

    @Test
    fun `calculateStopMileMarkerAndCorridor handles empty polyline`() {
        val polyline = emptyList<LatLng>()
        val index = PolylineDistanceIndex(polyline)

        val result = index.calculateStopMileMarkerAndCorridor(LatLng(30.0, -90.0))
        assertNotNull(result)
        assertEquals(0.0, result!!.first, 0.0)
        assertEquals(0.0, result.second, 0.0)
    }
}
