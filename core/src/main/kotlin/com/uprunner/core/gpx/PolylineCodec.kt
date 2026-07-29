package com.uprunner.core.gpx

/**
 * Decodes Google's Encoded Polyline Algorithm Format
 * (https://developers.google.com/maps/documentation/utilities/polylinealgorithm), used by
 * routing engines such as Valhalla (which encodes at 6-digit decimal precision rather than
 * the original format's 5) to pack a route's shape into a compact string.
 */
object PolylineCodec {

    private data class Delta(val value: Int, val nextIndex: Int)

    fun decode(encoded: String, precision: Int = 5): List<Pair<Double, Double>> {
        val factor = Math.pow(10.0, precision.toDouble())
        val points = mutableListOf<Pair<Double, Double>>()
        var index = 0
        var lat = 0
        var lng = 0

        while (index < encoded.length) {
            val latDelta = decodeNextDelta(encoded, index)
                ?: throw IllegalArgumentException("Malformed polyline: truncated latitude value at index $index")
            lat += latDelta.value
            index = latDelta.nextIndex

            val lngDelta = decodeNextDelta(encoded, index)
                ?: throw IllegalArgumentException("Malformed polyline: truncated longitude value at index $index")
            lng += lngDelta.value
            index = lngDelta.nextIndex

            points.add(Pair(lat / factor, lng / factor))
        }
        return points
    }

    /** Returns null (rather than throwing) if the string ends mid-sequence, so [decode] can
     *  report a clean, catchable error instead of an out-of-bounds crash on malformed input. */
    private fun decodeNextDelta(encoded: String, startIndex: Int): Delta? {
        var index = startIndex
        var shift = 0
        var result = 0
        var b: Int
        do {
            if (index >= encoded.length) return null
            b = encoded[index].code - 63
            index++
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        val value = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        return Delta(value, index)
    }
}
