package com.uprunner.core.tts

/**
 * Piper TTS parses punctuation strictly (spec §5) — any string headed for speech synthesis
 * must be run through [format] first. Rule order matters: relative-time must be resolved
 * before the generic numeric-dash rule, otherwise "-15s" would be mangled by the dash rule
 * first.
 */
object TtsFormatter {

    private val PACE_REGEX = Regex("""(\d{1,2}):(\d{2})\s*(?:/|per)\s*km""", RegexOption.IGNORE_CASE)
    private val DECIMAL_KM_REGEX = Regex("""(\d+)\.(\d)\d*\s*km""", RegexOption.IGNORE_CASE)
    private val RELATIVE_TIME_REGEX = Regex("""([+-])(\d+)\s*s\b""")
    private val NUMERIC_DASH_REGEX = Regex("""(\d)-(\d)""")

    fun format(text: String): String {
        var result = text
        result = PACE_REGEX.replace(result) { match ->
            "${match.groupValues[1]} ${match.groupValues[2]} per K"
        }
        result = DECIMAL_KM_REGEX.replace(result) { match ->
            "${match.groupValues[1]} point ${match.groupValues[2]} kilometers"
        }
        result = RELATIVE_TIME_REGEX.replace(result) { match ->
            val sign = match.groupValues[1]
            val amount = match.groupValues[2]
            if (sign == "+") "$amount seconds up" else "$amount seconds down"
        }
        result = NUMERIC_DASH_REGEX.replace(result) { match ->
            "${match.groupValues[1]} - ${match.groupValues[2]}"
        }
        return result
    }
}
