package com.uprunner.core.gpx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class GpxParserTest {

    private val sampleGpx = """
        <?xml version="1.0" encoding="UTF-8"?>
        <gpx version="1.1" creator="uprunner-test">
          <trk>
            <name>Riverside Loop</name>
            <trkseg>
              <trkpt lat="51.5074" lon="-0.1278">
                <ele>12.3</ele>
                <time>2026-06-01T07:00:00Z</time>
              </trkpt>
              <trkpt lat="51.5080" lon="-0.1270">
                <ele>13.1</ele>
                <time>2026-06-01T07:00:30Z</time>
              </trkpt>
            </trkseg>
            <trkseg>
              <trkpt lat="51.5090" lon="-0.1260">
                <ele>14.0</ele>
                <time>2026-06-01T07:01:00Z</time>
              </trkpt>
            </trkseg>
          </trk>
        </gpx>
    """.trimIndent()

    @Test
    fun `parses track name and points across multiple segments`() {
        val track = GpxParser.parse(sampleGpx)

        assertEquals("Riverside Loop", track.name)
        assertEquals(3, track.points.size)
    }

    @Test
    fun `parses lat lon elevation and time for each point`() {
        val track = GpxParser.parse(sampleGpx)
        val first = track.points[0]

        assertEquals(51.5074, first.latitude, 0.0001)
        assertEquals(-0.1278, first.longitude, 0.0001)
        assertEquals(12.3, first.elevationMeters!!, 0.001)
        assertEquals(1780297200000L, first.timeMillis) // 2026-06-01T07:00:00Z
    }

    @Test
    fun `missing elevation and time are null without throwing`() {
        val gpx = """
            <gpx><trk><trkseg>
              <trkpt lat="1.0" lon="2.0"></trkpt>
            </trkseg></trk></gpx>
        """.trimIndent()

        val track = GpxParser.parse(gpx)

        assertEquals(1, track.points.size)
        assertNull(track.points[0].elevationMeters)
        assertNull(track.points[0].timeMillis)
    }

    @Test
    fun `missing track name is null`() {
        val gpx = """
            <gpx><trk><trkseg><trkpt lat="1.0" lon="2.0"></trkpt></trkseg></trk></gpx>
        """.trimIndent()

        assertNull(GpxParser.parse(gpx).name)
    }

    @Test
    fun `throws on gpx with no track element`() {
        assertThrows(IllegalArgumentException::class.java) {
            GpxParser.parse("<gpx></gpx>")
        }
    }
}
