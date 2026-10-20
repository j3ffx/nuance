package io.github.j3ffx.nuance.core.modes

import io.github.j3ffx.nuance.core.Rgb
import io.github.j3ffx.nuance.core.color.Oklab
import io.github.j3ffx.nuance.core.color.Srgb
import io.github.j3ffx.nuance.core.color.deltaE2000
import io.github.j3ffx.nuance.core.cvd.Axis
import io.github.j3ffx.nuance.core.cvd.CvdProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModesTest {
    private val settings = Settings(CvdProfile(Axis.Deutan, 0.8))

    private fun chroma(c: Rgb) = Oklab.toLch(Oklab.fromLinear(Srgb.toLinear(c))).y

    @Test
    fun isolatingRedKeepsRedsAndGreysTheRest() {
        val f = ColorFunction.rgb(ColorFunction.isolation(settings.copy(isolateHue = HuePreset.Red.hue)))
        assertTrue(chroma(f(Rgb(0.9f, 0.1f, 0.1f))) > 0.15, "red keeps its colour")
        assertTrue(chroma(f(Rgb(0.1f, 0.7f, 0.2f))) < 0.005, "green turns grey")
        assertTrue(chroma(f(Rgb(0.1f, 0.2f, 0.9f))) < 0.005, "blue turns grey")
        assertTrue(chroma(f(Rgb(0.5f, 0.5f, 0.5f))) < 0.005, "grey stays grey")
    }

    @Test
    fun isolationKeepsLightness() {
        val f = ColorFunction.rgb(ColorFunction.isolation(settings.copy(isolateHue = HuePreset.Blue.hue)))
        val green = Rgb(0.2f, 0.6f, 0.3f)
        val lightness = { c: Rgb -> Oklab.fromLinear(Srgb.toLinear(c)).x }
        assertEquals(lightness(green), lightness(f(green)), 0.01)
    }

    @Test
    fun everyPresetIsolatesItsOwnSwatch() {
        for (preset in HuePreset.entries) {
            val f = ColorFunction.rgb(ColorFunction.isolation(settings.copy(isolateHue = preset.hue)))
            assertTrue(chroma(f(preset.swatch)) > 0.05, "$preset swatch kept")
            // A swatch at least 90° away is greyed out.
            val far = HuePreset.entries.filter {
                val d = kotlin.math.abs(it.hue - preset.hue).let { x -> if (x > 180) 360 - x else x }
                d > 90
            }
            for (other in far) assertTrue(chroma(f(other.swatch)) < 0.005, "$preset greys $other")
        }
    }

    @Test
    fun enhancementAtZeroIsTheIdentityAndKeepsGreysNeutral() {
        val c = Srgb.toLinear(Rgb(0.3f, 0.6f, 0.2f))
        val same = ColorFunction.enhancement(0.0, 0.0)(c)
        assertEquals(c, same)
        val f = ColorFunction.rgb(ColorFunction.enhancement(0.5, 0.5))
        val gray = f(Rgb(0.4f, 0.4f, 0.4f))
        assertTrue(chroma(gray) < 0.001, "grey stays neutral")
        assertTrue(chroma(f(Rgb(0.6f, 0.4f, 0.3f))) > chroma(Rgb(0.6f, 0.4f, 0.3f)), "saturation rises")
    }

    @Test
    fun comparerHasFourViewsStartingWithTheRealImage() {
        val views = ColorFunction.comparer(settings).map(ColorFunction::rgb)
        assertEquals(4, views.size)
        val c = Rgb(0.8f, 0.3f, 0.2f)
        assertTrue(deltaE2000(c, views[0](c)) < 0.01, "view 1 is the real image")
        assertTrue(deltaE2000(views[1](c), views[0](c)) > 1, "view 2 is simulated")
    }
}
