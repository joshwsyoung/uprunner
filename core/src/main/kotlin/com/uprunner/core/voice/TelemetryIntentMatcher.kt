package com.uprunner.core.voice

/** Tier-1 (instant) voice query classification per spec §4.2. `null` means route to the LLM tier. */
sealed interface TelemetryIntent {
    data object Pace : TelemetryIntent
    data object Distance : TelemetryIntent
    data object Time : TelemetryIntent
}

object TelemetryIntentMatcher {

    private val PACE_REGEX = Regex("""\bpace\b""", RegexOption.IGNORE_CASE)
    private val DISTANCE_REGEX = Regex("""\b(distance|how far)\b""", RegexOption.IGNORE_CASE)
    private val TIME_REGEX = Regex("""\b(time|how long)\b""", RegexOption.IGNORE_CASE)

    fun match(transcript: String): TelemetryIntent? = when {
        PACE_REGEX.containsMatchIn(transcript) -> TelemetryIntent.Pace
        DISTANCE_REGEX.containsMatchIn(transcript) -> TelemetryIntent.Distance
        TIME_REGEX.containsMatchIn(transcript) -> TelemetryIntent.Time
        else -> null
    }
}
