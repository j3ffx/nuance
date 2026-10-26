package io.github.j3ffx.nuance.ui

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.SliderDefaults
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
import io.github.j3ffx.nuance.core.correction.CorrectionStrategy
import io.github.j3ffx.nuance.core.correction.ErrorRedistribution
import io.github.j3ffx.nuance.core.cvd.Axis
import io.github.j3ffx.nuance.core.cvd.CvdProfile
import io.github.j3ffx.nuance.core.modes.HuePreset
import io.github.j3ffx.nuance.core.naming.Chroma
import io.github.j3ffx.nuance.core.naming.ColorName
import io.github.j3ffx.nuance.core.naming.ColorTerm
import io.github.j3ffx.nuance.core.naming.Lightness
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
import io.github.j3ffx.nuance.resources.color_beige
import io.github.j3ffx.nuance.resources.color_black
import io.github.j3ffx.nuance.resources.color_blue
import io.github.j3ffx.nuance.resources.color_brown
import io.github.j3ffx.nuance.resources.color_green
import io.github.j3ffx.nuance.resources.color_grey
import io.github.j3ffx.nuance.resources.color_mod_dark
import io.github.j3ffx.nuance.resources.color_mod_greyish
import io.github.j3ffx.nuance.resources.color_mod_light
import io.github.j3ffx.nuance.resources.color_mod_vivid
import io.github.j3ffx.nuance.resources.color_olive
import io.github.j3ffx.nuance.resources.color_orange
import io.github.j3ffx.nuance.resources.color_pink
import io.github.j3ffx.nuance.resources.color_red
import io.github.j3ffx.nuance.resources.color_turquoise
import io.github.j3ffx.nuance.resources.color_violet
import io.github.j3ffx.nuance.resources.color_white
import io.github.j3ffx.nuance.resources.color_yellow
import io.github.j3ffx.nuance.resources.hue_blue
import io.github.j3ffx.nuance.resources.hue_green
import io.github.j3ffx.nuance.resources.hue_orange
import io.github.j3ffx.nuance.resources.hue_pink
import io.github.j3ffx.nuance.resources.hue_red
import io.github.j3ffx.nuance.resources.hue_violet
import io.github.j3ffx.nuance.resources.hue_yellow
import io.github.j3ffx.nuance.resources.profile_summary
import io.github.j3ffx.nuance.resources.strategy_error_redistribution
import io.github.j3ffx.nuance.resources.strategy_lab_remap
import io.github.j3ffx.nuance.resources.strategy_short_error_redistribution
import io.github.j3ffx.nuance.resources.strategy_short_lab_remap
import io.github.j3ffx.nuance.resources.vision_none
import kotlin.math.abs
import kotlin.math.roundToInt
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

/** Like [Choices], on one line that scrolls sideways: for a long list in a short panel. */
@Composable
fun <T> ChoicesRow(
    options: List<T>,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    isSelected: (T) -> Boolean,
    modifier: Modifier = Modifier
) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

/** A slider with its value written on the same line, left of it: one line instead of two. */
@Composable
fun InlineSlider(text: String, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 12.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.outline),
            modifier = Modifier.weight(1f).semantics { stateDescription = text }
        )
    }
}

/** A slider with its value written above it. [value] and the callback are in 0..1. */
@Composable
fun LabeledSlider(text: String, value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        // The track's unfilled part in "outline": the default was below 3:1 on the dark surface.
        Slider(
            value = value,
            onValueChange = onChange,
            colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.outline),
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

/** "Deutan 60 %": one axis at a severity in percent. */
@Composable
fun profileSummary(axis: Axis, severity: Int): String =
    stringResource(Res.string.profile_summary, axisTitle(axis), severity)

/** "Protan 100 % + Tritan 30 %", strongest first; "Vision typique" when no axis is affected. */
@Composable
fun profileSummary(cvd: CvdProfile): String =
    cvd.components.map { profileSummary(it, (cvd.severityOf(it) * 100).roundToInt()) }
        .joinToString(" + ")
        .ifEmpty { stringResource(Res.string.vision_none) }

/** A preset's name, and the summary under a named profile. */
@Composable
fun profileSummary(p: Profile): String = profileSummary(p.cvd)

@Composable
fun profileName(p: Profile): String = p.name.ifEmpty { profileSummary(p) }

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

/** "Bleu grisâtre clair", "Light greyish blue": the term, then each modifier wraps it. */
@Composable
fun colorName(n: ColorName): String {
    var text = stringResource(
        when (n.term) {
            ColorTerm.Black -> Res.string.color_black
            ColorTerm.White -> Res.string.color_white
            ColorTerm.Grey -> Res.string.color_grey
            ColorTerm.Red -> Res.string.color_red
            ColorTerm.Orange -> Res.string.color_orange
            ColorTerm.Brown -> Res.string.color_brown
            ColorTerm.Beige -> Res.string.color_beige
            ColorTerm.Yellow -> Res.string.color_yellow
            ColorTerm.Olive -> Res.string.color_olive
            ColorTerm.Green -> Res.string.color_green
            ColorTerm.Turquoise -> Res.string.color_turquoise
            ColorTerm.Blue -> Res.string.color_blue
            ColorTerm.Violet -> Res.string.color_violet
            ColorTerm.Pink -> Res.string.color_pink
        }
    )
    when (n.chroma) {
        Chroma.Greyish -> text = stringResource(Res.string.color_mod_greyish, text)
        Chroma.Vivid -> text = stringResource(Res.string.color_mod_vivid, text)
        null -> Unit
    }
    when (n.lightness) {
        Lightness.Light -> text = stringResource(Res.string.color_mod_light, text)
        Lightness.Dark -> text = stringResource(Res.string.color_mod_dark, text)
        null -> Unit
    }
    return text.replaceFirstChar { it.uppercaseChar() }
}

/** "Clair et foncé (recommandée)" or "Recoloration". */
@Composable
fun strategyName(st: CorrectionStrategy): String = stringResource(
    if (st === ErrorRedistribution) Res.string.strategy_error_redistribution else Res.string.strategy_lab_remap
)

/** "par la clarté" or "par les teintes": the method's name inside "Méthode : …". */
@Composable
fun strategyShortName(st: CorrectionStrategy): String = stringResource(
    if (st ===
        ErrorRedistribution
    ) {
        Res.string.strategy_short_error_redistribution
    } else {
        Res.string.strategy_short_lab_remap
    }
)
