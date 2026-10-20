package io.github.j3ffx.nuance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import io.github.j3ffx.nuance.core.Rgb
import io.github.j3ffx.nuance.core.Transforms
import io.github.j3ffx.nuance.render.CameraRenderer
import io.github.j3ffx.nuance.render.RenderStats
import io.github.j3ffx.nuance.resources.Res
import io.github.j3ffx.nuance.resources.camera_permission_grant
import io.github.j3ffx.nuance.resources.camera_permission_rationale
import io.github.j3ffx.nuance.resources.lut_identity
import io.github.j3ffx.nuance.resources.lut_invert
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
                if (devTools) DevTools(renderer, Modifier.align(Alignment.TopStart))
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

private enum class TestLut(val transform: (Rgb) -> Rgb) { Identity(Transforms.Identity), Invert(Transforms.Invert) }

@Composable
private fun DevTools(renderer: CameraRenderer, modifier: Modifier) {
    val stats by renderer.stats.collectAsState()
    var lut by remember { mutableStateOf(TestLut.Identity) }
    LaunchedEffect(lut) {
        val (table, took) = withContext(Dispatchers.Default) {
            measureTimedValue { Lut3d.bake(Lut3d.DEFAULT_SIZE, lut.transform) }
        }
        renderer.setLut(table, took.inWholeMicroseconds / 1000f)
    }
    Column(
        modifier.safeDrawingPadding().padding(8.dp).background(Color(0xCC000000)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(stats.describe(), fontFamily = FontFamily.Monospace, color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(lut == TestLut.Identity, {
                lut = TestLut.Identity
            }, { Text(stringResource(Res.string.lut_identity)) })
            FilterChip(lut == TestLut.Invert, { lut = TestLut.Invert }, { Text(stringResource(Res.string.lut_invert)) })
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
