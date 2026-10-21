package io.github.j3ffx.nuance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.j3ffx.nuance.core.cvd.Axis
import io.github.j3ffx.nuance.core.modes.HuePreset
import io.github.j3ffx.nuance.core.profile.Profile
import io.github.j3ffx.nuance.resources.Res
import io.github.j3ffx.nuance.resources.axis_deutan
import io.github.j3ffx.nuance.resources.axis_deutan_help
import io.github.j3ffx.nuance.resources.axis_deutan_title
import io.github.j3ffx.nuance.resources.axis_protan
import io.github.j3ffx.nuance.resources.axis_protan_help
import io.github.j3ffx.nuance.resources.axis_protan_title
import io.github.j3ffx.nuance.resources.axis_tritan
import io.github.j3ffx.nuance.resources.axis_tritan_help
import io.github.j3ffx.nuance.resources.axis_tritan_title
import io.github.j3ffx.nuance.resources.back
import io.github.j3ffx.nuance.resources.hue_blue
import io.github.j3ffx.nuance.resources.hue_green
import io.github.j3ffx.nuance.resources.hue_orange
import io.github.j3ffx.nuance.resources.hue_pink
import io.github.j3ffx.nuance.resources.hue_red
import io.github.j3ffx.nuance.resources.hue_violet
import io.github.j3ffx.nuance.resources.hue_yellow
import io.github.j3ffx.nuance.resources.profile_summary
import kotlin.math.abs
import org.jetbrains.compose.resources.stringResource

/** A page with a title and a back arrow, its content scrolling. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Page(
    title: String,
    onBack: () -> Unit,
    snackbar: SnackbarHostState? = null,
    actions: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                },
                actions = { actions() }
            )
        },
        snackbarHost = { snackbar?.let { SnackbarHost(it) } }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() }
    )
}

/** Chips to pick one option; the picked one also shows a check mark, never colour alone. */
@Composable
fun <T> Choices(
    options: List<T>,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    isSelected: (T) -> Boolean,
    modifier: Modifier = Modifier
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (option in options) {
            val on = isSelected(option)
            FilterChip(
                selected = on,
                onClick = { onSelect(option) },
                label = { Text(label(option), maxLines = 1) },
                leadingIcon = if (on) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else {
                    null
                },
                modifier = Modifier.heightIn(min = 48.dp)
            )
        }
    }
}

/** A slider with its value written above it. [value] and the callback are in 0..1. */
@Composable
fun LabeledSlider(text: String, value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().semantics { stateDescription = text }
        )
    }
}

/** A row that reads as a switch: the state is written too, on the line below the title. */
@Composable
fun SwitchRow(
    text: String,
    help: String?,
    checked: Boolean,
    onText: String,
    offText: String,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            val state = if (checked) onText else offText
            Text(
                if (help == null) state else "$state · $help",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = null, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
fun axisName(a: Axis): String = stringResource(
    when (a) {
        Axis.Protan -> Res.string.axis_protan
        Axis.Deutan -> Res.string.axis_deutan
        Axis.Tritan -> Res.string.axis_tritan
    }
)

@Composable
fun axisTitle(a: Axis): String = stringResource(
    when (a) {
        Axis.Protan -> Res.string.axis_protan_title
        Axis.Deutan -> Res.string.axis_deutan_title
        Axis.Tritan -> Res.string.axis_tritan_title
    }
)

@Composable
fun axisHelp(a: Axis): String = stringResource(
    when (a) {
        Axis.Protan -> Res.string.axis_protan_help
        Axis.Deutan -> Res.string.axis_deutan_help
        Axis.Tritan -> Res.string.axis_tritan_help
    }
)

/** "Deutan 60 %": a preset's name, and the summary under a named profile. */
@Composable
fun profileSummary(axis: Axis, severity: Int): String =
    stringResource(Res.string.profile_summary, axisTitle(axis), severity)

@Composable
fun profileName(p: Profile): String = p.name.ifEmpty { profileSummary(p.axis, p.severity) }

@Composable
fun hueName(p: HuePreset): String = stringResource(
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

fun nearestPreset(hue: Double): HuePreset = HuePreset.entries.minBy {
    abs(it.hue - hue).let { d -> if (d > 180) 360 - d else d }
}
