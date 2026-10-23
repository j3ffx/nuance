package io.github.j3ffx.nuance.core.profile

import io.github.j3ffx.nuance.core.correction.CorrectionStrategy
import io.github.j3ffx.nuance.core.cvd.Axis
import io.github.j3ffx.nuance.core.cvd.CvdProfile
import io.github.j3ffx.nuance.core.modes.HuePreset
import io.github.j3ffx.nuance.core.modes.Mode
import io.github.j3ffx.nuance.core.modes.Settings
import kotlin.math.roundToInt

/**
 * A saved profile: who it is for (how much each axis is affected, usually one) and how they like
 * the camera (mode and settings). Kept on the phone only, and shareable as a short text
 * ([ProfileCodec]).
 */
data class Profile(
    /** Stable id; presets use "preset-…". */
    val id: String,
    /** Shown in lists; empty for presets, whose name the UI builds from the severities. */
    val name: String,
    /** Each axis 0 to 100, in steps of 1 (docs/ALGORITHMS.md, "Profils mixtes"). */
    val protan: Int,
    val deutan: Int,
    val tritan: Int,
    val mode: Mode = Mode.Correct,
    /** 0 to 100. */
    val intensity: Int = 100,
    val strategyId: String = CorrectionStrategy.Default.id,
    /** 0 to 100. */
    val contrast: Int = 0,
    val saturation: Int = 0,
    val isolateHue: Int = HuePreset.Red.hue.roundToInt(),
    /** Half-width of the kept hue range, in degrees. */
    val isolateWidth: Int = 30
) {
    /** A profile for one axis at [severity] (0 to 100). */
    constructor(
        id: String,
        name: String,
        axis: Axis,
        severity: Int,
        mode: Mode = Mode.Correct,
        intensity: Int = 100,
        strategyId: String = CorrectionStrategy.Default.id,
        contrast: Int = 0,
        saturation: Int = 0,
        isolateHue: Int = HuePreset.Red.hue.roundToInt(),
        isolateWidth: Int = 30
    ) : this(
        id,
        name,
        if (axis == Axis.Protan) severity else 0,
        if (axis == Axis.Deutan) severity else 0,
        if (axis == Axis.Tritan) severity else 0,
        mode,
        intensity,
        strategyId,
        contrast,
        saturation,
        isolateHue,
        isolateWidth
    )

    init {
        require(listOf(protan, deutan, tritan, intensity, contrast, saturation).all { it in 0..100 })
        require(isolateHue in 0..359 && isolateWidth in 5..90)
    }

    /** The severities as fractions. */
    val cvd: CvdProfile get() = CvdProfile(protan / 100.0, deutan / 100.0, tritan / 100.0)

    /** The main axis (the strongest; see [CvdProfile.axis]) and its severity, 0 to 100. */
    val axis: Axis get() = cvd.axis
    val severity: Int get() = severityOf(axis)

    fun severityOf(axis: Axis): Int = when (axis) {
        Axis.Protan -> protan
        Axis.Deutan -> deutan
        Axis.Tritan -> tritan
    }

    val isPreset: Boolean get() = id.startsWith(PRESET_PREFIX)

    fun toSettings() = Settings(
        profile = cvd,
        intensity = intensity / 100.0,
        strategyId = strategyId,
        contrast = contrast / 100.0,
        saturation = saturation / 100.0,
        isolateHue = isolateHue.toDouble(),
        isolateWidth = isolateWidth.toDouble()
    )

    /** This profile with the camera's current [settings] and [mode] (what "save" keeps). */
    fun with(settings: Settings, mode: Mode) = copy(
        protan = (settings.profile.protan * 100).roundToInt(),
        deutan = (settings.profile.deutan * 100).roundToInt(),
        tritan = (settings.profile.tritan * 100).roundToInt(),
        mode = mode,
        intensity = (settings.intensity * 100).roundToInt(),
        strategyId = settings.strategyId,
        contrast = (settings.contrast * 100).roundToInt(),
        saturation = (settings.saturation * 100).roundToInt(),
        isolateHue = (settings.isolateHue.roundToInt() % 360 + 360) % 360,
        isolateWidth = settings.isolateWidth.roundToInt().coerceIn(5, 90)
    )

    /**
     * True when [settings] differ from this profile's own, at the stored precision (whole
     * percents and degrees). The mode is not compared: switching modes changes nothing to save.
     */
    fun isModifiedBy(settings: Settings): Boolean = with(settings, mode) != this

    companion object {
        const val PRESET_PREFIX = "preset-"

        /** One preset per axis, medium (50 %) and total (100 %). */
        val presets: List<Profile> = Axis.entries.flatMap { axis ->
            listOf(50, 100).map { severity ->
                Profile("$PRESET_PREFIX${axis.name.lowercase()}-$severity", "", axis, severity)
            }
        }

        /** Used until the person picks a profile: deutan, the most common axis, at 60 %. */
        val Default = Profile("preset-default", "", Axis.Deutan, 60)

        /** Every profile that comes with the app, [Default] first, so the one in use is always listed. */
        val builtIns: List<Profile> = listOf(Default) + presets
    }
}
