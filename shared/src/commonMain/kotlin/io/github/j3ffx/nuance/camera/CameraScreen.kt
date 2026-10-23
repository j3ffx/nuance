package io.github.j3ffx.nuance.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.j3ffx.nuance.core.correction.CorrectionStrategy
import io.github.j3ffx.nuance.core.cvd.Axis
import io.github.j3ffx.nuance.core.modes.HuePreset
import io.github.j3ffx.nuance.core.modes.Mode
import io.github.j3ffx.nuance.core.profile.Profile
import io.github.j3ffx.nuance.platform.CaptureFormat
import io.github.j3ffx.nuance.platform.Preferences
import io.github.j3ffx.nuance.render.ViewLayout
import io.github.j3ffx.nuance.resources.Res
import io.github.j3ffx.nuance.resources.action_settings
import io.github.j3ffx.nuance.resources.adjust_compare
import io.github.j3ffx.nuance.resources.adjust_correct
import io.github.j3ffx.nuance.resources.adjust_isolate
import io.github.j3ffx.nuance.resources.compare_four
import io.github.j3ffx.nuance.resources.compare_two
import io.github.j3ffx.nuance.resources.hold_for_original
import io.github.j3ffx.nuance.resources.intensity_value
import io.github.j3ffx.nuance.resources.mode_caption_compare
import io.github.j3ffx.nuance.resources.mode_caption_correct
import io.github.j3ffx.nuance.resources.mode_caption_isolate
import io.github.j3ffx.nuance.resources.mode_caption_simulate
import io.github.j3ffx.nuance.resources.mode_compare
import io.github.j3ffx.nuance.resources.mode_correct
import io.github.j3ffx.nuance.resources.mode_isolate
import io.github.j3ffx.nuance.resources.mode_simulate
import io.github.j3ffx.nuance.resources.profile_chip_description
import io.github.j3ffx.nuance.resources.profile_modified
import io.github.j3ffx.nuance.resources.severity_value
import io.github.j3ffx.nuance.resources.update_chip
import io.github.j3ffx.nuance.resources.update_chip_description
import io.github.j3ffx.nuance.resources.update_done_chip
import io.github.j3ffx.nuance.resources.view_corrected
import io.github.j3ffx.nuance.resources.view_corrected_as_seen
import io.github.j3ffx.nuance.resources.view_corrected_seen
import io.github.j3ffx.nuance.resources.view_isolated
import io.github.j3ffx.nuance.resources.view_original
import io.github.j3ffx.nuance.resources.view_real
import io.github.j3ffx.nuance.resources.view_real_seen
import io.github.j3ffx.nuance.ui.Choices
import io.github.j3ffx.nuance.ui.LabeledSlider
import io.github.j3ffx.nuance.ui.NuanceColors
import io.github.j3ffx.nuance.ui.axisName
import io.github.j3ffx.nuance.ui.hueName
import io.github.j3ffx.nuance.ui.nearestPreset
import io.github.j3ffx.nuance.ui.profileSummary
import io.github.j3ffx.nuance.ui.strategyName
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The camera screen, in two layers: what matters in one gesture (profile, mode, one slider),
 * everything else folded in the Advanced panel. The views never sit under the controls: in
 * portrait the controls are below the image, in landscape beside it.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun CameraScreen(
    controller: CameraController,
    preferences: Preferences,
    onOpenSettings: () -> Unit,
    onManageProfiles: () -> Unit,
    onFindProfile: () -> Unit,
    onOpenImage: () -> Unit,
    camera: @Composable (Modifier) -> Unit
) {
    var showProfiles by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    val status by controller.renderer.camera.collectAsState()
    // Back first leaves a gallery photo or a frozen image, before leaving the app.
    BackHandler(enabled = status.image || status.frozen) {
        if (status.image) controller.renderer.backToCamera() else controller.setFrozen(false)
    }
    controller.autoPauseMinutes = preferences.autoPauseMinutes
    BoxWithConstraints(
        Modifier.fillMaxSize().background(NuanceColors.scrim).pointerInput(Unit) {
            // Every touch, wherever it lands, counts as activity; nothing is consumed here.
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    controller.noteTouch()
                }
            }
        }
    ) {
        val landscape = maxWidth > maxHeight
        val image: @Composable (Modifier) -> Unit = { m ->
            Box(m) {
                CameraArea(controller, preferences, camera)
                TopBar(controller, preferences, { showProfiles = true }, onOpenSettings)
            }
        }
        val controls: @Composable (Modifier) -> Unit = { m -> Controls(controller, { showAdvanced = true }, m) }
        val capture: @Composable () -> Unit = {
            CaptureBar(
                controller,
                preferences.captureFormat == CaptureFormat.FilteredAndOriginal,
                onOpenImage,
                Modifier.fillMaxWidth()
            )
        }
        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                image(Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.width(320.dp).fillMaxHeight()) {
                    capture()
                    controls(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()))
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                image(Modifier.weight(1f).fillMaxWidth())
                capture()
                controls(Modifier.fillMaxWidth())
            }
        }
    }
    if (showProfiles) {
        ProfileSheet(controller, preferences, onManage = {
            showProfiles = false
            onManageProfiles()
        }, onFindProfile = {
            showProfiles = false
            onFindProfile()
        }, onDismiss = { showProfiles = false })
    }
    if (showAdvanced) AdvancedSheet(controller, preferences, onDismiss = { showAdvanced = false })
}

@Composable
private fun CameraArea(controller: CameraController, preferences: Preferences, camera: @Composable (Modifier) -> Unit) {
    val holdDescription = stringResource(Res.string.hold_for_original)
    Box(Modifier.fillMaxSize()) {
        camera(Modifier.fillMaxSize())
        // A transparent layer over the platform view takes the gestures (the view would keep them):
        // a tap points the pipette, a drag moves it, a long press shows the original.
        Box(
            Modifier.fillMaxSize()
                .semantics { contentDescription = holdDescription }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { controller.pick(it.x / size.width, it.y / size.height) },
                        onLongPress = { controller.update { it.copy(showingOriginal = true) } },
                        onPress = {
                            tryAwaitRelease()
                            controller.update { it.copy(showingOriginal = false) }
                        }
                    )
                }
                .pointerInput(Unit) { cameraGestures(controller) }
        )
        ViewLabels(controller.state)
        controller.pipette?.let { PipetteMarker(it) }
        CameraButtons(controller, preferences, Modifier.align(Alignment.CenterEnd).padding(end = 12.dp))
        CameraLabel(controller, Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp))
        PausedCover(controller)
        PipetteCard(
            controller,
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 60.dp, start = 8.dp, end = 8.dp)
        )
    }
}

@Composable
private fun TopBar(
    controller: CameraController,
    preferences: Preferences,
    onProfile: () -> Unit,
    onSettings: () -> Unit
) {
    val s = controller.state.settings
    val profile = preferences.profile(controller.state.profileId)
    val name = profile?.name?.takeIf { it.isNotEmpty() }
    val summary = profileSummary(s.profile.axis, (s.profile.severity * 100).roundToInt())
    val named = if (name != null) "$name · $summary" else summary
    // Changed since the profile was picked: written out, so it is never a colour alone.
    val modified = profile?.isModifiedBy(s) == true
    val label = if (modified) stringResource(Res.string.profile_modified, named) else named
    val description = stringResource(Res.string.profile_chip_description, label)
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The chip gives way first, so the gear always fits; the arrow says it opens a list.
        Box(Modifier.weight(1f)) {
            AssistChip(
                onClick = onProfile,
                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                // A pencil too when changed: the shape still tells it once a long name cuts the text.
                leadingIcon = {
                    Icon(
                        if (modified) Icons.Filled.Edit else Icons.Filled.Person,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = NuanceColors.scrim,
                    labelColor = NuanceColors.onScrim,
                    leadingIconContentColor = NuanceColors.onScrim,
                    trailingIconContentColor = NuanceColors.onScrim
                ),
                modifier = Modifier.heightIn(min = 48.dp)
                    .semantics { contentDescription = description }
            )
        }
        preferences.justUpdated?.let { version ->
            AssistChip(
                onClick = { preferences.justUpdated = null },
                label = { Text(stringResource(Res.string.update_done_chip, version), maxLines = 1) },
                leadingIcon = { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = NuanceColors.scrim,
                    labelColor = NuanceColors.onScrim,
                    leadingIconContentColor = NuanceColors.onScrim
                ),
                modifier = Modifier.heightIn(min = 48.dp).padding(end = 8.dp)
            )
        }
        preferences.availableUpdate?.let { update ->
            val updateDescription = stringResource(Res.string.update_chip_description, update.version)
            AssistChip(
                onClick = onSettings,
                label = { Text(stringResource(Res.string.update_chip), maxLines = 1) },
                leadingIcon = {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = NuanceColors.scrim,
                    labelColor = NuanceColors.onScrim,
                    leadingIconContentColor = NuanceColors.onScrim
                ),
                modifier = Modifier.heightIn(min = 48.dp).padding(end = 8.dp).semantics {
                    contentDescription =
                        updateDescription
                }
            )
        }
        IconButton(
            onClick = onSettings,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = NuanceColors.scrim,
                contentColor = NuanceColors.onScrim
            )
        ) {
            Icon(Icons.Filled.Settings, contentDescription = stringResource(Res.string.action_settings))
        }
    }
}

@Composable
private fun ViewLabels(state: ViewState) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rects = ViewLayout.rects(state.views.size, maxWidth > maxHeight)
        val axis = axisName(state.settings.profile.axis)
        for ((kind, rect) in state.views.zip(rects)) {
            val text = when {
                state.showingOriginal -> stringResource(Res.string.view_original)

                state.views.size == 1 && kind == ViewKind.Corrected -> null

                // the main mode needs no label
                else -> viewLabel(kind, axis, state)
            } ?: continue
            // At the bottom left of its view: clear of the top bar.
            Box(
                Modifier.offset(maxWidth * rect.x, maxHeight * rect.y).size(
                    maxWidth * rect.width,
                    maxHeight * rect.height
                )
            ) {
                Text(
                    text,
                    color = NuanceColors.onScrim,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .background(NuanceColors.scrim, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
internal fun viewLabel(kind: ViewKind, axis: String, state: ViewState): String = when (kind) {
    ViewKind.Real -> stringResource(Res.string.view_real)
    ViewKind.RealSeen -> stringResource(Res.string.view_real_seen, axis)
    ViewKind.Corrected -> stringResource(Res.string.view_corrected)
    ViewKind.CorrectedSeen -> stringResource(Res.string.view_corrected_seen, axis)
    ViewKind.Isolated -> stringResource(Res.string.view_isolated, hueName(nearestPreset(state.settings.isolateHue)))
    ViewKind.ProtanCorrectedSeen -> stringResource(Res.string.view_corrected_as_seen, axisName(Axis.Protan), axis)
    ViewKind.DeutanCorrectedSeen -> stringResource(Res.string.view_corrected_as_seen, axisName(Axis.Deutan), axis)
}

@Composable
private fun Controls(controller: CameraController, onAdvanced: () -> Unit, modifier: Modifier) {
    val state = controller.state
    val s = state.settings
    Surface(modifier, color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (mode in Mode.entries) {
                    ModeButton(stringResource(modeName(mode)), state.mode == mode) {
                        controller.update { it.copy(mode = mode) }
                    }
                }
            }
            // What the picked mode is for, in a few words: the names alone assumed too much.
            Text(
                stringResource(modeCaption(state.mode)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            when (state.mode) {
                Mode.Correct, Mode.Compare -> {
                    LabeledSlider(
                        stringResource(Res.string.intensity_value, percent(s.intensity)),
                        s.intensity.toFloat(),
                        { v -> controller.update { it.copy(settings = it.settings.copy(intensity = v.toDouble())) } }
                    )
                    // The method is one tap away: people compare both on what they look at.
                    Choices(
                        options = CorrectionStrategy.all,
                        label = { strategyName(it) },
                        onSelect = { st ->
                            controller.update { it.copy(settings = it.settings.copy(strategyId = st.id)) }
                        },
                        isSelected = { it.id == s.strategyId }
                    )
                    if (state.mode == Mode.Compare) {
                        Choices(
                            options = listOf(4, 2),
                            label = {
                                stringResource(
                                    if (it ==
                                        4
                                    ) {
                                        Res.string.compare_four
                                    } else {
                                        Res.string.compare_two
                                    }
                                )
                            },
                            onSelect = { n -> controller.update { it.copy(compareCount = n) } },
                            isSelected = { it == state.compareCount }
                        )
                    }
                }

                Mode.Simulate -> LabeledSlider(
                    stringResource(Res.string.severity_value, percent(s.profile.severity)),
                    s.profile.severity.toFloat(),
                    { v ->
                        controller.update {
                            it.copy(
                                settings = it.settings.copy(profile = it.settings.profile.copy(severity = v.toDouble()))
                            )
                        }
                    }
                )

                Mode.Isolate -> Choices(
                    options = HuePreset.entries,
                    label = { hueName(it) },
                    onSelect = { p -> controller.update { it.copy(settings = it.settings.copy(isolateHue = p.hue)) } },
                    isSelected = { it == nearestPreset(s.isolateHue) }
                )
            }
            // Named after what it adjusts; Simulate has nothing more to adjust.
            val adjust = when (state.mode) {
                Mode.Correct -> Res.string.adjust_correct
                Mode.Isolate -> Res.string.adjust_isolate
                Mode.Compare -> Res.string.adjust_compare
                Mode.Simulate -> null
            }
            if (adjust != null) {
                OutlinedButton(onClick = onAdvanced, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(adjust), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/** One of the four modes: the picked one is filled, in bold, and announced as selected. */
@Composable
private fun ModeButton(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) colors.primary else colors.surface,
        contentColor = if (selected) colors.onPrimary else colors.onSurface,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, colors.outline),
        modifier = Modifier.heightIn(min = 48.dp).padding(vertical = 4.dp).semantics {
            role = Role.Tab
            this.selected = selected
        }
    ) {
        Box(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}

internal fun percent(v: Double) = (v * 100).roundToInt()

internal fun modeCaption(m: Mode): StringResource = when (m) {
    Mode.Correct -> Res.string.mode_caption_correct
    Mode.Isolate -> Res.string.mode_caption_isolate
    Mode.Simulate -> Res.string.mode_caption_simulate
    Mode.Compare -> Res.string.mode_caption_compare
}

internal fun modeName(m: Mode): StringResource = when (m) {
    Mode.Correct -> Res.string.mode_correct
    Mode.Isolate -> Res.string.mode_isolate
    Mode.Simulate -> Res.string.mode_simulate
    Mode.Compare -> Res.string.mode_compare
}

/** Unused presets are offered first in pickers; the default lives in [Profile.Default]. */
internal fun pickerProfiles(preferences: Preferences): List<Profile> = preferences.allProfiles
