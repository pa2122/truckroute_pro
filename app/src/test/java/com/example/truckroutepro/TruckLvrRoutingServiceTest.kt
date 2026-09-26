package com.example.truckroutepro

import com.google.android.gms.maps.model.LatLng
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class TruckLvrRoutingServiceTest {

    private val testOrigin = LatLng(34.0522, -118.2437)
    private val testDestination = LatLng(36.1699, -115.1398)
    private val testProfile = TruckProfile()

    @Before
    fun setup() {
        mockkConstructor(URL::class)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `computeTruckRoute with blank API key returns fallback direct path`() = runTest {
        val result = TruckLvrRoutingService.computeTruckRoute(
            apiKey = "",
            origin = testOrigin,
            destination = testDestination,
            profile = testProfile
        )

        assertEquals("Map API key missing. Displaying direct path.", result.warningMessage)
        assertEquals(2, result.polylinePoints.size)
        assertEquals(testOrigin, result.polylinePoints[0])
        assertEquals(testDestination, result.polylinePoints[1])
        assertEquals(1, result.routeLegs.size)
    }

    @Test
    fun `computeTruckRoute parses valid JSON route correctly`() = runTest {
        val mockConnection = mockk<HttpURLConnection>()
        
        every { anyConstructed<URL>().openConnection() } returns mockConnection
        every { mockConnection.requestMethod = any() } returns Unit
        every { mockConnection.connectTimeout = any() } returns Unit
        every { mockConnection.readTimeout = any() } returns Unit
        every { mockConnection.responseCode } returns 200

        val validJson = """
            {
                "status": "OK",
                "routes": [
                    {
                        "summary": "I-15 N",
                        "legs": [
                            {
                                "distance": { "value": 400000 },
                                "duration": { "value": 14400 },
                                "start_address": "Los Angeles",
                                "end_address": "Las Vegas",
                                "steps": []
                            }
                        ],
                        "overview_polyline": {
                            "points": ""
                        }
                    }
                ]
            }
        """.trimIndent()
        
        every { mockConnection.inputStream } returns ByteArrayInputStream(validJson.toByteArray())

        val result = TruckLvrRoutingService.computeTruckRoute(
            apiKey = "VALID_KEY",
            origin = testOrigin,
            destination = testDestination,
            originAddress = "LA",
            destinationAddress = "Vegas",
            profile = testProfile
        )

        assertEquals("", result.warningMessage) // No fallback warning
        // 400000 meters in miles is ~248.548
        assertEquals(248.549, result.distanceMiles, 0.01)
        // 14400 seconds is 240 mins
        assertEquals(240, result.durationMins)
        assertEquals(1, result.routeLegs.size)
        assertEquals(248.549, result.routeLegs[0].distanceMiles, 0.01)
        assertEquals(240, result.routeLegs[0].durationMins)
    }
}
