package com.uprunner.core.gpx

import com.uprunner.core.model.GpxPoint
import com.uprunner.core.model.GpxTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GpxWriterTest {

    @Test
    fun `round-trips through GpxParser`() {
        val track = GpxTrack(
            name = "Planned route",
            points = listOf(
                GpxPoint(51.5074, -0.1278, elevationMeters = 12.3, timeMillis = 1_780_297_200_000L),
                GpxPoint(51.5080, -0.1270, elevationMeters = null, timeMillis = null),
            ),
        )

        val reparsed = GpxParser.parse(GpxWriter.write(track))

        assertEquals(track.name, reparsed.name)
        assertEquals(track.points.size, reparsed.points.size)
        track.points.zip(reparsed.points).forEach { (expected, actual) ->
            assertEquals(expected.latitude, actual.latitude, 0.000001)
            assertEquals(expected.longitude, actual.longitude, 0.000001)
            assertEquals(expected.elevationMeters, actual.elevationMeters)
            assertEquals(expected.timeMillis, actual.timeMillis)
        }
    }

    @Test
    fun `track without a name round-trips to a null name`() {
        val track = GpxTrack(name = null, points = listOf(GpxPoint(1.0, 2.0, null, null)))
        assertNull(GpxParser.parse(GpxWriter.write(track)).name)
    }

    @Test
    fun `escapes special characters in the track name`() {
        val track = GpxTrack(name = "Riverside & <fast> loop", points = listOf(GpxPoint(1.0, 2.0, null, null)))
        val reparsed = GpxParser.parse(GpxWriter.write(track))
        assertEquals("Riverside & <fast> loop", reparsed.name)
    }
}
