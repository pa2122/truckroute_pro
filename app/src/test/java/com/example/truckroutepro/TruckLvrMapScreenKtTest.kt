package com.example.truckroutepro

import org.junit.Assert.assertEquals
import org.junit.Test

class TruckLvrMapScreenKtTest {

    @Test
    fun `formatDurationMins formats less than 60 mins correctly`() {
        assertEquals("45m", formatDurationMins(45))
        assertEquals("0m", formatDurationMins(0))
        assertEquals("59m", formatDurationMins(59))
    }

    @Test
    fun `formatDurationMins formats exactly hours correctly`() {
        assertEquals("1h", formatDurationMins(60))
        assertEquals("2h", formatDurationMins(120))
    }

    @Test
    fun `formatDurationMins formats hours and minutes correctly`() {
        assertEquals("1h 25m", formatDurationMins(85))
        assertEquals("2h 5m", formatDurationMins(125))
    }
}
