package io.github.j3ffx.nuance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.j3ffx.nuance.camera.CameraController
import io.github.j3ffx.nuance.camera.CameraScreen
import io.github.j3ffx.nuance.core.correction.CorrectionStrategy
import io.github.j3ffx.nuance.core.modes.Mode
import io.github.j3ffx.nuance.render.CameraRenderer
import io.github.j3ffx.nuance.render.RenderStats
import io.github.j3ffx.nuance.resources.Res
import io.github.j3ffx.nuance.resources.camera_permission_grant
import io.github.j3ffx.nuance.resources.camera_permission_rationale
import org.jetbrains.compose.resources.stringResource

/** A command from a dev script (Android intent extras), applied once. */
data class DevCommand(val mode: String? = null, val compare: Int? = null)

/**
 * The whole app, shared by Android and iOS. [camera] draws the platform's camera view, once the
 * camera permission is granted ([onRequestCamera] asks for it).
 */
@Composable
fun App(
    renderer: CameraRenderer,
    devTools: Boolean,
    cameraGranted: Boolean,
    onRequestCamera: () -> Unit,
    devCommand: DevCommand? = null,
    camera: @Composable (Modifier) -> Unit
) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black, contentColor = Color.White) {
            Box(Modifier.fillMaxSize()) {
                if (!cameraGranted) {
                    CameraPermissionPrompt(onRequestCamera, Modifier.align(Alignment.Center))
                    return@Box
                }
                val scope = rememberCoroutineScope()
                val controller = remember(renderer) { CameraController(renderer, scope) }
                LaunchedEffect(devCommand) {
                    devCommand?.mode?.let { name -> Mode.entries.firstOrNull { it.name.equals(name, true) } }
                        ?.let { m -> controller.update { it.copy(mode = m) } }
                    devCommand?.compare?.takeIf {
                        it == 2 || it == 4
                    }?.let { n -> controller.update { it.copy(compareCount = n) } }
                }
                CameraScreen(controller, camera)
                if (devTools) DevTools(renderer, controller, Modifier.align(Alignment.TopStart))
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

/** Dev variant only: performance figures, image source and correction strategy. Technical, so English. */
@Composable
private fun DevTools(renderer: CameraRenderer, controller: CameraController, modifier: Modifier) {
    val stats by renderer.stats.collectAsState()
    var open by remember { mutableStateOf(false) }
    Column(modifier.safeDrawingPadding().padding(8.dp)) {
        TextButton(onClick = { open = !open }, modifier = Modifier.background(Color(0xCC000000))) {
            Text(if (open) "dev ▲" else "dev ▼  ${stats.fps.fmt()} fps  ${stats.views}×", color = Color.White)
        }
        if (!open) return@Column
        Column(
            Modifier.background(Color(0xCC000000)).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(stats.describe(), fontFamily = FontFamily.Monospace, color = Color.White)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (name in renderer.sources) {
                    FilterChip(stats.source == name, { renderer.selectSource(name) }, { Text(name) })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (strategy in CorrectionStrategy.all) {
                    val on = controller.state.settings.strategyId == strategy.id
                    FilterChip(on, {
                        controller.update { it.copy(settings = it.settings.copy(strategyId = strategy.id)) }
                    }, { Text(strategy.id) })
                }
            }
        }
    }
}

private fun RenderStats.describe(): String = buildString {
    appendLine("$source  $resolution  $views view(s)")
    appendLine("draw ${fps.fmt()} fps  camera ${cameraFps.fmt()} fps")
    appendLine("gpu ${gpuMs.fmt()} ms  capture→swap ${captureToSwapMs.fmt()} ms")
    appendLine("  capture→app ${captureToArrivalMs.fmt()} ms  app→swap ${arrivalToSwapMs.fmt()} ms")
    appendLine("vsync ${vsyncMs.fmt()} ms  lut bake ${lutBakeMs.fmt()} ms")
    append("thermal $thermal")
}

private fun Float?.fmt(): String = if (this == null) "–" else ((this * 10).toInt() / 10f).toString()
