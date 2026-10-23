package io.github.j3ffx.nuance.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.j3ffx.nuance.platform.CaptureFormat
import io.github.j3ffx.nuance.platform.Platform
import io.github.j3ffx.nuance.platform.Preferences
import io.github.j3ffx.nuance.platform.ThemeChoice
import io.github.j3ffx.nuance.platform.UpdateOutcome
import io.github.j3ffx.nuance.platform.UpdateProgress
import io.github.j3ffx.nuance.platform.Versions
import io.github.j3ffx.nuance.resources.Res
import io.github.j3ffx.nuance.resources.about_text
import io.github.j3ffx.nuance.resources.auto_pause_minutes
import io.github.j3ffx.nuance.resources.auto_pause_never
import io.github.j3ffx.nuance.resources.capture_both
import io.github.j3ffx.nuance.resources.capture_filtered
import io.github.j3ffx.nuance.resources.check_now
import io.github.j3ffx.nuance.resources.help_image
import io.github.j3ffx.nuance.resources.help_image_title
import io.github.j3ffx.nuance.resources.help_intro
import io.github.j3ffx.nuance.resources.help_medical
import io.github.j3ffx.nuance.resources.help_medical_title
import io.github.j3ffx.nuance.resources.help_modes
import io.github.j3ffx.nuance.resources.help_modes_title
import io.github.j3ffx.nuance.resources.help_photos
import io.github.j3ffx.nuance.resources.help_photos_title
import io.github.j3ffx.nuance.resources.help_privacy
import io.github.j3ffx.nuance.resources.help_privacy_title
import io.github.j3ffx.nuance.resources.help_profile
import io.github.j3ffx.nuance.resources.help_profile_title
import io.github.j3ffx.nuance.resources.language_phone
import io.github.j3ffx.nuance.resources.off
import io.github.j3ffx.nuance.resources.on
import io.github.j3ffx.nuance.resources.settings_about
import io.github.j3ffx.nuance.resources.settings_auto_pause
import io.github.j3ffx.nuance.resources.settings_auto_pause_help
import io.github.j3ffx.nuance.resources.settings_capture
import io.github.j3ffx.nuance.resources.settings_find_profile
import io.github.j3ffx.nuance.resources.settings_help
import io.github.j3ffx.nuance.resources.settings_language
import io.github.j3ffx.nuance.resources.settings_theme
import io.github.j3ffx.nuance.resources.settings_title
import io.github.j3ffx.nuance.resources.settings_updates
import io.github.j3ffx.nuance.resources.settings_updates_help
import io.github.j3ffx.nuance.resources.settings_version
import io.github.j3ffx.nuance.resources.theme_dark
import io.github.j3ffx.nuance.resources.theme_light
import io.github.j3ffx.nuance.resources.theme_system
import io.github.j3ffx.nuance.resources.update_available
import io.github.j3ffx.nuance.resources.update_cancelled
import io.github.j3ffx.nuance.resources.update_closes
import io.github.j3ffx.nuance.resources.update_downloading
import io.github.j3ffx.nuance.resources.update_failed
import io.github.j3ffx.nuance.resources.update_install
import io.github.j3ffx.nuance.resources.update_installed
import io.github.j3ffx.nuance.resources.update_permission
import io.github.j3ffx.nuance.resources.update_up_to_date
import io.github.j3ffx.nuance.resources.update_verifying
import io.github.j3ffx.nuance.resources.update_waiting
import io.github.j3ffx.nuance.resources.update_working
import io.github.j3ffx.nuance.ui.Choices
import io.github.j3ffx.nuance.ui.Page
import io.github.j3ffx.nuance.ui.SectionTitle
import io.github.j3ffx.nuance.ui.SwitchRow
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen(
    preferences: Preferences,
    platform: Platform,
    onHelp: () -> Unit,
    onAbout: () -> Unit,
    onFindProfile: () -> Unit,
    onBack: () -> Unit
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Page(stringResource(Res.string.settings_title), onBack, snackbar) {
        SectionTitle(stringResource(Res.string.settings_language))
        var language by remember { mutableStateOf(platform.appLanguage) }
        Choices(
            listOf(null, "fr", "en"),
            {
                when (it) {
                    null -> stringResource(Res.string.language_phone)

                    // Each language in its own words, so that anyone finds theirs.
                    "fr" -> "Français"

                    else -> "English"
                }
            },
            {
                language = it
                platform.appLanguage = it
            },
            { it == language }
        )

        SectionTitle(stringResource(Res.string.settings_theme))
        Choices(
            ThemeChoice.entries,
            {
                stringResource(
                    when (it) {
                        ThemeChoice.Dark -> Res.string.theme_dark
                        ThemeChoice.Light -> Res.string.theme_light
                        ThemeChoice.System -> Res.string.theme_system
                    }
                )
            },
            { preferences.theme = it },
            { it == preferences.theme }
        )

        SectionTitle(stringResource(Res.string.settings_auto_pause))
        Text(stringResource(Res.string.settings_auto_pause_help), style = MaterialTheme.typography.bodyMedium)
        Choices(
            listOf(0, 1, 2, 5),
            {
                if (it ==
                    0
                ) {
                    stringResource(Res.string.auto_pause_never)
                } else {
                    stringResource(Res.string.auto_pause_minutes, it)
                }
            },
            { preferences.autoPauseMinutes = it },
            { it == preferences.autoPauseMinutes }
        )

        SectionTitle(stringResource(Res.string.settings_capture))
        Choices(
            CaptureFormat.entries,
            {
                stringResource(
                    if (it ==
                        CaptureFormat.Filtered
                    ) {
                        Res.string.capture_filtered
                    } else {
                        Res.string.capture_both
                    }
                )
            },
            { preferences.captureFormat = it },
            { it == preferences.captureFormat }
        )

        SwitchRow(
            stringResource(Res.string.settings_updates),
            stringResource(Res.string.settings_updates_help),
            preferences.updateChecks,
            stringResource(Res.string.on),
            stringResource(Res.string.off)
        ) { preferences.updateChecks = it }
        UpdateRow(preferences, platform)

        LinkRow(stringResource(Res.string.settings_find_profile), onFindProfile)
        LinkRow(stringResource(Res.string.settings_help), onHelp)
        LinkRow(stringResource(Res.string.settings_about), onAbout)
        Text(
            stringResource(Res.string.settings_version, platform.versionName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/**
 * "Check now" (always available), what it found, and "Install" when there is something newer.
 * While installing: the download's progress, then what Android is doing, then how it ended.
 */
@Composable
private fun UpdateRow(preferences: Preferences, platform: Platform) {
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<UpdateProgress?>(null) }
    var outcome by remember {
        mutableStateOf(if (preferences.availableUpdate != null) UpdateOutcome.Available else null)
    }
    val busy = checking || progress != null
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = {
                checking = true
                scope.launch {
                    val latest = platform.latestRelease()
                    val newer = latest?.takeIf { Versions.isNewer(it.version, platform.versionName) }
                    preferences.availableUpdate = newer
                    outcome = when {
                        latest == null -> UpdateOutcome.Failed
                        newer != null -> UpdateOutcome.Available
                        else -> UpdateOutcome.UpToDate
                    }
                    checking = false
                }
            },
            enabled = !busy,
            modifier = Modifier.heightIn(min = 48.dp)
        ) { Text(stringResource(Res.string.check_now)) }
        val update = preferences.availableUpdate
        if (update != null) {
            Button(
                onClick = {
                    progress = UpdateProgress.Downloading(null)
                    scope.launch {
                        // Read at the next launch: Android closes Nuance to update it.
                        preferences.updatingTo = update.version
                        outcome = platform.installRelease(update) { progress = it }
                        if (outcome != UpdateOutcome.Installed) preferences.updatingTo = ""
                        progress = null
                    }
                },
                enabled = !busy,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                if (progress != null) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(Res.string.update_install))
            }
        }
    }
    val step = progress
    if (step is UpdateProgress.Downloading) {
        val fraction = step.fraction
        if (fraction == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        }
    }
    val text = when {
        step is UpdateProgress.Downloading -> stringResource(
            Res.string.update_downloading,
            ((step.fraction ?: 0f) * 100).roundToInt()
        )

        step == UpdateProgress.Verifying -> stringResource(Res.string.update_verifying)

        step == UpdateProgress.WaitingForAndroid -> stringResource(Res.string.update_waiting)

        checking -> stringResource(Res.string.update_working)

        outcome == UpdateOutcome.UpToDate -> stringResource(Res.string.update_up_to_date, platform.versionName)

        // Found here, or by the check at launch, which may land after this screen opened.
        outcome == UpdateOutcome.Available || outcome == null -> preferences.availableUpdate?.let {
            stringResource(Res.string.update_available, it.version) + " " + stringResource(Res.string.update_closes)
        }

        outcome == UpdateOutcome.Installed -> stringResource(Res.string.update_installed)

        outcome == UpdateOutcome.Cancelled -> stringResource(Res.string.update_cancelled)

        outcome == UpdateOutcome.NeedsPermission -> stringResource(Res.string.update_permission)

        outcome == UpdateOutcome.Failed -> stringResource(Res.string.update_failed)

        else -> null
    }
    if (text != null) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}

@Composable
private fun LinkRow(text: String, onClick: () -> Unit) {
    // A chevron says it opens a page: the text colour alone would not.
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick)
            .semantics { role = Role.Button }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

/** The help, in sections whose titles TalkBack announces as headings. */
@Composable
fun HelpScreen(onBack: () -> Unit) {
    val sections = listOf(
        Res.string.help_modes_title to Res.string.help_modes,
        Res.string.help_image_title to Res.string.help_image,
        Res.string.help_photos_title to Res.string.help_photos,
        Res.string.help_profile_title to Res.string.help_profile,
        Res.string.help_privacy_title to Res.string.help_privacy,
        Res.string.help_medical_title to Res.string.help_medical
    )
    Page(stringResource(Res.string.settings_help), onBack) {
        Text(stringResource(Res.string.help_intro), style = MaterialTheme.typography.bodyLarge)
        for ((title, body) in sections) {
            SectionTitle(stringResource(title))
            Text(stringResource(body), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) =
    TextScreen(stringResource(Res.string.settings_about), stringResource(Res.string.about_text), onBack)

@Composable
private fun TextScreen(title: String, text: String, onBack: () -> Unit) {
    Page(title, onBack) {
        Column { Text(text, style = MaterialTheme.typography.bodyLarge) }
    }
}
