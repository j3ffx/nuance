package io.github.j3ffx.nuance.screening

import io.github.j3ffx.nuance.core.cvd.Axis
import io.github.j3ffx.nuance.core.screening.Confidence
import io.github.j3ffx.nuance.core.screening.ScreeningResult

/** What is remembered of the last "Find my profile": "Deutan;64;Medium", or "none;0;High". */
data class ScreeningSummary(val axis: Axis?, val severity: Int, val confidence: Confidence) {
    companion object {
        fun encode(r: ScreeningResult) = "${r.axis?.name ?: "none"};${r.severity};${r.confidence.name}"

        /** Null for an empty or unreadable text. */
        fun decode(text: String): ScreeningSummary? {
            val parts = text.split(';')
            if (parts.size != 3) return null
            val axis = if (parts[0] == "none") null else Axis.entries.firstOrNull { it.name == parts[0] } ?: return null
            val severity = parts[1].toIntOrNull()?.takeIf { it in 0..100 } ?: return null
            val confidence = Confidence.entries.firstOrNull { it.name == parts[2] } ?: return null
            return ScreeningSummary(axis, severity, confidence)
        }
    }
}
