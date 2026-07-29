package com.uprunner.app.ui.plan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Free-text place/area search via OpenStreetMap's Nominatim (https://nominatim.openstreetmap.org)
 * — no API key, but its usage policy requires a descriptive User-Agent identifying the app and
 * caps usage at ~1 request/second, so this is only ever called on an explicit user search, never
 * as-you-type. Response parsing is a minimal regex over the first result's lat/lon, not a
 * general JSON parser — same reasoning as ValhallaRouting's response parsing in :core.
 */
object GeocodingClient {

    private const val SEARCH_URL = "https://nominatim.openstreetmap.org/search"
    private const val USER_AGENT = "uprunner-app/0.1 (offline running coach app; contact: none)"

    private val LAT_REGEX = Regex("\"lat\"\\s*:\\s*\"([-0-9.]+)\"")
    private val LON_REGEX = Regex("\"lon\"\\s*:\\s*\"([-0-9.]+)\"")
    private val DISPLAY_NAME_REGEX = Regex("\"display_name\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")

    data class PlaceResult(val latitude: Double, val longitude: Double, val displayName: String)

    suspend fun searchFirstResult(query: String): Result<PlaceResult> = withContext(Dispatchers.IO) {
        runCatching {
            val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            val url = URL("$SEARCH_URL?format=json&limit=1&q=$encodedQuery")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = 15_000
                readTimeout = 15_000
            }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            connection.disconnect()

            check(responseCode in 200..299) { "Search request failed ($responseCode)" }

            val lat = LAT_REGEX.find(body)?.groupValues?.get(1)?.toDoubleOrNull()
            val lon = LON_REGEX.find(body)?.groupValues?.get(1)?.toDoubleOrNull()
            val name = DISPLAY_NAME_REGEX.find(body)?.groupValues?.get(1).orEmpty()
            checkNotNull(lat) { "No results found" }
            checkNotNull(lon) { "No results found" }

            PlaceResult(lat, lon, name)
        }
    }
}
