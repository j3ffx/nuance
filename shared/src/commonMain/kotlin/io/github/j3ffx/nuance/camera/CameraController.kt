package io.github.j3ffx.nuance.camera

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import io.github.j3ffx.nuance.core.Lut3d
import io.github.j3ffx.nuance.core.cvd.Axis
import io.github.j3ffx.nuance.core.cvd.CvdProfile
import io.github.j3ffx.nuance.core.modes.ColorFunction
import io.github.j3ffx.nuance.core.modes.LinearFunction
import io.github.j3ffx.nuance.core.modes.Mode
import io.github.j3ffx.nuance.core.modes.Settings
import io.github.j3ffx.nuance.render.CameraRenderer
import kotlin.time.measureTimedValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What a view shows; the UI turns it into a text label (never a colour alone). */
enum class ViewKind { Real, RealSeen, Corrected, CorrectedSeen, Isolated }

/** Everything the camera screen shows, as one immutable value. */
data class ViewState(
    val mode: Mode = Mode.Correct,
    val settings: Settings = Settings(CvdProfile(Axis.Deutan, 0.8)),
    /** Comparer: 4 views, or the 2 picked in [compareSelection]. */
    val compareCount: Int = 4,
    val compareSelection: List<ViewKind> = listOf(ViewKind.RealSeen, ViewKind.CorrectedSeen),
    /** While a finger rests on the image: the original, full screen. */
    val showingOriginal: Boolean = false
) {
    /** The views on screen, in [io.github.j3ffx.nuance.render.ViewLayout] order. */
    val views: List<ViewKind>
        get() = when {
            showingOriginal -> listOf(ViewKind.Real)
            mode == Mode.Correct -> listOf(ViewKind.Corrected)
            mode == Mode.Isolate -> listOf(ViewKind.Isolated)
            mode == Mode.Simulate -> listOf(ViewKind.RealSeen)
            compareCount == 2 -> compareSelection
            else -> listOf(ViewKind.Real, ViewKind.RealSeen, ViewKind.Corrected, ViewKind.CorrectedSeen)
        }
}

/**
 * Holds the camera screen's state and keeps the renderer's LUTs in step with it. Each change is
 * baked twice: at 17³ at once (a few ms, so sliders follow the finger), then at full size once the
 * state has stayed put for a moment. A newer change cancels an older bake.
 */
class CameraController(private val renderer: CameraRenderer, scope: CoroutineScope) {
    var state by mutableStateOf(ViewState())
        private set

    init {
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

        fun functionFor(kind: ViewKind, s: Settings): LinearFunction {
            val views = ColorFunction.comparer(s)
            return when (kind) {
                ViewKind.Real -> views[0]
                ViewKind.RealSeen -> views[1]
                ViewKind.Corrected -> views[2]
                ViewKind.CorrectedSeen -> views[3]
                ViewKind.Isolated -> ColorFunction.isolation(s)
            }
        }
    }
}
