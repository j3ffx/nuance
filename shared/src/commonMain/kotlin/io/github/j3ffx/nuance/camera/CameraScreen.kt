package io.github.j3ffx.nuance.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.j3ffx.nuance.core.cvd.Axis
import io.github.j3ffx.nuance.core.modes.HuePreset
import io.github.j3ffx.nuance.core.modes.Mode
import io.github.j3ffx.nuance.render.ViewLayout
import io.github.j3ffx.nuance.resources.Res
import io.github.j3ffx.nuance.resources.axis_deutan
import io.github.j3ffx.nuance.resources.axis_protan
import io.github.j3ffx.nuance.resources.axis_tritan
import io.github.j3ffx.nuance.resources.compare_four
import io.github.j3ffx.nuance.resources.compare_two
import io.github.j3ffx.nuance.resources.hold_for_original
import io.github.j3ffx.nuance.resources.hue_blue
import io.github.j3ffx.nuance.resources.hue_green
import io.github.j3ffx.nuance.resources.hue_orange
import io.github.j3ffx.nuance.resources.hue_pink
import io.github.j3ffx.nuance.resources.hue_red
import io.github.j3ffx.nuance.resources.hue_violet
import io.github.j3ffx.nuance.resources.hue_yellow
import io.github.j3ffx.nuance.resources.intensity_value
import io.github.j3ffx.nuance.resources.mode_compare
import io.github.j3ffx.nuance.resources.mode_correct
import io.github.j3ffx.nuance.resources.mode_isolate
import io.github.j3ffx.nuance.resources.mode_simulate
import io.github.j3ffx.nuance.resources.severity_value
import io.github.j3ffx.nuance.resources.view_corrected
import io.github.j3ffx.nuance.resources.view_corrected_seen
import io.github.j3ffx.nuance.resources.view_isolated
import io.github.j3ffx.nuance.resources.view_original
import io.github.j3ffx.nuance.resources.view_real
import io.github.j3ffx.nuance.resources.view_real_seen
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The camera screen: the image in 1, 2 or 4 views with their labels, and the controls (mode,
 * profile, intensity…). A long press on the image shows the original as long as the finger stays.
 * J4 turns the controls into the final two-layer interface; this is the working version.
 */
@Composable
fun CameraScreen(controller: CameraController, camera: @Composable (Modifier) -> Unit, modifier: Modifier = Modifier) {
    val state = controller.state
    val holdDescription = stringResource(Res.string.hold_for_original)
    // The views fill the space above the controls, so none is hidden behind them.
    Column(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            CameraArea(controller, holdDescription, camera)
        }
        Controls(controller, Modifier)
    }
}

@Composable
private fun CameraArea(controller: CameraController, holdDescription: String, camera: @Composable (Modifier) -> Unit) {
    val state = controller.state
    Box(Modifier.fillMaxSize()) {
        camera(Modifier.fillMaxSize())
        // A transparent layer over the platform view takes the long press (the view would keep it).
        Box(
            Modifier.fillMaxSize()
                .semantics { contentDescription = holdDescription }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { controller.update { it.copy(showingOriginal = true) } },
                        onPress = {
                            tryAwaitRelease()
                            controller.update { it.copy(showingOriginal = false) }
                        }
                    )
                }
        )
        ViewLabels(state)
    }
}

@Composable
private fun ViewLabels(state: ViewState) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rects = ViewLayout.rects(state.views.size, maxWidth > maxHeight)
        val axis = axisName(state.settings.profile.axis)
        for ((kind, rect) in state.views.zip(rects)) {
            val text = when {
                state.showingOriginal -> stringResource(Res.string.view_original)

                state.views.size == 1 && kind == ViewKind.Corrected -> null

                // the main mode needs no label
                else -> labelFor(kind, axis, state)
            } ?: continue
            // At the bottom left of its view: clear of the status bar and the dev button.
            Box(
                Modifier.offset(maxWidth * rect.x, maxHeight * rect.y).size(
                    maxWidth * rect.width,
                    maxHeight * rect.height
                )
            ) {
                Text(
                    text,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .background(Color(0xB3000000), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun labelFor(kind: ViewKind, axis: String, state: ViewState): String = when (kind) {
    ViewKind.Real -> stringResource(Res.string.view_real)
    ViewKind.RealSeen -> stringResource(Res.string.view_real_seen, axis)
    ViewKind.Corrected -> stringResource(Res.string.view_corrected)
    ViewKind.CorrectedSeen -> stringResource(Res.string.view_corrected_seen, axis)
    ViewKind.Isolated -> stringResource(Res.string.view_isolated, hueName(nearestPreset(state.settings.isolateHue)))
}

@Composable
private fun Controls(controller: CameraController, modifier: Modifier) {
    val state = controller.state
    val s = state.settings
    Column(
        modifier
            .fillMaxWidth()
            .background(Color(0xCC000000))
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Choices(
            options = Mode.entries,
            selected = state.mode,
            label = { stringResource(modeName(it)) },
            onSelect = { m -> controller.update { it.copy(mode = m) } }
        )
        if (state.mode != Mode.Isolate) {
            Choices(
                options = Axis.entries,
                selected = s.profile.axis,
                label = { axisName(it) },
                onSelect = { a ->
                    controller.update {
                        it.copy(settings = it.settings.copy(profile = it.settings.profile.copy(axis = a)))
                    }
                }
            )
            Text(stringResource(Res.string.severity_value, percent(s.profile.severity)), color = Color.White)
            Slider(
                value = s.profile.severity.toFloat(),
                onValueChange = { v ->
                    controller.update {
                        it.copy(
                            settings = it.settings.copy(profile = it.settings.profile.copy(severity = v.toDouble()))
                        )
                    }
                }
            )
        }
        if (state.mode == Mode.Correct || state.mode == Mode.Compare) {
            Text(stringResource(Res.string.intensity_value, percent(s.intensity)), color = Color.White)
            Slider(
                value = s.intensity.toFloat(),
                onValueChange = { v ->
                    controller.update { it.copy(settings = it.settings.copy(intensity = v.toDouble())) }
                }
            )
        }
        if (state.mode == Mode.Isolate) {
            Choices(
                options = HuePreset.entries,
                selected = nearestPreset(s.isolateHue),
                label = { hueName(it) },
                onSelect = { p -> controller.update { it.copy(settings = it.settings.copy(isolateHue = p.hue)) } }
            )
        }
        if (state.mode == Mode.Compare) {
            Choices(
                options = listOf(4, 2),
                selected = state.compareCount,
                label = { stringResource(if (it == 4) Res.string.compare_four else Res.string.compare_two) },
                onSelect = { n -> controller.update { it.copy(compareCount = n) } }
            )
            if (state.compareCount == 2) {
                val axis = axisName(s.profile.axis)
                Choices(
                    options = listOf(ViewKind.Real, ViewKind.RealSeen, ViewKind.Corrected, ViewKind.CorrectedSeen),
                    selected = null,
                    isSelected = { it in state.compareSelection },
                    label = { labelFor(it, axis, state) },
                    onSelect = { k ->
                        // Two views at a time: picking a third drops the oldest.
                        controller.update {
                            val picked = if (k in
                                it.compareSelection
                            ) {
                                it.compareSelection
                            } else {
                                (it.compareSelection + k).takeLast(2)
                            }
                            it.copy(compareSelection = picked)
                        }
                    }
                )
            }
        }
    }
}

/** Chips; the selected one also shows a check mark, so the state isn't told by colour alone. */
@Composable
private fun <T> Choices(
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    isSelected: (T) -> Boolean = { it == selected }
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (option in options) {
            val on = isSelected(option)
            FilterChip(
                selected = on,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
                leadingIcon = if (on) {
                    { Text("✓", modifier = Modifier.size(18.dp)) }
                } else {
                    null
                }
            )
        }
    }
}

private fun percent(v: Double) = (v * 100).roundToInt()

private fun modeName(m: Mode): StringResource = when (m) {
    Mode.Correct -> Res.string.mode_correct
    Mode.Isolate -> Res.string.mode_isolate
    Mode.Simulate -> Res.string.mode_simulate
    Mode.Compare -> Res.string.mode_compare
}

@Composable
private fun axisName(a: Axis): String = stringResource(
    when (a) {
        Axis.Protan -> Res.string.axis_protan
        Axis.Deutan -> Res.string.axis_deutan
        Axis.Tritan -> Res.string.axis_tritan
    }
)

@Composable
private fun hueName(p: HuePreset): String = stringResource(
    when (p) {
        HuePreset.Red -> Res.string.hue_red
        HuePreset.Orange -> Res.string.hue_orange
        HuePreset.Yellow -> Res.string.hue_yellow
        HuePreset.Green -> Res.string.hue_green
        HuePreset.Blue -> Res.string.hue_blue
        HuePreset.Violet -> Res.string.hue_violet
        HuePreset.Pink -> Res.string.hue_pink
    }
)

private fun nearestPreset(hue: Double): HuePreset = HuePreset.entries.minBy {
    kotlin.math.abs(it.hue - hue).let { d -> if (d > 180) 360 - d else d }
}
