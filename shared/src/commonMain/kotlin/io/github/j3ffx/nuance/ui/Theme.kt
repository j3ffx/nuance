package io.github.j3ffx.nuance.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.j3ffx.nuance.platform.ThemeChoice

/**
 * Nuance's own palette, never the phone's dynamic colours (Material You can produce pairs a
 * colour-blind person can't read). High contrast, amber accent: amber stays bright and distinct
 * for every axis. A test (ThemeTest) checks every text/background pair at 4.5:1 or more, for typical
 * vision and through the simulation of the three axes. States are never told by colour alone.
 */
object NuanceColors {
    val dark: ColorScheme = darkColorScheme(
        primary = Color(0xFFFFD54A),
        onPrimary = Color(0xFF1A1400),
        primaryContainer = Color(0xFF4A3A00),
        onPrimaryContainer = Color(0xFFFFEFB8),
        secondary = Color(0xFF9CCFFF),
        onSecondary = Color(0xFF00213A),
        secondaryContainer = Color(0xFF233A4F),
        onSecondaryContainer = Color(0xFFE0EEFF),
        background = Color(0xFF0B0E11),
        onBackground = Color(0xFFF1F4F6),
        surface = Color(0xFF12161A),
        onSurface = Color(0xFFF1F4F6),
        surfaceVariant = Color(0xFF232A30),
        onSurfaceVariant = Color(0xFFC9D1D7),
        surfaceContainer = Color(0xFF1A1F24),
        surfaceContainerHigh = Color(0xFF20262C),
        surfaceContainerHighest = Color(0xFF272E35),
        surfaceContainerLow = Color(0xFF15191D),
        surfaceContainerLowest = Color(0xFF0B0E11),
        outline = Color(0xFF8D979F),
        outlineVariant = Color(0xFF3D464D),
        error = Color(0xFFFF9C94),
        onError = Color(0xFF3A0000),
        inverseSurface = Color(0xFFF1F4F6),
        inverseOnSurface = Color(0xFF12161A)
    )

    val light: ColorScheme = lightColorScheme(
        primary = Color(0xFF6B4500),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFE08A),
        onPrimaryContainer = Color(0xFF241800),
        secondary = Color(0xFF00538F),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD3E6FF),
        onSecondaryContainer = Color(0xFF001C33),
        background = Color(0xFFF8F9FA),
        onBackground = Color(0xFF101417),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF101417),
        surfaceVariant = Color(0xFFE6EAED),
        onSurfaceVariant = Color(0xFF3A4349),
        surfaceContainer = Color(0xFFF0F2F4),
        surfaceContainerHigh = Color(0xFFE9ECEF),
        surfaceContainerHighest = Color(0xFFE2E6E9),
        surfaceContainerLow = Color(0xFFF5F7F8),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        outline = Color(0xFF5C666E),
        outlineVariant = Color(0xFFC3CAD0),
        error = Color(0xFFB3261E),
        onError = Color(0xFFFFFFFF),
        inverseSurface = Color(0xFF263038),
        inverseOnSurface = Color(0xFFF1F4F6)
    )

    /** Text over the camera image: on this scrim, whatever the scene. */
    val scrim = Color(0xD9000000)
    val onScrim = Color(0xFFFFFFFF)
}

@Composable
fun NuanceTheme(choice: ThemeChoice, content: @Composable () -> Unit) {
    val dark = when (choice) {
        ThemeChoice.Dark -> true
        ThemeChoice.Light -> false
        ThemeChoice.System -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) NuanceColors.dark else NuanceColors.light, content = content)
}
