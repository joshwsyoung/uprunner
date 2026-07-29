package com.uprunner.core.routing

/**
 * Serializes a route's maneuvers to a single plain string for storage in `RouteEntity` — same
 * "no JSON library in :core" reasoning as [ValhallaRouting]'s own response parsing. Uses the
 * ASCII record/unit separator control characters as delimiters rather than a printable
 * character (comma, pipe, …) so an instruction string containing that character can't corrupt
 * the encoding — Valhalla's plain-English instructions won't contain either separator.
 */
object ManeuverCodec {

    private const val RECORD_SEPARATOR = "\u001E"
    private const val FIELD_SEPARATOR = "\u001F"

    fun encode(maneuvers: List<ValhallaRouting.Maneuver>): String =
        maneuvers.joinToString(RECORD_SEPARATOR) { "${it.beginPointIndex}$FIELD_SEPARATOR${it.instruction}" }

    fun decode(encoded: String): List<ValhallaRouting.Maneuver> {
        if (encoded.isBlank()) return emptyList()
        return encoded.split(RECORD_SEPARATOR).mapNotNull { record ->
            val parts = record.split(FIELD_SEPARATOR, limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val beginPointIndex = parts[0].toIntOrNull() ?: return@mapNotNull null
            ValhallaRouting.Maneuver(instruction = parts[1], beginPointIndex = beginPointIndex)
        }
    }
}
