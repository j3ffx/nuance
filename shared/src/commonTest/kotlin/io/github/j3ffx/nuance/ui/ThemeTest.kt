package io.github.j3ffx.nuance.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import io.github.j3ffx.nuance.core.Rgb
import io.github.j3ffx.nuance.core.color.Srgb
import io.github.j3ffx.nuance.core.cvd.Axis
import io.github.j3ffx.nuance.core.cvd.CvdProfile
import io.github.j3ffx.nuance.core.cvd.Simulation
import kotlin.test.Test
import kotlin.test.assertTrue

class ThemeTest {
    private fun rgb(c: Color) = Rgb(c.red, c.green, c.blue)

    /** WCAG 2 contrast ratio. */
    private fun contrast(a: Rgb, b: Rgb): Double {
        fun luminance(c: Rgb) = Srgb.toLinear(c).let { 0.2126 * it.x + 0.7152 * it.y + 0.0722 * it.z }
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun pairs(s: ColorScheme) = mapOf(
        "onPrimary/primary" to (s.onPrimary to s.primary),
        "onPrimaryContainer/primaryContainer" to (s.onPrimaryContainer to s.primaryContainer),
        "onSecondary/secondary" to (s.onSecondary to s.secondary),
        "onSecondaryContainer/secondaryContainer" to (s.onSecondaryContainer to s.secondaryContainer),
        "onBackground/background" to (s.onBackground to s.background),
        "onSurface/surface" to (s.onSurface to s.surface),
        "onSurfaceVariant/surface" to (s.onSurfaceVariant to s.surface),
        "onSurfaceVariant/surfaceVariant" to (s.onSurfaceVariant to s.surfaceVariant),
        "onSurface/surfaceContainerHighest" to (s.onSurface to s.surfaceContainerHighest),
        "primary/surface" to (s.primary to s.surface),
        "primary/background" to (s.primary to s.background),
        "secondary/surface" to (s.secondary to s.surface),
        "error/surface" to (s.error to s.surface),
        "onError/error" to (s.onError to s.error),
        "inverseOnSurface/inverseSurface" to (s.inverseOnSurface to s.inverseSurface),
        "onScrim/scrim over black" to (NuanceColors.onScrim to Color.Black)
    )

    @Test
    fun everyTextPairReadsForEveryone() {
        val viewers = listOf<Pair<String, (Rgb) -> Rgb>>("typical" to { it }) +
            Axis.entries.map { axis -> axis.name to Simulation.rgb(CvdProfile(axis, 1.0)) }
        val problems = mutableListOf<String>()
        for ((schemeName, scheme) in listOf("dark" to NuanceColors.dark, "light" to NuanceColors.light)) {
            for ((pairName, pair) in pairs(scheme)) {
                for ((viewer, see) in viewers) {
                    val ratio = contrast(see(rgb(pair.first)), see(rgb(pair.second)))
                    if (ratio < 4.5) problems += "$schemeName $pairName seen $viewer: ${(ratio * 100).toInt() / 100.0}"
                }
            }
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    @Test
    fun outlinesStayVisibleAgainstTheSurface() {
        // Non-text UI (chip and field borders) needs 3:1 (WCAG 1.4.11).
        for (scheme in listOf(NuanceColors.dark, NuanceColors.light)) {
            assertTrue(contrast(rgb(scheme.outline), rgb(scheme.surface)) >= 3.0)
        }
    }
}
