package com.uprunner.app.ui.plan

import com.uprunner.core.routing.ValhallaRouting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Talks to the free, no-API-key FOSSGIS-hosted Valhalla instance for pedestrian routing
 * (https://valhalla1.openstreetmap.de) — plain HttpURLConnection rather than adding
 * OkHttp/Retrofit for what's currently a single POST call.
 */
object RoutingClient {

    private const val ROUTE_URL = "https://valhalla1.openstreetmap.de/route"

    // Public OSM-adjacent services (Nominatim, and FOSSGIS's Valhalla mirror) reject or rate-limit
    // requests carrying Java's default User-Agent as abuse mitigation, so this identifies the app
    // the same way GeocodingClient's Nominatim calls already do.
    private const val USER_AGENT = "uprunner-app/0.1 (offline running coach app; contact: none)"

    /** Carries the HTTP status so callers can tell "no route exists" (400, Valhalla's own
     *  no-path error) apart from the request being blocked or rate-limited (403/429) or the
     *  service being down (5xx) — those need very different user-facing messages. */
    class RoutingHttpException(val statusCode: Int, message: String) : Exception(message)

    suspend fun routePedestrian(waypoints: List<Pair<Double, Double>>): Result<ValhallaRouting.RoutedPath> =
        withContext(Dispatchers.IO) {
            runCatching {
                val requestJson = ValhallaRouting.buildRouteRequestJson(waypoints)
                val connection = (URL(ROUTE_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("User-Agent", USER_AGENT)
                    connectTimeout = 15_000
                    readTimeout = 15_000
                }

                connection.outputStream.use { output ->
                    OutputStreamWriter(output, StandardCharsets.UTF_8).use { it.write(requestJson) }
                }

                val responseCode = connection.responseCode
                val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                val responseBody = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
                connection.disconnect()

                if (responseCode !in 200..299) {
                    throw RoutingHttpException(responseCode, "Routing request failed ($responseCode): $responseBody")
                }

                val routed = ValhallaRouting.decodeFullRouteWithManeuvers(responseBody)
                if (routed.points.isEmpty()) throw RoutingHttpException(responseCode, "No route found: $responseBody")
                routed
            }
        }
}
