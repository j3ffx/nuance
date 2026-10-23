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

/**
 * A colour vision profile: how much each cone type is affected, each 0 (typical) to 1 (the total
 * form). Usually one axis only; any mix is allowed (docs/ALGORITHMS.md, "Profils mixtes"), though
 * no validated model exists for more than one.
 */
data class CvdProfile(val protan: Double = 0.0, val deutan: Double = 0.0, val tritan: Double = 0.0) {
    /** One axis at [severity], the others at 0. */
    constructor(axis: Axis, severity: Double) : this(
        if (axis == Axis.Protan) severity else 0.0,
        if (axis == Axis.Deutan) severity else 0.0,
        if (axis == Axis.Tritan) severity else 0.0
    )

    init {
        for (s in listOf(protan, deutan, tritan)) require(s in 0.0..1.0) { "severity is 0..1, got $s" }
    }

    fun severityOf(axis: Axis): Double = when (axis) {
        Axis.Protan -> protan
        Axis.Deutan -> deutan
        Axis.Tritan -> tritan
    }

    /** This profile with [axis] at [severity], the other axes unchanged. */
    fun with(axis: Axis, severity: Double) = when (axis) {
        Axis.Protan -> copy(protan = severity)
        Axis.Deutan -> copy(deutan = severity)
        Axis.Tritan -> copy(tritan = severity)
    }

    /** The affected axes, strongest first (ties in [Axis] order). */
    val components: List<Axis> get() = Axis.entries.filter { severityOf(it) > 0 }.sortedByDescending(::severityOf)

    /**
     * The main axis, the strongest (deutan, the most common, when none is affected): what texts,
     * the built-in test and the single-axis tools go by.
     */
    val axis: Axis get() = components.firstOrNull() ?: Axis.Deutan

    /** The main axis's severity. */
    val severity: Double get() = severityOf(axis)

    val isMixed: Boolean get() = components.size > 1

    /**
     * Every axis scaled so the strongest becomes [severity]: one slider for the whole profile,
     * which keeps a mix in proportion. A profile with no axis gets its main one.
     */
    fun scaledTo(severity: Double): CvdProfile {
        val max = this.severity
        if (max == 0.0) return with(axis, severity)
        val k = severity / max
        return CvdProfile(
            (protan * k).coerceIn(0.0, 1.0),
            (deutan * k).coerceIn(0.0, 1.0),
            (tritan * k).coerceIn(0.0, 1.0)
        )
    }
}
