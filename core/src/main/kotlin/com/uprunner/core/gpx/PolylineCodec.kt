package com.uprunner.core.gpx

/**
 * Decodes Google's Encoded Polyline Algorithm Format
 * (https://developers.google.com/maps/documentation/utilities/polylinealgorithm), used by
 * routing engines such as Valhalla (which encodes at 6-digit decimal precision rather than
 * the original format's 5) to pack a route's shape into a compact string.
 */
object PolylineCodec {

    fun decode(encoded: String, precision: Int = 5): List<Pair<Double, Double>> {
        val factor = Math.pow(10.0, precision.toDouble())
        val points = mutableListOf<Pair<Double, Double>>()
        var index = 0
        var lat = 0
        var lng = 0

        while (index < encoded.length) {
            var shift = 0
            var result = 0
            var b: Int
            do {
                b = encoded[index].code - 63
                index++
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            shift = 0
            result = 0
            do {
                b = encoded[index].code - 63
                index++
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            points.add(Pair(lat / factor, lng / factor))
        }
        return points
    }
}
