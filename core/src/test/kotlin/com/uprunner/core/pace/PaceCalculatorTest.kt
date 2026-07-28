package com.uprunner.core.pace

import com.uprunner.core.model.LocationSample
import com.uprunner.core.model.PaceSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaceCalculatorTest {

    private val metersPerDegreeLat = 111_320.0

    /** Builds a straight-line-north sample at [stepMeters] past the previous one. */
    private fun sampleAt(stepIndex: Int, stepMeters: Double, timestampMillis: Long, accuracyMeters: Float? = 5f) =
        LocationSample(
            latitude = stepIndex * (stepMeters / metersPerDegreeLat),
            longitude = 0.0,
            timestampMillis = timestampMillis,
            accuracyMeters = accuracyMeters,
        )

    @Test
    fun `pace is null until first sample`() {
        val calculator = PaceCalculator()
        val snapshot = calculator.addSample(sampleAt(0, 0.0, 0L))
        assertNull(snapshot.currentPaceSecPerKm)
        assertEquals(0.0, snapshot.totalDistanceMeters, 0.001)
    }

    @Test
    fun `pace is null until window has accrued enough distance`() {
        val calculator = PaceCalculator()
        calculator.addSample(sampleAt(0, 3.0, 0L))
        // Only 2 meters moved so far — below the 5m minimum for a pace reading.
        val snapshot = calculator.addSample(sampleAt(1, 2.0, 1_000L))
        assertNull(snapshot.currentPaceSecPerKm)
    }

    @Test
    fun `constant speed track converges to expected pace`() {
        val calculator = PaceCalculator()
        val stepMeters = 3.0 // 3 m/s at 1s intervals -> ~333.33 sec/km
        var lastSnapshot: PaceSnapshot? = null
        var time = 0L
        repeat(60) { i ->
            lastSnapshot = calculator.addSample(sampleAt(i, stepMeters, time))
            time += 1_000
        }
        val pace = lastSnapshot!!.currentPaceSecPerKm
        assertNotNull(pace)
        assertEquals(333.33, pace!!, 5.0)
    }

    @Test
    fun `window drops stale distance once 50m of newer movement has accrued`() {
        val calculator = PaceCalculator()
        var time = 0L
        var lat = 0.0
        var lastSnapshot: PaceSnapshot? = calculator.addSample(LocationSample(lat, 0.0, time, 5f))

        fun step(stepMeters: Double) {
            lat += stepMeters / metersPerDegreeLat
            time += 1_000
            lastSnapshot = calculator.addSample(LocationSample(lat, 0.0, time, 5f))
        }

        // Slow phase: 2.5 m/s (400 sec/km) for 250m — well over the 50m window.
        repeat(100) { step(2.5) }

        // Fast phase: 4 m/s (250 sec/km) for another 120m.
        repeat(30) { step(4.0) }

        val pace = lastSnapshot!!.currentPaceSecPerKm
        assertNotNull(pace)
        // If the window still included slow-phase samples, pace would drift toward 400.
        assertEquals(250.0, pace!!, 20.0)
    }

    @Test
    fun `low accuracy sample is rejected without corrupting totals`() {
        val calculator = PaceCalculator()
        var time = 0L
        repeat(20) { i ->
            calculator.addSample(sampleAt(i, 3.0, time))
            time += 1_000
        }
        val before = calculator.addSample(sampleAt(20, 3.0, time))
        time += 1_000

        // A wildly inaccurate fix (100km away) with poor accuracy must be ignored.
        val noisy = LocationSample(
            latitude = before.totalDistanceMeters + 100_000.0,
            longitude = 0.0,
            timestampMillis = time,
            accuracyMeters = 50f,
        )
        val after = calculator.addSample(noisy)

        assertEquals(before.totalDistanceMeters, after.totalDistanceMeters, 0.001)
        assertEquals(before.currentPaceSecPerKm, after.currentPaceSecPerKm)
    }

    @Test
    fun `stationary samples do not corrupt totals or throw`() {
        val calculator = PaceCalculator()
        var time = 0L
        repeat(20) { i ->
            calculator.addSample(sampleAt(i, 3.0, time))
            time += 1_000
        }
        val moving = calculator.addSample(sampleAt(20, 3.0, time))
        time += 1_000

        var stationary: PaceSnapshot? = null
        val stillPoint = sampleAt(20, 3.0, time)
        repeat(10) {
            stationary = calculator.addSample(stillPoint.copy(timestampMillis = time))
            time += 1_000
        }

        assertEquals(moving.totalDistanceMeters, stationary!!.totalDistanceMeters, 0.001)
        assertTrue(stationary!!.elapsedTimeMillis > moving.elapsedTimeMillis)
    }
}
