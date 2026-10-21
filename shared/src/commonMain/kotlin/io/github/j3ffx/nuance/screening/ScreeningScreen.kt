package io.github.j3ffx.nuance.screening

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.j3ffx.nuance.core.Rgb
import io.github.j3ffx.nuance.core.cvd.Axis
import io.github.j3ffx.nuance.core.cvd.CvdProfile
import io.github.j3ffx.nuance.core.cvd.Simulation
import io.github.j3ffx.nuance.core.profile.Profile
import io.github.j3ffx.nuance.core.screening.Confidence
import io.github.j3ffx.nuance.core.screening.D15
import io.github.j3ffx.nuance.core.screening.Plate
import io.github.j3ffx.nuance.core.screening.Screening
import io.github.j3ffx.nuance.core.screening.ScreeningResult
import io.github.j3ffx.nuance.resources.Res
import io.github.j3ffx.nuance.resources.screening_arrange_help
import io.github.j3ffx.nuance.resources.screening_arrange_last
import io.github.j3ffx.nuance.resources.screening_arrange_left
import io.github.j3ffx.nuance.resources.screening_arrange_title
import io.github.j3ffx.nuance.resources.screening_arrange_undo
import io.github.j3ffx.nuance.resources.screening_cap
import io.github.j3ffx.nuance.resources.screening_conditions
import io.github.j3ffx.nuance.resources.screening_confidence_high
import io.github.j3ffx.nuance.resources.screening_confidence_low
import io.github.j3ffx.nuance.resources.screening_confidence_medium
import io.github.j3ffx.nuance.resources.screening_dev
import io.github.j3ffx.nuance.resources.screening_intro
import io.github.j3ffx.nuance.resources.screening_none
import io.github.j3ffx.nuance.resources.screening_none_help
import io.github.j3ffx.nuance.resources.screening_not_medical
import io.github.j3ffx.nuance.resources.screening_nothing
import io.github.j3ffx.nuance.resources.screening_patch
import io.github.j3ffx.nuance.resources.screening_patches_help
import io.github.j3ffx.nuance.resources.screening_patches_none
import io.github.j3ffx.nuance.resources.screening_patches_title
import io.github.j3ffx.nuance.resources.screening_plate
import io.github.j3ffx.nuance.resources.screening_plate_description
import io.github.j3ffx.nuance.resources.screening_plate_question
import io.github.j3ffx.nuance.resources.screening_private
import io.github.j3ffx.nuance.resources.screening_proposed
import io.github.j3ffx.nuance.resources.screening_redo
import io.github.j3ffx.nuance.resources.screening_result_title
import io.github.j3ffx.nuance.resources.screening_save_name
import io.github.j3ffx.nuance.resources.screening_start
import io.github.j3ffx.nuance.resources.screening_step
import io.github.j3ffx.nuance.resources.screening_title
import io.github.j3ffx.nuance.resources.screening_trial
import io.github.j3ffx.nuance.resources.screening_use
import io.github.j3ffx.nuance.resources.severity_value
import io.github.j3ffx.nuance.ui.Choices
import io.github.j3ffx.nuance.ui.LabeledSlider
import io.github.j3ffx.nuance.ui.Page
import io.github.j3ffx.nuance.ui.SectionTitle
import io.github.j3ffx.nuance.ui.axisHelp
import io.github.j3ffx.nuance.ui.axisTitle
import io.github.j3ffx.nuance.ui.profileSummary
import kotlin.math.roundToInt
import kotlin.random.Random
import org.jetbrains.compose.resources.stringResource

/**
 * "Find my profile": an introduction, the plates, the hue arrangement, the patches, then the
 * proposed profile, which the person can adjust before keeping it. [testAs] (dev variant only)
 * shows every colour of the test through a simulation, so it can be taken "as" a profile.
 * [onKeep] gets the profile to save and use; [onResult] the raw result, to remember.
 */
@Composable
fun ScreeningScreen(
    devTools: Boolean,
    testAs: CvdProfile?,
    onTestAs: (CvdProfile?) -> Unit,
    onResult: (ScreeningResult) -> Unit,
    onKeep: (Profile) -> Unit,
    onBack: () -> Unit
) {
    var attempt by remember { mutableIntStateOf(0) }
    var started by remember { mutableStateOf(false) }
    val session = remember(attempt) { Screening(Random.nextLong()) }
    var tick by remember(attempt) { mutableIntStateOf(0) }
    val see: (Rgb) -> Rgb = remember(testAs) { testAs?.let { Simulation.rgb(it) } ?: { it } }
    fun act(block: Screening.() -> Unit) {
        session.block()
        tick++
        if (session.step == Screening.Step.Done) onResult(session.result())
    }

    Page(stringResource(Res.string.screening_title), onBack) {
        if (!started) {
            Intro(devTools, testAs, onTestAs) { started = true }
            return@Page
        }
        // The session is plain logic: each answer bumps tick, which redraws the step.
        key(tick) {
            when (session.step) {
                Screening.Step.Plates -> PlateStep(session, see) { digit -> act { answerPlate(digit) } }

                Screening.Step.Arrangement -> ArrangementStep(
                    session,
                    see,
                    onPlace = { cap -> act { place(cap) } },
                    onUndo = { act { undo() } }
                )

                Screening.Step.Severity -> PatchStep(session, see) { choice -> act { answerTrial(choice) } }

                Screening.Step.Done -> ResultStep(remember(session) { session.result() }, onKeep) {
                    attempt++
                }
            }
        }
    }
}

@Composable
private fun Intro(devTools: Boolean, testAs: CvdProfile?, onTestAs: (CvdProfile?) -> Unit, onStart: () -> Unit) {
    Text(stringResource(Res.string.screening_intro), style = MaterialTheme.typography.bodyLarge)
    Text(stringResource(Res.string.screening_conditions), style = MaterialTheme.typography.bodyLarge)
    Text(stringResource(Res.string.screening_private), style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(Res.string.screening_not_medical), style = MaterialTheme.typography.bodyMedium)
    if (devTools) {
        // Dev variant only: take the test "as" a profile, to check that it lands right.
        SectionTitle(stringResource(Res.string.screening_dev))
        val options = listOf<CvdProfile?>(null) +
            Axis.entries.flatMap { a -> listOf(CvdProfile(a, 1.0), CvdProfile(a, 0.6)) }
        Choices(
            options,
            { p -> p?.let { "${it.axis.name} ${(it.severity * 100).roundToInt()} %" } ?: "off" },
            onTestAs,
            { it == testAs }
        )
    }
    Button(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(stringResource(Res.string.screening_start))
    }
}

@Composable
private fun StepHeader(step: Int, title: String, help: String) {
    Text(stringResource(Res.string.screening_step, step), style = MaterialTheme.typography.labelLarge)
    SectionTitle(title)
    Text(help, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun PlateStep(session: Screening, see: (Rgb) -> Rgb, onAnswer: (Int?) -> Unit) {
    val plate = session.plate ?: return
    StepHeader(
        1,
        stringResource(Res.string.screening_plate, session.plateIndex + 1, session.plates.size),
        stringResource(Res.string.screening_plate_question)
    )
    PlateView(plate, see)
    // 3 × 3 digits, then "I see nothing": large targets, the same place for each plate.
    for (row in 0..2) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (col in 1..3) {
                val digit = row * 3 + col
                OutlinedButton(
                    onClick = { onAnswer(digit) },
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp)
                ) { Text("$digit", style = MaterialTheme.typography.titleLarge) }
            }
        }
    }
    OutlinedButton(onClick = { onAnswer(null) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(stringResource(Res.string.screening_nothing))
    }
}

/** The plate, drawn dot by dot, as large as the width allows (up to 340 dp). */
@Composable
private fun PlateView(plate: Plate, see: (Rgb) -> Rgb) {
    val colors = remember(plate, see) { plate.dots.map { see(it.color).toColor() } }
    val description = stringResource(Res.string.screening_plate_description)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.widthIn(max = 340.dp).fillMaxWidth().aspectRatio(1f).semantics {
                contentDescription = description
            }
        ) {
            val s = size.minDimension
            drawCircle(PlateBackground, s / 2, Offset(s / 2, s / 2))
            for ((dot, color) in plate.dots.zip(colors)) {
                drawCircle(color, dot.radius * s, Offset(dot.x * s, dot.y * s))
            }
        }
    }
}

@Composable
private fun ArrangementStep(session: Screening, see: (Rgb) -> Rgb, onPlace: (Int) -> Unit, onUndo: () -> Unit) {
    StepHeader(
        2,
        stringResource(Res.string.screening_arrange_title),
        stringResource(Res.string.screening_arrange_help)
    )
    val placed = session.arrangement
    val last = placed.lastOrNull() ?: 0
    Box(
        Modifier.fillMaxWidth().background(TestBackground, RoundedCornerShape(12.dp)).padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.screening_arrange_last), color = Color.White)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Cap(see(D15.caps[last]), 72, null)
                // The row so far, small: the pilot, then each cap placed.
                FlowRow(Modifier.padding(start = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (cap in listOf(0) + placed) Cap(see(D15.caps[cap]), 20, null)
                }
            }
            Text(stringResource(Res.string.screening_arrange_left), color = Color.White)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for ((i, cap) in session.offeredCaps.withIndex()) {
                    if (cap in placed) continue
                    val label = stringResource(Res.string.screening_cap, i + 1)
                    Cap(see(D15.caps[cap]), 56, label) { onPlace(cap) }
                }
            }
        }
    }
    TextButton(onClick = onUndo, enabled = placed.isNotEmpty(), modifier = Modifier.heightIn(min = 48.dp)) {
        Text(stringResource(Res.string.screening_arrange_undo))
    }
}

@Composable
private fun Cap(c: Rgb, sizeDp: Int, label: String?, onClick: (() -> Unit)? = null) {
    var m = Modifier.size(sizeDp.dp).border(BorderStroke(1.dp, Color(0xFF9A9A9A)), CircleShape)
        .padding(1.dp).background(c.toColor(), CircleShape)
    if (onClick != null) {
        m = m.clickable(onClick = onClick).semantics {
            role = Role.Button
            if (label != null) contentDescription = label
        }
    }
    Box(m)
}

@Composable
private fun PatchStep(session: Screening, see: (Rgb) -> Rgb, onAnswer: (Int?) -> Unit) {
    val staircase = session.staircase ?: return
    val trial = staircase.trial
    StepHeader(
        3,
        stringResource(Res.string.screening_patches_title),
        stringResource(Res.string.screening_patches_help)
    )
    Text(stringResource(Res.string.screening_trial, session.trialNumber), style = MaterialTheme.typography.labelLarge)
    BoxWithConstraints(
        Modifier.fillMaxWidth().background(TestBackground, RoundedCornerShape(12.dp)).padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        val side = minOf(maxWidth, 320.dp)
        Column(Modifier.size(side), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            for (row in 0..1) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (col in 0..1) {
                        val i = row * 2 + col
                        val color = see(if (i == trial.odd) trial.different else trial.common).toColor()
                        val label = stringResource(Res.string.screening_patch, i + 1)
                        Box(
                            Modifier.weight(1f).aspectRatio(1f)
                                .background(color, RoundedCornerShape(12.dp))
                                .clickable { onAnswer(i) }
                                .semantics {
                                    role = Role.Button
                                    contentDescription = label
                                }
                        )
                    }
                }
            }
        }
    }
    OutlinedButton(onClick = { onAnswer(null) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(stringResource(Res.string.screening_patches_none))
    }
}

@Composable
private fun ResultStep(result: ScreeningResult, onKeep: (Profile) -> Unit, onRedo: () -> Unit) {
    var axis by remember(result) { mutableStateOf(result.axis ?: Axis.Deutan) }
    var severity by remember(result) { mutableIntStateOf(result.severity.coerceIn(0, 100)) }
    val name = stringResource(Res.string.screening_save_name)
    SectionTitle(stringResource(Res.string.screening_result_title))
    if (result.axis == null) {
        Text(
            stringResource(Res.string.screening_none),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(stringResource(Res.string.screening_none_help), style = MaterialTheme.typography.bodyLarge)
    } else {
        Text(
            stringResource(Res.string.screening_proposed, profileSummary(result.axis!!, result.severity)),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(axisHelp(result.axis!!), style = MaterialTheme.typography.bodyLarge)
    }
    Text(
        stringResource(
            when (result.confidence) {
                Confidence.High -> Res.string.screening_confidence_high
                Confidence.Medium -> Res.string.screening_confidence_medium
                Confidence.Low -> Res.string.screening_confidence_low
            }
        ),
        style = MaterialTheme.typography.bodyLarge
    )
    Text(stringResource(Res.string.screening_not_medical), style = MaterialTheme.typography.bodyMedium)

    // The proposal can be adjusted before it is kept.
    Choices(Axis.entries, { axisTitle(it) }, { axis = it }, { it == axis })
    LabeledSlider(
        stringResource(Res.string.severity_value, severity),
        severity / 100f,
        { severity = (it * 100).roundToInt() }
    )
    Button(
        onClick = { onKeep(Profile("", name, axis, severity)) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    ) { Text(stringResource(Res.string.screening_use, profileSummary(axis, severity))) }
    OutlinedButton(onClick = onRedo, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(Res.string.screening_redo))
    }
}

private fun Rgb.toColor() = Color(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f))

/** Neutral greys behind the test's colours, the same in both themes. */
private val PlateBackground = Color(0xFF2B2B2B)
private val TestBackground = Color(0xFF333333)
