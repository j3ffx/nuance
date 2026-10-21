package io.github.j3ffx.nuance.dev

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.j3ffx.nuance.core.cvd.CvdProfile
import io.github.j3ffx.nuance.render.CameraRenderer
import io.github.j3ffx.nuance.render.RenderStats

/** Dev variant: how the whole app is framed, to check layouts at 360 dp and with large text. */
class DevPreview {
    var narrow by mutableStateOf(false)
    var largeText by mutableStateOf(false)

    /** "Find my profile" seen through this simulation, to take it "as" a protan, deutan or tritan. */
    var testAs by mutableStateOf<CvdProfile?>(null)
}

/** Dev variant only: performance figures, image source, layout checks. Technical, so English. */
@Composable
fun DevTools(renderer: CameraRenderer, preview: DevPreview, modifier: Modifier) {
    val stats by renderer.stats.collectAsState()
    var open by remember { mutableStateOf(false) }
    Column(modifier.padding(8.dp)) {
        TextButton(onClick = { open = !open }, modifier = Modifier.background(Color(0xCC000000))) {
            Text(if (open) "dev ▲" else "dev ▼  ${stats.fps.fmt()} fps  ${stats.views}×", color = Color.White)
        }
        if (!open) return@Column
        Column(Modifier.background(Color(0xCC000000)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stats.describe(), fontFamily = FontFamily.Monospace, color = Color.White)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (name in renderer.sources) {
                    FilterChip(stats.source == name, { renderer.selectSource(name) }, { Text(name) })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(preview.narrow, { preview.narrow = !preview.narrow }, { Text("360 dp") })
                FilterChip(preview.largeText, { preview.largeText = !preview.largeText }, { Text("text ×1.3") })
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
