package com.uprunner.core.gpx

import com.uprunner.core.model.GpxPoint
import com.uprunner.core.model.GpxTrack
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.format.DateTimeParseException
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Minimal GPX 1.1 track reader — only what the Plan tab needs (spec §3 Tab 2): a track name
 * and its ordered trackpoints (lat/lon/elevation/time). Uses javax.xml.parsers, which is
 * available on both the JVM and Android (API 1+), so this stays in :core and is testable
 * without the Android SDK. Waypoints/routes (<wpt>/<rte>) are intentionally not parsed —
 * only <trk>/<trkseg>/<trkpt>.
 */
object GpxParser {

    fun parse(gpxXml: String): GpxTrack = parse(ByteArrayInputStream(gpxXml.toByteArray(StandardCharsets.UTF_8)))

    fun parse(input: InputStream): GpxTrack {
        val factory = DocumentBuilderFactory.newInstance()
        val document = factory.newDocumentBuilder().parse(input)
        document.documentElement.normalize()

        val trkElement = document.getElementsByTagName("trk").item(0) as? Element
            ?: throw IllegalArgumentException("GPX file contains no <trk> element")

        val name = trkElement.getElementsByTagName("name").item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

        val trkptNodes = trkElement.getElementsByTagName("trkpt")
        val points = buildList {
            for (i in 0 until trkptNodes.length) {
                val trkpt = trkptNodes.item(i) as Element
                val lat = trkpt.getAttribute("lat").toDoubleOrNull()
                val lon = trkpt.getAttribute("lon").toDoubleOrNull()
                if (lat == null || lon == null) continue

                val elevation = trkpt.getElementsByTagName("ele").item(0)?.textContent?.trim()?.toDoubleOrNull()
                val time = trkpt.getElementsByTagName("time").item(0)?.textContent?.trim()?.let(::parseIsoInstantMillis)

                add(GpxPoint(latitude = lat, longitude = lon, elevationMeters = elevation, timeMillis = time))
            }
        }

        return GpxTrack(name = name, points = points)
    }

    private fun parseIsoInstantMillis(text: String): Long? = try {
        Instant.parse(text).toEpochMilli()
    } catch (e: DateTimeParseException) {
        null
    }
}
