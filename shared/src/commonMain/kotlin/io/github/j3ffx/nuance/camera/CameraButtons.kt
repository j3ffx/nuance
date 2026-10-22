package io.github.j3ffx.nuance.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.j3ffx.nuance.render.CameraStatus
import io.github.j3ffx.nuance.resources.Res
import io.github.j3ffx.nuance.resources.camera_flip
import io.github.j3ffx.nuance.resources.camera_freeze
import io.github.j3ffx.nuance.resources.camera_frozen
import io.github.j3ffx.nuance.resources.camera_resume
import io.github.j3ffx.nuance.resources.camera_torch_off
import io.github.j3ffx.nuance.resources.camera_torch_on
import io.github.j3ffx.nuance.resources.camera_zoom
import io.github.j3ffx.nuance.ui.CameraIcons
import io.github.j3ffx.nuance.ui.NuanceColors
import kotlin.math.abs
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/**
 * The camera's own buttons, in a column over the image's side: freeze, torch, front/back,
 * and the zoom, which steps through the lenses (pinching zooms finely).
 */
@Composable
internal fun CameraButtons(controller: CameraController, modifier: Modifier) {
    val status by controller.renderer.camera.collectAsState()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.End) {
        RoundButton(
            if (status.frozen) CameraIcons.Play else CameraIcons.Pause,
            stringResource(if (status.frozen) Res.string.camera_resume else Res.string.camera_freeze)
        ) { controller.setFrozen(!status.frozen) }
        if (status.hasTorch && !status.frozen) {
            RoundButton(
                if (status.torch) CameraIcons.FlashOff else CameraIcons.FlashOn,
                stringResource(if (status.torch) Res.string.camera_torch_off else Res.string.camera_torch_on)
            ) { controller.renderer.setTorch(!status.torch) }
        }
        if (status.canSwitchFacing) {
            RoundButton(CameraIcons.FlipCamera, stringResource(Res.string.camera_flip)) {
                controller.renderer.switchFacing()
            }
        }
        if (status.maxZoom > status.minZoom && !status.frozen) {
            val text = zoomText(status.zoom)
            val description = stringResource(Res.string.camera_zoom, text)
            Surface(
                onClick = { controller.renderer.setZoom(nextZoomStep(status)) },
                shape = CircleShape,
                color = NuanceColors.scrim,
                contentColor = NuanceColors.onScrim,
                modifier = Modifier.size(52.dp).semantics {
                    role = Role.Button
                    contentDescription = description
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** While frozen, a written reminder: the play icon alone could be missed. */
@Composable
internal fun FrozenLabel(controller: CameraController, modifier: Modifier) {
    val status by controller.renderer.camera.collectAsState()
    if (!status.frozen) return
    Text(
        stringResource(Res.string.camera_frozen),
        color = NuanceColors.onScrim,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier.background(NuanceColors.scrim, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@Composable
private fun RoundButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = NuanceColors.scrim,
        contentColor = NuanceColors.onScrim,
        modifier = Modifier.size(52.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(26.dp))
        }
    }
}

/** "0.6×", "1×", "2.5×". */
internal fun zoomText(zoom: Float): String {
    val tenths = (zoom * 10).roundToInt()
    return if (tenths % 10 == 0) "${tenths / 10}×" else "${tenths / 10}.${tenths % 10}×"
}

/**
 * The next of the usual lens steps (the ultra wide if there is one, 1×, 2×, 3×, 5×, 10×) within
 * the camera's range, back to the first after the last.
 */
internal fun nextZoomStep(s: CameraStatus): Float {
    val steps = (listOf(s.minZoom) + listOf(1f, 2f, 3f, 5f, 10f))
        .filter { it >= s.minZoom - 0.01f && it <= s.maxZoom + 0.01f }
        .distinctBy { (it * 10).roundToInt() }
        .sorted()
    return steps.firstOrNull { it > s.zoom + 0.05f } ?: steps.first()
}

/**
 * Pinch and drag on the image. Two fingers: zoom (the camera's while live, into the image while
 * frozen) and, while frozen, move. One finger: moves the pipette when it is open, else moves
 * around the zoomed frozen image. Taps and long presses are left to the tap detector.
 */
internal suspend fun PointerInputScope.cameraGestures(controller: CameraController) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var dragging = false
        var travelled = Offset.Zero
        do {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 2) {
                controller.transform(event.calculateZoom(), event.calculatePan(), size)
                event.changes.forEach { it.consume() }
            } else if (pressed.size == 1) {
                val change = pressed.single()
                travelled += change.positionChange()
                if (!dragging && abs(travelled.x) + abs(travelled.y) > viewConfiguration.touchSlop) dragging = true
                if (dragging && controller.drag(change.position, change.positionChange(), size)) change.consume()
            }
        } while (event.changes.any { it.pressed })
    }
}

/** Pixels → fractions of the image area. */
internal fun Offset.fraction(size: IntSize) = Offset(x / size.width, y / size.height)
