package com.uprunner.core.gpx

import com.uprunner.core.model.GpxTrack
import java.time.Instant

/** Serializes a [GpxTrack] back to GPX 1.1 text — the inverse of [GpxParser], used to persist
 *  a routed/planned track through the same Room `gpxRaw` column a loaded file uses. */
object GpxWriter {

    fun write(track: GpxTrack): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<gpx version=\"1.1\" creator=\"uprunner\">\n")
        append("<trk>\n")
        track.name?.let { append("<name>").append(escapeXml(it)).append("</name>\n") }
        append("<trkseg>\n")
        track.points.forEach { point ->
            append("<trkpt lat=\"").append(point.latitude).append("\" lon=\"").append(point.longitude).append("\">\n")
            point.elevationMeters?.let { append("<ele>").append(it).append("</ele>\n") }
            point.timeMillis?.let { append("<time>").append(Instant.ofEpochMilli(it)).append("</time>\n") }
            append("</trkpt>\n")
        }
        append("</trkseg>\n")
        append("</trk>\n")
        append("</gpx>\n")
    }

    private fun escapeXml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
