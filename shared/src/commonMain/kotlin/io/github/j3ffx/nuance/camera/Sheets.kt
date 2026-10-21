package io.github.j3ffx.nuance.camera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.j3ffx.nuance.core.correction.CorrectionStrategy
import io.github.j3ffx.nuance.core.correction.ErrorRedistribution
import io.github.j3ffx.nuance.core.cvd.Axis
import io.github.j3ffx.nuance.core.modes.Mode
import io.github.j3ffx.nuance.core.profile.Profile
import io.github.j3ffx.nuance.platform.Preferences
import io.github.j3ffx.nuance.resources.Res
import io.github.j3ffx.nuance.resources.advanced_compare
import io.github.j3ffx.nuance.resources.advanced_enhance
import io.github.j3ffx.nuance.resources.advanced_method
import io.github.j3ffx.nuance.resources.advanced_profile
import io.github.j3ffx.nuance.resources.advanced_title
import io.github.j3ffx.nuance.resources.cancel
import io.github.j3ffx.nuance.resources.compare_four
import io.github.j3ffx.nuance.resources.compare_two
import io.github.j3ffx.nuance.resources.contrast_value
import io.github.j3ffx.nuance.resources.isolate_width_value
import io.github.j3ffx.nuance.resources.profile_name
import io.github.j3ffx.nuance.resources.profile_name_default
import io.github.j3ffx.nuance.resources.profiles_title
import io.github.j3ffx.nuance.resources.reset_defaults
import io.github.j3ffx.nuance.resources.saturation_value
import io.github.j3ffx.nuance.resources.save
import io.github.j3ffx.nuance.resources.save_as_new
import io.github.j3ffx.nuance.resources.save_to_profile
import io.github.j3ffx.nuance.resources.severity_value
import io.github.j3ffx.nuance.resources.strategy_error_redistribution
import io.github.j3ffx.nuance.resources.strategy_lab_remap
import io.github.j3ffx.nuance.ui.Choices
import io.github.j3ffx.nuance.ui.LabeledSlider
import io.github.j3ffx.nuance.ui.SectionTitle
import io.github.j3ffx.nuance.ui.axisHelp
import io.github.j3ffx.nuance.ui.axisName
import io.github.j3ffx.nuance.ui.axisTitle
import io.github.j3ffx.nuance.ui.profileName
import kotlin.random.Random
import org.jetbrains.compose.resources.stringResource

/** Pick the profile the camera uses; "Manage" opens the profiles screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(controller: CameraController, preferences: Preferences, onManage: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            for (p in preferences.allProfiles) {
                val selected = p.id == controller.state.profileId
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected, role = Role.RadioButton) {
                        controller.applyProfile(p)
                        onDismiss()
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // The check is the selection mark; the row's text alone tells profiles apart.
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        profileName(p),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
            HorizontalDivider()
            TextButton(onClick = onManage, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.profiles_title))
            }
        }
    }
}

/** Everything else, in percent: profile, method, image, hue range, views; reset and save. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSheet(controller: CameraController, preferences: Preferences, onDismiss: () -> Unit) {
    val state = controller.state
    val s = state.settings
    val profile = preferences.profile(state.profileId) ?: Profile.Default
    var naming by remember { mutableStateOf(false) }
    fun update(transform: (io.github.j3ffx.nuance.core.modes.Settings) -> io.github.j3ffx.nuance.core.modes.Settings) =
        controller.update { it.copy(settings = transform(it.settings)) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.navigationBarsPadding().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(Res.string.advanced_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics {
                    heading()
                }
            )

            SectionTitle(stringResource(Res.string.advanced_profile))
            Choices(
                options = Axis.entries,
                label = { axisTitle(it) },
                onSelect = { a -> update { it.copy(profile = it.profile.copy(axis = a)) } },
                isSelected = { it == s.profile.axis }
            )
            Text(
                axisHelp(s.profile.axis),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LabeledSlider(
                stringResource(Res.string.severity_value, percent(s.profile.severity)),
                s.profile.severity.toFloat(),
                { v -> update { it.copy(profile = it.profile.copy(severity = v.toDouble())) } }
            )

            SectionTitle(stringResource(Res.string.advanced_method))
            Choices(
                options = CorrectionStrategy.all,
                label = {
                    stringResource(
                        if (it ===
                            ErrorRedistribution
                        ) {
                            Res.string.strategy_error_redistribution
                        } else {
                            Res.string.strategy_lab_remap
                        }
                    )
                },
                onSelect = { strategy -> update { it.copy(strategyId = strategy.id) } },
                isSelected = { it.id == s.strategyId }
            )

            SectionTitle(stringResource(Res.string.advanced_enhance))
            LabeledSlider(stringResource(Res.string.contrast_value, percent(s.contrast)), s.contrast.toFloat(), { v ->
                update { it.copy(contrast = v.toDouble()) }
            })
            LabeledSlider(
                stringResource(
                    Res.string.saturation_value,
                    percent(s.saturation)
                ),
                s.saturation.toFloat(),
                { v ->
                    update { it.copy(saturation = v.toDouble()) }
                }
            )
            if (state.mode == Mode.Isolate) {
                // 5° to 90° on either side of the target hue.
                LabeledSlider(
                    stringResource(Res.string.isolate_width_value, s.isolateWidth.toInt()),
                    ((s.isolateWidth - 5) / 85).toFloat(),
                    { v -> update { it.copy(isolateWidth = 5 + 85.0 * v) } }
                )
            }

            if (state.mode == Mode.Compare) {
                SectionTitle(stringResource(Res.string.advanced_compare))
                Choices(
                    options = listOf(4, 2),
                    label = { stringResource(if (it == 4) Res.string.compare_four else Res.string.compare_two) },
                    onSelect = { n -> controller.update { it.copy(compareCount = n) } },
                    isSelected = { it == state.compareCount }
                )
                if (state.compareCount == 2) {
                    val axis = axisName(s.profile.axis)
                    Choices(
                        options = listOf(ViewKind.Real, ViewKind.RealSeen, ViewKind.Corrected, ViewKind.CorrectedSeen),
                        label = { viewLabel(it, axis, state) },
                        onSelect = { k ->
                            // Two views at a time: picking a third drops the oldest.
                            controller.update {
                                val picked = if (k in
                                    it.compareSelection
                                ) {
                                    it.compareSelection
                                } else {
                                    (it.compareSelection + k).takeLast(2)
                                }
                                it.copy(compareSelection = picked)
                            }
                        },
                        isSelected = { it in state.compareSelection }
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            TextButton(onClick = { controller.resetTo(profile) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.reset_defaults))
            }
            if (!profile.isPreset && profile.id != Profile.Default.id) {
                OutlinedButton(
                    onClick = { preferences.saveProfile(profile.with(s, state.mode)) },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text(stringResource(Res.string.save_to_profile, profileName(profile)), maxLines = 1) }
            }
            OutlinedButton(onClick = { naming = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.save_as_new))
            }
        }
    }

    if (naming) {
        val defaultName = stringResource(Res.string.profile_name_default)
        var name by remember { mutableStateOf(defaultName) }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text(stringResource(Res.string.save_as_new)) },
            text = {
                OutlinedTextField(name, {
                    name = it
                }, label = { Text(stringResource(Res.string.profile_name)) }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    val p = Profile(
                        newProfileId(),
                        name.trim().ifEmpty {
                            defaultName
                        },
                        s.profile.axis,
                        0
                    ).with(s, state.mode)
                    preferences.saveProfile(p)
                    controller.update { it.copy(profileId = p.id) }
                    naming = false
                }) { Text(stringResource(Res.string.save)) }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text(stringResource(Res.string.cancel)) } }
        )
    }
}

/** A random id: profiles are compared by id, never by name. */
fun newProfileId(): String = "p" + Random.nextLong().toULong().toString(36)
