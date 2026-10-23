package io.github.j3ffx.nuance.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.j3ffx.nuance.core.cvd.Axis
import io.github.j3ffx.nuance.resources.Res
import io.github.j3ffx.nuance.resources.vision_hint
import io.github.j3ffx.nuance.resources.vision_mixed_note
import io.github.j3ffx.nuance.resources.vision_value
import io.github.j3ffx.nuance.ui.LabeledSlider
import io.github.j3ffx.nuance.ui.axisHelp
import io.github.j3ffx.nuance.ui.axisTitle
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/**
 * Who the profile is for: one slider per axis, 0 to 100 %, mixed at will. Usually one axis only,
 * which the hint says; a mix is allowed and said to be an approximation (docs/ALGORITHMS.md,
 * "Profils mixtes"). [severities] and [onChange] are in percent, in [Axis] order.
 */
@Composable
fun VisionEditor(severities: List<Int>, onChange: (List<Int>) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(Res.string.vision_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        for (axis in Axis.entries) {
            val value = severities[axis.ordinal]
            LabeledSlider(
                stringResource(Res.string.vision_value, axisTitle(axis), value),
                value / 100f,
                { v -> onChange(severities.toMutableList().also { it[axis.ordinal] = (v * 100).roundToInt() }) }
            )
            Text(
                axisHelp(axis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (severities.count { it > 0 } > 1) {
            Text(stringResource(Res.string.vision_mixed_note), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
