package io.github.j3ffx.nuance.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.j3ffx.nuance.platform.CaptureFormat
import io.github.j3ffx.nuance.platform.Platform
import io.github.j3ffx.nuance.platform.Preferences
import io.github.j3ffx.nuance.platform.ThemeChoice
import io.github.j3ffx.nuance.resources.Res
import io.github.j3ffx.nuance.resources.about_text
import io.github.j3ffx.nuance.resources.auto_pause_minutes
import io.github.j3ffx.nuance.resources.auto_pause_never
import io.github.j3ffx.nuance.resources.capture_both
import io.github.j3ffx.nuance.resources.capture_filtered
import io.github.j3ffx.nuance.resources.check_now
import io.github.j3ffx.nuance.resources.check_soon
import io.github.j3ffx.nuance.resources.help_text
import io.github.j3ffx.nuance.resources.off
import io.github.j3ffx.nuance.resources.on
import io.github.j3ffx.nuance.resources.settings_about
import io.github.j3ffx.nuance.resources.settings_auto_pause
import io.github.j3ffx.nuance.resources.settings_auto_pause_help
import io.github.j3ffx.nuance.resources.settings_capture
import io.github.j3ffx.nuance.resources.settings_help
import io.github.j3ffx.nuance.resources.settings_language
import io.github.j3ffx.nuance.resources.settings_language_value
import io.github.j3ffx.nuance.resources.settings_theme
import io.github.j3ffx.nuance.resources.settings_title
import io.github.j3ffx.nuance.resources.settings_updates
import io.github.j3ffx.nuance.resources.settings_updates_help
import io.github.j3ffx.nuance.resources.settings_version
import io.github.j3ffx.nuance.resources.theme_dark
import io.github.j3ffx.nuance.resources.theme_light
import io.github.j3ffx.nuance.resources.theme_system
import io.github.j3ffx.nuance.ui.Choices
import io.github.j3ffx.nuance.ui.Page
import io.github.j3ffx.nuance.ui.SectionTitle
import io.github.j3ffx.nuance.ui.SwitchRow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen(
    preferences: Preferences,
    platform: Platform,
    onHelp: () -> Unit,
    onAbout: () -> Unit,
    onBack: () -> Unit
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Page(stringResource(Res.string.settings_title), onBack, snackbar) {
        SectionTitle(stringResource(Res.string.settings_language))
        LinkRow(stringResource(Res.string.settings_language_value)) { platform.openLanguageSettings() }

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
        OutlinedButton(
            onClick = { scope.launch { snackbar.showSnackbar(getString(Res.string.check_soon)) } },
            modifier = Modifier.heightIn(min = 48.dp)
        ) { Text(stringResource(Res.string.check_now)) }

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

@Composable
private fun LinkRow(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick)
            .semantics { role = Role.Button }.padding(vertical = 12.dp)
    )
}

@Composable
fun HelpScreen(onBack: () -> Unit) =
    TextScreen(stringResource(Res.string.settings_help), stringResource(Res.string.help_text), onBack)

@Composable
fun AboutScreen(onBack: () -> Unit) =
    TextScreen(stringResource(Res.string.settings_about), stringResource(Res.string.about_text), onBack)

@Composable
private fun TextScreen(title: String, text: String, onBack: () -> Unit) {
    Page(title, onBack) {
        Column { Text(text, style = MaterialTheme.typography.bodyLarge) }
    }
}
