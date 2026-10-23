package io.github.j3ffx.nuance.camera

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.j3ffx.nuance.core.Rgb
import io.github.j3ffx.nuance.core.color.Oklab
import io.github.j3ffx.nuance.core.color.Srgb
import io.github.j3ffx.nuance.core.cvd.Simulation
import io.github.j3ffx.nuance.core.modes.Mode
import io.github.j3ffx.nuance.core.naming.ColorNaming
import io.github.j3ffx.nuance.resources.Res
import io.github.j3ffx.nuance.resources.pipette_close
import io.github.j3ffx.nuance.resources.pipette_isolate
import io.github.j3ffx.nuance.resources.pipette_reading
import io.github.j3ffx.nuance.resources.pipette_real
import io.github.j3ffx.nuance.resources.pipette_seen
import io.github.j3ffx.nuance.resources.pipette_seen_same
import io.github.j3ffx.nuance.ui.NuanceColors
import io.github.j3ffx.nuance.ui.colorName
import org.jetbrains.compose.resources.stringResource

/** The pipette's target: a ring with a gap at its centre, white over black so any scene shows it. */
@Composable
internal fun PipetteMarker(pipette: PipetteState) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Canvas(
            Modifier.offset(maxWidth * pipette.x - 24.dp, maxHeight * pipette.y - 24.dp).size(48.dp)
        ) {
            val c = Offset(size.width / 2, size.height / 2)
            val r = 14.dp.toPx()
            drawCircle(Color.Black, r, c, style = Stroke(5.dp.toPx()))
            drawCircle(Color.White, r, c, style = Stroke(2.5.dp.toPx()))
            for ((dx, dy) in listOf(1f to 0f, -1f to 0f, 0f to 1f, 0f to -1f)) {
                val from = c + Offset(dx, dy) * (r + 3.dp.toPx())
                val to = c + Offset(dx, dy) * (r + 10.dp.toPx())
                drawLine(Color.Black, from, to, 5.dp.toPx())
                drawLine(Color.White, from, to, 2.5.dp.toPx())
            }
        }
    }
}

/**
 * What the pipette reads: the real colour's name, a swatch and its code, then how the profile
 * probably sees it (or that it looks the same). In "Isolate", it can make this colour the one to keep.
 */
@Composable
internal fun PipetteCard(controller: CameraController, modifier: Modifier) {
    val pipette = controller.pipette ?: return
    val state = controller.state
    val profile = state.settings.profile
    Surface(
        color = NuanceColors.scrim,
        contentColor = NuanceColors.onScrim,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.widthIn(max = 420.dp).semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.Top) {
            val color = pipette.color
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (color == null) {
                    Text(stringResource(Res.string.pipette_reading), style = MaterialTheme.typography.bodyLarge)
                } else {
                    // The colour in front of the camera, before any filter: said so, since in
                    // "Correct" the screen shows it changed.
                    val name = colorName(ColorNaming.name(color))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Swatch(color, 40)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(
                                stringResource(Res.string.pipette_real, name),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(ColorNaming.hex(color), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (profile.severity > 0) {
                        val seen = Simulation.rgb(profile)(color)
                        val seenName = colorName(ColorNaming.name(seen))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Swatch(seen, 24)
                            Text(
                                if (seenName == name) {
                                    stringResource(Res.string.pipette_seen_same)
                                } else {
                                    stringResource(
                                        Res.string.pipette_seen,
                                        seenName.replaceFirstChar {
                                            it.lowercaseChar()
                                        }
                                    )
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                    if (state.mode == Mode.Isolate && isChromatic(color)) {
                        OutlinedButton(
                            onClick = {
                                val hue = Oklab.toLch(Oklab.fromLinear(Srgb.toLinear(color))).z
                                controller.update { it.copy(settings = it.settings.copy(isolateHue = hue)) }
                            },
                            border = BorderStroke(1.dp, NuanceColors.onScrim),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(stringResource(Res.string.pipette_isolate), color = NuanceColors.onScrim)
                        }
                    }
                }
            }
            IconButton(onClick = controller::closePipette) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.pipette_close))
            }
        }
    }
}

@Composable
private fun Swatch(c: Rgb, sizeDp: Int) {
    Box(
        Modifier.size(sizeDp.dp)
            .border(1.5.dp, NuanceColors.onScrim, RoundedCornerShape(6.dp))
            .padding(1.5.dp)
            .background(Color(c.r, c.g, c.b), RoundedCornerShape(5.dp))
    )
}

/** Greys have no hue worth isolating (the same threshold as hue isolation's grey fade). */
private fun isChromatic(c: Rgb) = Oklab.toLch(Oklab.fromLinear(Srgb.toLinear(c))).y > 0.04
