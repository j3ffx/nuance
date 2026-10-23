package io.github.j3ffx.nuance.core.profile

import io.github.j3ffx.nuance.core.correction.CorrectionStrategy
import io.github.j3ffx.nuance.core.cvd.Axis
import io.github.j3ffx.nuance.core.cvd.CvdProfile
import io.github.j3ffx.nuance.core.modes.HuePreset
import io.github.j3ffx.nuance.core.modes.Mode
import io.github.j3ffx.nuance.core.modes.Settings
import kotlin.math.roundToInt

/**
 * A saved profile: who it is for (axis and severity) and how they like the camera (mode and
 * settings). Kept on the phone only, and shareable as a short text ([ProfileCodec]).
 */
data class Profile(
    /** Stable id; presets use "preset-…". */
    val id: String,
    /** Shown in lists; empty for presets, whose name the UI builds from [axis] and [severity]. */
    val name: String,
    val axis: Axis,
    /** 0 to 100, in steps of 1. */
    val severity: Int,
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
    init {
        require(severity in 0..100 && intensity in 0..100 && contrast in 0..100 && saturation in 0..100)
        require(isolateHue in 0..359 && isolateWidth in 5..90)
    }

    val isPreset: Boolean get() = id.startsWith(PRESET_PREFIX)

    fun toSettings() = Settings(
        profile = CvdProfile(axis, severity / 100.0),
        intensity = intensity / 100.0,
        strategyId = strategyId,
        contrast = contrast / 100.0,
        saturation = saturation / 100.0,
        isolateHue = isolateHue.toDouble(),
        isolateWidth = isolateWidth.toDouble()
    )

    /** This profile with the camera's current [settings] and [mode] (what "save" keeps). */
    fun with(settings: Settings, mode: Mode) = copy(
        axis = settings.profile.axis,
        severity = (settings.profile.severity * 100).roundToInt(),
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
