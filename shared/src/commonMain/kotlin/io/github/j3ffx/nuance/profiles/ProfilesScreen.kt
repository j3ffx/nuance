package io.github.j3ffx.nuance.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.j3ffx.nuance.camera.newProfileId
import io.github.j3ffx.nuance.core.cvd.Axis
import io.github.j3ffx.nuance.core.profile.Profile
import io.github.j3ffx.nuance.core.profile.ProfileCodec
import io.github.j3ffx.nuance.platform.Platform
import io.github.j3ffx.nuance.platform.Preferences
import io.github.j3ffx.nuance.resources.Res
import io.github.j3ffx.nuance.resources.cancel
import io.github.j3ffx.nuance.resources.import_field
import io.github.j3ffx.nuance.resources.import_help
import io.github.j3ffx.nuance.resources.import_invalid
import io.github.j3ffx.nuance.resources.import_paste
import io.github.j3ffx.nuance.resources.import_title
import io.github.j3ffx.nuance.resources.imported
import io.github.j3ffx.nuance.resources.profile_actions
import io.github.j3ffx.nuance.resources.profile_delete
import io.github.j3ffx.nuance.resources.profile_delete_confirm
import io.github.j3ffx.nuance.resources.profile_edit
import io.github.j3ffx.nuance.resources.profile_edit_title
import io.github.j3ffx.nuance.resources.profile_import
import io.github.j3ffx.nuance.resources.profile_in_use
import io.github.j3ffx.nuance.resources.profile_name
import io.github.j3ffx.nuance.resources.profile_name_default
import io.github.j3ffx.nuance.resources.profile_new
import io.github.j3ffx.nuance.resources.profile_share
import io.github.j3ffx.nuance.resources.profiles_empty
import io.github.j3ffx.nuance.resources.profiles_mine
import io.github.j3ffx.nuance.resources.profiles_presets
import io.github.j3ffx.nuance.resources.profiles_title
import io.github.j3ffx.nuance.resources.save
import io.github.j3ffx.nuance.resources.screening_last
import io.github.j3ffx.nuance.resources.screening_last_none
import io.github.j3ffx.nuance.resources.screening_title
import io.github.j3ffx.nuance.resources.share_title
import io.github.j3ffx.nuance.screening.ScreeningSummary
import io.github.j3ffx.nuance.ui.Page
import io.github.j3ffx.nuance.ui.SectionTitle
import io.github.j3ffx.nuance.ui.profileName
import io.github.j3ffx.nuance.ui.profileSummary
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProfilesScreen(
    preferences: Preferences,
    platform: Platform,
    activeProfileId: String,
    onUse: (Profile) -> Unit,
    onEdited: (Profile) -> Unit,
    onFindProfile: () -> Unit,
    onBack: () -> Unit
) {
    var editing by remember { mutableStateOf<Profile?>(null) }
    var deleting by remember { mutableStateOf<Profile?>(null) }
    var importing by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val shareTitle = stringResource(Res.string.share_title)
    val defaultName = stringResource(Res.string.profile_name_default)

    Page(stringResource(Res.string.profiles_title), onBack, snackbar) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                editing = Profile(newProfileId(), defaultName, Axis.Deutan, 60)
            }, modifier = Modifier) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.profile_new), modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = { importing = true }, modifier = Modifier) {
                Text(stringResource(Res.string.profile_import))
            }
            OutlinedButton(onClick = onFindProfile, modifier = Modifier) {
                Text(stringResource(Res.string.screening_title))
            }
        }
        LastScreening(preferences.lastScreening)

        SectionTitle(stringResource(Res.string.profiles_mine))
        if (preferences.profiles.isEmpty()) {
            Text(stringResource(Res.string.profiles_empty), style = MaterialTheme.typography.bodyMedium)
        }
        for (p in preferences.profiles) {
            ProfileRow(
                p,
                inUse = p.id == activeProfileId,
                onUse = { onUse(p) },
                onEdit = { editing = p },
                onShare = { platform.shareText(ProfileCodec.encode(p), shareTitle) },
                onDelete = { deleting = p }
            )
        }

        SectionTitle(stringResource(Res.string.profiles_presets))
        for (p in Profile.builtIns) {
            ProfileRow(
                p,
                inUse = p.id == activeProfileId,
                onUse = { onUse(p) },
                onEdit = null,
                onShare = { platform.shareText(ProfileCodec.encode(p), shareTitle) },
                onDelete = null
            )
        }
    }

    editing?.let { p ->
        ProfileEditor(p, onSave = {
            preferences.saveProfile(it)
            onEdited(it)
            editing = null
        }, onCancel = { editing = null })
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(Res.string.profile_delete_confirm, profileName(p))) },
            confirmButton = {
                TextButton(onClick = {
                    preferences.deleteProfile(p.id)
                    deleting = null
                }) { Text(stringResource(Res.string.profile_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(Res.string.cancel)) } }
        )
    }
    if (importing) {
        ImportDialog(onImport = { p ->
            // A new id, so importing twice or importing one's own profile never overwrites anything.
            preferences.saveProfile(p.copy(id = newProfileId()))
            importing = false
            scope.launch { snackbar.showSnackbar(getString(Res.string.imported)) }
        }, onCancel = { importing = false })
    }
}

/**
 * One profile: a tap uses it; the one in use is checked and says so in words. Edit, share and
 * delete wait behind "⋮", so the list stays a list.
 */
@Composable
private fun ProfileRow(
    p: Profile,
    inUse: Boolean,
    onUse: () -> Unit,
    onEdit: (() -> Unit)?,
    onShare: () -> Unit,
    onDelete: (() -> Unit)?
) {
    var menu by remember { mutableStateOf(false) }
    val summary = if (p.name.isNotEmpty()) profileSummary(p) else null
    val state = if (inUse) stringResource(Res.string.profile_in_use) else null
    ListItem(
        headlineContent = {
            Text(profileName(p), fontWeight = if (inUse) FontWeight.Bold else FontWeight.Normal)
        },
        supportingContent = listOfNotNull(state, summary).takeIf { it.isNotEmpty() }?.let { lines ->
            { Text(lines.joinToString(" · ")) }
        },
        leadingContent = {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = if (inUse) MaterialTheme.colorScheme.primary else Color.Transparent
            )
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(Res.string.profile_actions, profileName(p))
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (onEdit != null) {
                        DropdownMenuItem(text = { Text(stringResource(Res.string.profile_edit)) }, onClick = {
                            menu = false
                            onEdit()
                        })
                    }
                    DropdownMenuItem(text = { Text(stringResource(Res.string.profile_share)) }, onClick = {
                        menu = false
                        onShare()
                    })
                    if (onDelete != null) {
                        DropdownMenuItem(text = { Text(stringResource(Res.string.profile_delete)) }, onClick = {
                            menu = false
                            onDelete()
                        })
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .selectable(inUse, role = Role.RadioButton, onClick = onUse)
    )
}

/** Name and severities; the camera settings are saved from the camera (the profile sheet). */
@Composable
private fun ProfileEditor(initial: Profile, onSave: (Profile) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var severities by remember { mutableStateOf(listOf(initial.protan, initial.deutan, initial.tritan)) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(Res.string.profile_edit_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, {
                    name = it
                }, label = { Text(stringResource(Res.string.profile_name)) }, singleLine = true)
                VisionEditor(severities, { severities = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    initial.copy(
                        name = name.trim(),
                        protan = severities[0],
                        deutan = severities[1],
                        tritan = severities[2]
                    )
                )
            }) {
                Text(stringResource(Res.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(Res.string.cancel)) } }
    )
}

@Composable
private fun ImportDialog(onImport: (Profile) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }

    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(Res.string.import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.import_help))
                OutlinedTextField(text, {
                    text = it
                    invalid = false
                }, label = { Text(stringResource(Res.string.import_field)) }, minLines = 3, isError = invalid)
                if (invalid) Text(stringResource(Res.string.import_invalid), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = {
                    clipboard.getText()?.let { text = it.text }
                }, modifier = Modifier) {
                    Text(stringResource(Res.string.import_paste))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val p = ProfileCodec.decode(text)
                if (p == null) invalid = true else onImport(p)
            }) { Text(stringResource(Res.string.profile_import)) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(Res.string.cancel)) } }
    )
}

/** The last "Find my profile" result, under the button that takes it again. */
@Composable
private fun LastScreening(stored: String) {
    Text(lastScreeningText(stored) ?: return, style = MaterialTheme.typography.bodyMedium)
}

/** "Dernier test : Protan 100 %", or null when the test was never taken. */
@Composable
internal fun lastScreeningText(stored: String): String? {
    val last = ScreeningSummary.decode(stored) ?: return null
    return if (last.axis == null) {
        stringResource(Res.string.screening_last_none)
    } else {
        stringResource(Res.string.screening_last, profileSummary(last.axis, last.severity))
    }
}
