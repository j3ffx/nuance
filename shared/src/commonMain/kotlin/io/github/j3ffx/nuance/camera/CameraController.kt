package io.github.j3ffx.nuance.camera

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import io.github.j3ffx.nuance.core.Lut3d
import io.github.j3ffx.nuance.core.Rgb
import io.github.j3ffx.nuance.core.correction.CorrectionMetrics
import io.github.j3ffx.nuance.core.cvd.Axis
import io.github.j3ffx.nuance.core.modes.ColorFunction
import io.github.j3ffx.nuance.core.modes.LinearFunction
import io.github.j3ffx.nuance.core.modes.Mode
import io.github.j3ffx.nuance.core.modes.Settings
import io.github.j3ffx.nuance.core.profile.Profile
import io.github.j3ffx.nuance.render.CameraRenderer
import kotlin.time.measureTimedValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What a view shows; the UI turns it into a text label (never a colour alone). */
enum class ViewKind {
    Real,
    RealSeen,
    Corrected,
    CorrectedSeen,
    Isolated,

    /** Dev variant: the protan or deutan correction (whatever the profile's axis), seen with the profile. */
    ProtanCorrectedSeen,
    DeutanCorrectedSeen
}

/**
 * The pipette's target, as fractions of the image area from its top left, and the colour read
 * there (null until the first reading).
 */
data class PipetteState(val x: Float, val y: Float, val color: Rgb? = null)

/** Everything the camera screen shows, as one immutable value. */
data class ViewState(
    /** The profile the settings came from; they may have been changed since (see [Profile.with]). */
    val profileId: String = Profile.Default.id,
    val mode: Mode = Mode.Correct,
    val settings: Settings = Profile.Default.toSettings(),
    /** Comparer: 4 views, or the 2 picked in [compareSelection]. */
    val compareCount: Int = 4,
    val compareSelection: List<ViewKind> = listOf(ViewKind.RealSeen, ViewKind.CorrectedSeen),
    /** While a finger rests on the image: the original, full screen. */
    val showingOriginal: Boolean = false,
    /** Dev variant: Compare shows the protan and the deutan corrections, both seen with the profile. */
    val crossAxis: Boolean = false
) {
    /** The views on screen, in [io.github.j3ffx.nuance.render.ViewLayout] order. */
    val views: List<ViewKind>
        get() = when {
            showingOriginal -> listOf(ViewKind.Real)

            mode == Mode.Correct -> listOf(ViewKind.Corrected)

            mode == Mode.Isolate -> listOf(ViewKind.Isolated)

            mode == Mode.Simulate -> listOf(ViewKind.RealSeen)

            crossAxis -> listOf(
                ViewKind.Real,
                ViewKind.RealSeen,
                ViewKind.ProtanCorrectedSeen,
                ViewKind.DeutanCorrectedSeen
            )

            compareCount == 2 -> compareSelection

            else -> listOf(ViewKind.Real, ViewKind.RealSeen, ViewKind.Corrected, ViewKind.CorrectedSeen)
        }
}

/**
 * Holds the camera screen's state and keeps the renderer's LUTs in step with it. Each change is
 * baked twice: at 17³ at once (a few ms, so sliders follow the finger), then at full size once the
 * state has stayed put for a moment. A newer change cancels an older bake.
 */
class CameraController(
    private val renderer: CameraRenderer,
    scope: CoroutineScope,
    initial: Profile = Profile.Default
) {
    var state by mutableStateOf(stateFor(initial))
        private set

    /** Shown while the person points at something; the colour follows the image as it moves. */
    var pipette by mutableStateOf<PipetteState?>(null)
        private set

    /** Points the pipette at ([x], [y]); the colour is read right away, then every [PIPETTE_MS]. */
    fun pick(x: Float, y: Float) {
        pipette = PipetteState(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f), pipette?.color)
    }

    fun closePipette() {
        pipette = null
    }

    /** Switches to [profile]'s settings and mode. */
    fun applyProfile(profile: Profile) {
        state = stateFor(profile).copy(compareCount = state.compareCount, compareSelection = state.compareSelection)
    }

    /** Back to [profile]'s saved settings, keeping the current mode. */
    fun resetTo(profile: Profile) {
        state = state.copy(settings = profile.toSettings())
    }

    private fun stateFor(p: Profile) = ViewState(profileId = p.id, mode = p.mode, settings = p.toSettings())

    init {
        scope.launch { followPipette() }
        scope.launch {
            snapshotFlow { state }.collectLatest { s ->
                if (s.showingOriginal) {
                    renderer.setViews(listOf(identity))
                    return@collectLatest
                }
                val functions = s.views.map { functionFor(it, s.settings) }
                bake(functions, QUICK_SIZE)
                delay(SETTLE_MS)
                bake(functions, if (s.views == listOf(ViewKind.Isolated)) ISOLATION_SIZE else Lut3d.DEFAULT_SIZE)
            }
        }
    }

    private suspend fun followPipette() {
        snapshotFlow { pipette?.let { it.x to it.y } }.collectLatest { point ->
            point ?: return@collectLatest
            while (true) {
                val color = renderer.sampleColor(point.first, point.second)
                pipette = pipette?.copy(color = color ?: pipette?.color)
                delay(PIPETTE_MS)
            }
        }
    }

    fun update(transform: (ViewState) -> ViewState) {
        state = transform(state)
    }

    private suspend fun bake(functions: List<LinearFunction>, size: Int) {
        val (luts, took) = withContext(Dispatchers.Default) {
            measureTimedValue { functions.map { Lut3d.bakeLinear(size, it) } }
        }
        renderer.setViews(luts, took.inWholeMicroseconds / 1000f)
    }

    private val identity = Lut3d.bakeLinear(2, ColorFunction.identity)

    companion object {
        const val QUICK_SIZE = 17

        /** Hue isolation has sharp edges: 49³ keeps them right (docs/ALGORITHMS.md, "LUT size"). */
        const val ISOLATION_SIZE = 49
        const val SETTLE_MS = 150L

        /** How often the pipette reads its point again: the scene moves under it. */
        const val PIPETTE_MS = 300L

        fun functionFor(kind: ViewKind, s: Settings): LinearFunction {
            val views = ColorFunction.comparer(s)
            return when (kind) {
                ViewKind.Real -> views[0]
                ViewKind.RealSeen -> views[1]
                ViewKind.Corrected -> views[2]
                ViewKind.CorrectedSeen -> views[3]
                ViewKind.Isolated -> ColorFunction.isolation(s)
                ViewKind.ProtanCorrectedSeen -> correctedAs(Axis.Protan, s)
                ViewKind.DeutanCorrectedSeen -> correctedAs(Axis.Deutan, s)
            }
        }

        /** The correction made for [axis] at the profile's severity, seen with the profile itself. */
        private fun correctedAs(axis: Axis, s: Settings): LinearFunction {
            val simulate = ColorFunction.simulation(s.profile)
            val correct = ColorFunction.correction(s.copy(profile = s.profile.copy(axis = axis)))
            return { simulate(CorrectionMetrics.clampLinear(correct(it))) }
        }
    }
}
