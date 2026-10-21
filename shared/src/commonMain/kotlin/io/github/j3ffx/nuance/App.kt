package io.github.j3ffx.nuance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.j3ffx.nuance.camera.CameraController
import io.github.j3ffx.nuance.camera.CameraScreen
import io.github.j3ffx.nuance.camera.newProfileId
import io.github.j3ffx.nuance.core.modes.Mode
import io.github.j3ffx.nuance.core.profile.Profile
import io.github.j3ffx.nuance.dev.DevPreview
import io.github.j3ffx.nuance.dev.DevTools
import io.github.j3ffx.nuance.onboarding.OnboardingScreen
import io.github.j3ffx.nuance.platform.Platform
import io.github.j3ffx.nuance.platform.Preferences
import io.github.j3ffx.nuance.profiles.ProfilesScreen
import io.github.j3ffx.nuance.render.CameraRenderer
import io.github.j3ffx.nuance.resources.Res
import io.github.j3ffx.nuance.resources.camera_permission_grant
import io.github.j3ffx.nuance.resources.camera_permission_rationale
import io.github.j3ffx.nuance.settings.AboutScreen
import io.github.j3ffx.nuance.settings.HelpScreen
import io.github.j3ffx.nuance.settings.SettingsScreen
import io.github.j3ffx.nuance.ui.NuanceTheme
import org.jetbrains.compose.resources.stringResource

/** A command from a dev script (Android intent extras), applied once. */
data class DevCommand(
    val mode: String? = null,
    val compare: Int? = null,
    val screen: String? = null,
    val narrow: Boolean? = null,
    val largeText: Boolean? = null
)

private enum class Screen { Camera, Profiles, Settings, Help, About }

/**
 * The whole app, shared by Android and iOS. [camera] draws the platform's camera view, once the
 * camera permission is granted ([onRequestCamera] asks for it).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun App(
    renderer: CameraRenderer,
    platform: Platform,
    devTools: Boolean,
    cameraGranted: Boolean,
    onRequestCamera: () -> Unit,
    devCommand: DevCommand? = null,
    camera: @Composable (Modifier) -> Unit
) {
    val preferences = remember(platform) { Preferences(platform.store) }
    val dev = remember { DevPreview() }
    LaunchedEffect(devCommand) {
        devCommand?.narrow?.let { dev.narrow = it }
        devCommand?.largeText?.let { dev.largeText = it }
    }
    NuanceTheme(preferences.theme) {
        DevFrame(dev) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                if (!preferences.onboardingDone) {
                    OnboardingScreen { profile, updates ->
                        if (profile != Profile.Default) {
                            val mine = profile.copy(id = newProfileId())
                            preferences.saveProfile(mine)
                            preferences.defaultProfileId = mine.id
                        }
                        preferences.updateChecks = updates
                        preferences.onboardingDone = true
                    }
                    return@Surface
                }
                if (!cameraGranted) {
                    CameraPermissionPrompt(onRequestCamera)
                    return@Surface
                }
                Main(renderer, platform, preferences, devTools, dev, devCommand, camera)
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Main(
    renderer: CameraRenderer,
    platform: Platform,
    preferences: Preferences,
    devTools: Boolean,
    dev: DevPreview,
    devCommand: DevCommand?,
    camera: @Composable (Modifier) -> Unit
) {
    val scope = rememberCoroutineScope()
    val controller = remember(renderer) {
        CameraController(renderer, scope, preferences.profile(preferences.defaultProfileId) ?: Profile.Default)
    }
    var screen by remember { mutableStateOf(Screen.Camera) }
    LaunchedEffect(devCommand) {
        val command = devCommand ?: return@LaunchedEffect
        command.mode?.let { name -> Mode.entries.firstOrNull { it.name.equals(name, true) } }
            ?.let { m -> controller.update { it.copy(mode = m) } }
        command.compare?.takeIf { it == 2 || it == 4 }?.let { n -> controller.update { it.copy(compareCount = n) } }
        command.screen?.let { name -> Screen.entries.firstOrNull { it.name.equals(name, true) } }?.let { screen = it }
    }
    BackHandler(enabled = screen != Screen.Camera) {
        screen = if (screen == Screen.Help || screen == Screen.About) Screen.Settings else Screen.Camera
    }
    when (screen) {
        Screen.Camera -> Box(Modifier.fillMaxSize()) {
            CameraScreen(
                controller,
                preferences,
                onOpenSettings = { screen = Screen.Settings },
                onManageProfiles = { screen = Screen.Profiles },
                camera = camera
            )
            if (devTools) DevTools(renderer, dev, Modifier.align(Alignment.TopCenter).padding(top = 64.dp))
        }

        Screen.Profiles -> ProfilesScreen(
            preferences,
            platform,
            controller.state.profileId,
            onUse = {
                controller.applyProfile(it)
                screen = Screen.Camera
            },
            onBack = { screen = Screen.Camera }
        )

        Screen.Settings -> SettingsScreen(
            preferences,
            platform,
            onHelp = { screen = Screen.Help },
            onAbout = { screen = Screen.About },
            onBack = { screen = Screen.Camera }
        )

        Screen.Help -> HelpScreen(onBack = { screen = Screen.Settings })

        Screen.About -> AboutScreen(onBack = { screen = Screen.Settings })
    }
}

/** Dev variant: shows the app 360 dp wide and with larger text, to check that nothing overflows. */
@Composable
private fun DevFrame(dev: DevPreview, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val scaled = if (dev.largeText) Density(density.density, density.fontScale * 1.3f) else density
    CompositionLocalProvider(LocalDensity provides scaled) {
        if (dev.narrow) {
            Box(
                Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.width(360.dp).fillMaxHeight()) { content() }
            }
        } else {
            content()
        }
    }
}

@Composable
private fun CameraPermissionPrompt(onRequestCamera: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(Res.string.camera_permission_rationale), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onRequestCamera) { Text(stringResource(Res.string.camera_permission_grant)) }
        }
    }
}
