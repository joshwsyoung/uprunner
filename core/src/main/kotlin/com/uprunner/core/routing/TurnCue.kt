package com.uprunner.core.routing

/**
 * Turns Valhalla's verbose, compass/street-name-laden instruction ("Turn left onto Main
 * Street.") into a short cue for the Navigation Card. Valhalla's pedestrian instructions are
 * already phrased relative to the walker's direction of travel (never "head north" style
 * absolute compass directions), so a keyword scan over its own text is enough — no bearing/
 * heading math needed here.
 */
object TurnCue {

    fun shortText(instruction: String): String {
        val lower = instruction.lowercase()
        return when {
            lower.contains("destination") || lower.contains("arrive") -> "Arrive at destination"
            lower.contains("u-turn") || lower.contains("uturn") -> "Make a U-turn"
            lower.contains("sharp left") -> "Sharp left"
            lower.contains("sharp right") -> "Sharp right"
            lower.contains("slight left") -> "Bear left"
            lower.contains("slight right") -> "Bear right"
            lower.contains("turn left") -> "Turn left"
            lower.contains("turn right") -> "Turn right"
            lower.contains("left") -> "Turn left"
            lower.contains("right") -> "Turn right"
            else -> "Continue straight"
        }
    }
}
