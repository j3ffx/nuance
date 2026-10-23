package io.github.j3ffx.nuance.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.j3ffx.nuance.core.profile.Profile
import io.github.j3ffx.nuance.profiles.VisionEditor
import io.github.j3ffx.nuance.resources.Res
import io.github.j3ffx.nuance.resources.onb_compare
import io.github.j3ffx.nuance.resources.onb_correct
import io.github.j3ffx.nuance.resources.onb_dont_know
import io.github.j3ffx.nuance.resources.onb_dont_know_help
import io.github.j3ffx.nuance.resources.onb_intro
import io.github.j3ffx.nuance.resources.onb_isolate
import io.github.j3ffx.nuance.resources.onb_know
import io.github.j3ffx.nuance.resources.onb_not_medical
import io.github.j3ffx.nuance.resources.onb_private
import io.github.j3ffx.nuance.resources.onb_profile
import io.github.j3ffx.nuance.resources.onb_simulate
import io.github.j3ffx.nuance.resources.onb_start
import io.github.j3ffx.nuance.resources.onb_take_test
import io.github.j3ffx.nuance.resources.onb_title
import io.github.j3ffx.nuance.resources.onb_updates
import io.github.j3ffx.nuance.resources.settings_updates_help
import io.github.j3ffx.nuance.ui.Choices
import io.github.j3ffx.nuance.ui.SectionTitle
import org.jetbrains.compose.resources.stringResource

/**
 * One short page: what Nuance does and doesn't, the person's type (or "I don't know"), and the
 * update check, offered unticked. [onDone] gets the chosen profile, the update choice, and
 * whether to take "Find my profile" right away.
 */
@Composable
fun OnboardingScreen(onDone: (Profile, Boolean, Boolean) -> Unit) {
    // "I don't know" is picked to start with: "Start" is never greyed out without a reason.
    var knows by remember { mutableStateOf(false) }
    var severities by remember { mutableStateOf(listOf(0, 60, 0)) }
    var updates by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(Res.string.onb_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics {
                    heading()
                }
            )
            Text(stringResource(Res.string.onb_intro), style = MaterialTheme.typography.bodyLarge)
            for (line in listOf(
                Res.string.onb_correct,
                Res.string.onb_isolate,
                Res.string.onb_simulate,
                Res.string.onb_compare
            )) {
                Text("•  " + stringResource(line), style = MaterialTheme.typography.bodyLarge)
            }
            Text(stringResource(Res.string.onb_not_medical), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(Res.string.onb_private), style = MaterialTheme.typography.bodyMedium)

            SectionTitle(stringResource(Res.string.onb_profile))
            Choices(
                listOf(true, false),
                { stringResource(if (it) Res.string.onb_know else Res.string.onb_dont_know) },
                { knows = it },
                { it == knows }
            )
            if (knows) {
                VisionEditor(severities, { severities = it })
            } else {
                Text(stringResource(Res.string.onb_dont_know_help), style = MaterialTheme.typography.bodyMedium)
            }

            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(updates, role = Role.Checkbox) {
                    updates = it
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = updates, onCheckedChange = null)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(stringResource(Res.string.onb_updates), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(Res.string.settings_updates_help), style = MaterialTheme.typography.bodyMedium)
                }
            }

            val start = {
                val profile = if (knows) {
                    Profile.Default.copy(protan = severities[0], deutan = severities[1], tritan = severities[2])
                } else {
                    Profile.Default
                }
                onDone(profile, updates, false)
            }
            if (knows) {
                Button(onClick = start, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(Res.string.onb_start))
                }
            } else {
                // Not knowing one's type: the test is the main way forward.
                Button(
                    onClick = { onDone(Profile.Default, updates, true) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) { Text(stringResource(Res.string.onb_take_test)) }
                OutlinedButton(onClick = start, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(Res.string.onb_start))
                }
            }
        }
    }
}
