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

    suspend fun routePedestrian(waypoints: List<Pair<Double, Double>>): Result<List<Pair<Double, Double>>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val requestJson = ValhallaRouting.buildRouteRequestJson(waypoints)
                val connection = (URL(ROUTE_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
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

                check(responseCode in 200..299) { "Routing request failed ($responseCode): $responseBody" }

                val shapes = ValhallaRouting.extractLegShapes(responseBody)
                check(shapes.isNotEmpty()) { "No route found" }
                ValhallaRouting.decodeFullRoute(shapes)
            }
        }
}
