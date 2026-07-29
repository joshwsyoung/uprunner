package com.uprunner.core.gpx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PolylineCodecTest {

    // Canonical example from Google's Encoded Polyline Algorithm Format documentation.
    private val canonicalPoints = listOf(38.5 to -120.2, 40.7 to -120.95, 43.252 to -126.453)

    @Test
    fun `decodes canonical precision-5 example`() {
        val decoded = PolylineCodec.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@", precision = 5)
        assertPointsEqual(canonicalPoints, decoded)
    }

    @Test
    fun `decodes the same points re-encoded at Valhalla's precision-6`() {
        // Cross-checked against an independent Python implementation of the same algorithm.
        val decoded = PolylineCodec.decode("_izlhA~rlgdF_{geC~ywl@_kwzCn`{nI", precision = 6)
        assertPointsEqual(canonicalPoints, decoded)
    }

    @Test
    fun `decodes small deltas at precision-6`() {
        val decoded = PolylineCodec.decode("_c`|@_gayBgEoK", precision = 6)
        assertPointsEqual(listOf(1.0 to 2.0, 1.0001 to 2.0002), decoded)
    }

    @Test
    fun `empty string decodes to no points`() {
        assertEquals(emptyList<Pair<Double, Double>>(), PolylineCodec.decode(""))
    }

    @Test
    fun `truncated input throws a clean IllegalArgumentException instead of crashing`() {
        // A real-world case that used to surface a raw StringIndexOutOfBoundsException to the
        // user ("length=141; index=141") instead of a catchable, friendly-messaged error.
        assertThrows(IllegalArgumentException::class.java) {
            PolylineCodec.decode("_p~iF~ps|U_ulLnnqC_mqN", precision = 5)
        }
    }

    @Test
    fun `mid-continuation truncation also throws cleanly`() {
        // Ends on a byte with the continuation bit set (>= 0x20 after the -63 offset), so the
        // decoder expects another byte that never arrives.
        assertThrows(IllegalArgumentException::class.java) {
            PolylineCodec.decode("_p~iF~ps|U_ulLnnqC_mqNvxq", precision = 5)
        }
    }

    private fun assertPointsEqual(expected: List<Pair<Double, Double>>, actual: List<Pair<Double, Double>>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) ->
            assertEquals(e.first, a.first, 0.00001)
            assertEquals(e.second, a.second, 0.00001)
        }
    }
}
