package com.uprunner.core.routing

import org.junit.Assert.assertEquals
import org.junit.Test

class ManeuverCodecTest {

    @Test
    fun `round-trips a list of maneuvers`() {
        val maneuvers = listOf(
            ValhallaRouting.Maneuver("Walk east on Main Street.", beginPointIndex = 0),
            ValhallaRouting.Maneuver("Turn left onto Oak Avenue.", beginPointIndex = 12),
            ValhallaRouting.Maneuver("You have arrived at your destination.", beginPointIndex = 47),
        )

        assertEquals(maneuvers, ManeuverCodec.decode(ManeuverCodec.encode(maneuvers)))
    }

    @Test
    fun `round-trips an empty list`() {
        assertEquals(emptyList<ValhallaRouting.Maneuver>(), ManeuverCodec.decode(ManeuverCodec.encode(emptyList())))
    }

    @Test
    fun `decoding blank input returns an empty list`() {
        assertEquals(emptyList<ValhallaRouting.Maneuver>(), ManeuverCodec.decode(""))
    }
}
