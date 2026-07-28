package com.uprunner.core.pace

import kotlin.math.roundToInt

/**
 * Active Run Mode B's "Relative Pace Difference" (spec §3 Tab 1) — positive means the
 * runner is slower than target ("up", per the TTS formatter's §5 rule), negative means
 * faster ("down").
 */
object RelativePace {
    fun diffSecPerKm(actualPaceSecPerKm: Double, targetPaceSecPerKm: Double): Int =
        (actualPaceSecPerKm - targetPaceSecPerKm).roundToInt()
}
