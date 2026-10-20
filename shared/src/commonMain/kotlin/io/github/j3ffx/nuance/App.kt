package io.github.j3ffx.nuance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.j3ffx.nuance.core.Lut3d
import io.github.j3ffx.nuance.core.color.Vec3
import io.github.j3ffx.nuance.core.cvd.Axis
import io.github.j3ffx.nuance.core.cvd.CvdProfile
import io.github.j3ffx.nuance.core.modes.ColorFunction
import io.github.j3ffx.nuance.core.modes.LinearFunction
import io.github.j3ffx.nuance.core.modes.Settings
import io.github.j3ffx.nuance.render.CameraRenderer
import io.github.j3ffx.nuance.render.RenderStats
import io.github.j3ffx.nuance.resources.Res
import io.github.j3ffx.nuance.resources.camera_permission_grant
import io.github.j3ffx.nuance.resources.camera_permission_rationale
import kotlin.time.measureTimedValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/**
 * The whole app, shared by Android and iOS. For now (J1): the camera through a test LUT, and the
 * dev tools to compare pipelines. [camera] draws the platform's camera view, once the camera
 * permission is granted ([onRequestCamera] asks for it).
 */
@Composable
fun App(
    renderer: CameraRenderer,
    devTools: Boolean,
    cameraGranted: Boolean,
    onRequestCamera: () -> Unit,
    /** Dev variant: a test LUT asked for by a script (its label), or null. */
    devLut: String? = null,
    camera: @Composable (Modifier) -> Unit
) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black, contentColor = Color.White) {
            Box(Modifier.fillMaxSize()) {
                if (!cameraGranted) {
                    CameraPermissionPrompt(onRequestCamera, Modifier.align(Alignment.Center))
                    return@Box
                }
                camera(Modifier.fillMaxSize())
                if (devTools) DevTools(renderer, devLut, Modifier.align(Alignment.TopStart))
            }
        }
    }
}

@Composable
private fun CameraPermissionPrompt(onRequestCamera: () -> Unit, modifier: Modifier) {
    Column(
        modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(Res.string.camera_permission_rationale), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onRequestCamera) { Text(stringResource(Res.string.camera_permission_grant)) }
    }
}

/** Dev variant: LUTs to check the pipeline and time the bake on the phone (labels are technical). */
private enum class TestLut(val label: String, val size: Int, val function: () -> LinearFunction) {
    Identity("id", Lut3d.DEFAULT_SIZE, { ColorFunction.identity }),
    Invert("inv", Lut3d.DEFAULT_SIZE, { { Vec3.One - it } }),
    SimulateDeutan("sim D", Lut3d.DEFAULT_SIZE, { ColorFunction.simulation(CvdProfile(Axis.Deutan, 1.0)) }),
    CorrectDeutan("corr D", Lut3d.DEFAULT_SIZE, { ColorFunction.correction(Settings(CvdProfile(Axis.Deutan, 1.0))) }),
    CorrectDeutanSmall("corr D 17", 17, { ColorFunction.correction(Settings(CvdProfile(Axis.Deutan, 1.0))) }),
    IsolateRed("iso R 49", 49, { ColorFunction.isolation(Settings(CvdProfile(Axis.Deutan, 1.0))) })
}

@Composable
private fun DevTools(renderer: CameraRenderer, requested: String?, modifier: Modifier) {
    val stats by renderer.stats.collectAsState()
    var lut by remember { mutableStateOf(TestLut.Identity) }
    LaunchedEffect(requested) { TestLut.entries.firstOrNull { it.label == requested }?.let { lut = it } }
    LaunchedEffect(lut) {
        val (table, took) = withContext(Dispatchers.Default) {
            measureTimedValue { Lut3d.bakeLinear(lut.size, lut.function()) }
        }
        renderer.setLut(table, took.inWholeMicroseconds / 1000f)
    }
    Column(
        modifier.safeDrawingPadding().padding(8.dp).background(Color(0xCC000000)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(stats.describe(), fontFamily = FontFamily.Monospace, color = Color.White)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (option in TestLut.entries) FilterChip(lut == option, { lut = option }, { Text(option.label) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (name in renderer.pipelines) {
                FilterChip(stats.pipeline == name, { renderer.selectPipeline(name) }, { Text(name) })
            }
        }
    }
}

/** Dev overlay text: technical, so English only, like logs. */
private fun RenderStats.describe(): String = buildString {
    appendLine("$pipeline  $resolution")
    appendLine("draw ${fps.fmt()} fps  camera ${cameraFps.fmt()} fps")
    appendLine("gpu ${gpuMs.fmt()} ms  capture→swap ${captureToSwapMs.fmt()} ms")
    appendLine("  capture→app ${captureToArrivalMs.fmt()} ms  app→swap ${arrivalToSwapMs.fmt()} ms")
    appendLine("vsync ${vsyncMs.fmt()} ms  lut bake ${lutBakeMs.fmt()} ms")
    append("thermal $thermal")
}

private fun Float?.fmt(): String = if (this == null) "–" else ((this * 10).toInt() / 10f).toString()
