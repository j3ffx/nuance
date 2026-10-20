package io.github.j3ffx.nuance.core.cvd

/** Which cone type is affected. */
enum class Axis {
    /** L cones (long wavelengths, "red"): protanomaly, protanopia. */
    Protan,

    /** M cones (medium wavelengths, "green"): deuteranomaly, deuteranopia. */
    Deutan,

    /** S cones (short wavelengths, "blue"): tritanomaly, tritanopia. */
    Tritan
    ;

    /** Index of the affected cone in LMS order. */
    val cone: Int get() = ordinal
}

/** A colour vision profile: an axis and a severity, 0 (typical vision) to 1 (the total form). */
data class CvdProfile(val axis: Axis, val severity: Double) {
    init {
        require(severity in 0.0..1.0) { "severity is 0..1, got $severity" }
    }
}
