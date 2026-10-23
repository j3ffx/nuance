package io.github.j3ffx.nuance.core.profile

import io.github.j3ffx.nuance.core.cvd.Axis
import io.github.j3ffx.nuance.core.modes.Mode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileTest {
    private val sample = Profile(
        id = "p1",
        name = "Pour Léa; 60 % = bien",
        axis = Axis.Protan,
        severity = 60,
        mode = Mode.Compare,
        intensity = 80,
        strategyId = "lab-remap",
        contrast = 20,
        saturation = 10,
        isolateHue = 142,
        isolateWidth = 25
    )

    @Test
    fun encodingRoundTrips() {
        assertEquals(sample, ProfileCodec.decode(ProfileCodec.encode(sample)))
        for (p in Profile.presets + Profile.Default) assertEquals(p, ProfileCodec.decode(ProfileCodec.encode(p)))
    }

    @Test
    fun theTextStaysShortAndOnOneLine() {
        val text = ProfileCodec.encode(sample)
        assertTrue(text.startsWith("nuance1;"))
        assertTrue('\n' !in text && text.length < 160, text)
    }

    @Test
    fun aListRoundTripsAndSkipsGarbage() {
        val list = listOf(sample, Profile.Default.copy(id = "p2", name = "Moi"))
        val text = ProfileCodec.encodeAll(list) + "\nnot a profile\n\n"
        assertEquals(list, ProfileCodec.decodeAll(text))
    }

    @Test
    fun missingKeysTakeDefaultsAndUnknownOnesAreIgnored() {
        val p = ProfileCodec.decode("nuance1;a=t;s=40;future=42")
        assertEquals(Axis.Tritan, p?.axis)
        assertEquals(40, p?.severity)
        assertEquals(Mode.Correct, p?.mode)
        assertEquals(100, p?.intensity)
    }

    @Test
    fun invalidTextIsRejected() {
        assertNull(ProfileCodec.decode("hello"))
        assertNull(ProfileCodec.decode("nuance1;a=x;s=40"), "unknown axis")
        assertNull(ProfileCodec.decode("nuance1;a=d"), "no severity")
        assertNull(ProfileCodec.decode("nuance1;a=d;s=140"), "severity out of range")
        assertNull(ProfileCodec.decode("nuance1;a=d;s=abc"), "not a number")
    }

    @Test
    fun presetsCoverEveryAxisMediumAndTotal() {
        assertEquals(6, Profile.presets.size)
        for (axis in Axis.entries) {
            assertEquals(listOf(50, 100), Profile.presets.filter { it.axis == axis }.map { it.severity })
        }
        assertTrue(Profile.presets.all { it.isPreset })
    }

    @Test
    fun settingsRoundTripThroughTheProfile() {
        val back = sample.with(sample.toSettings(), sample.mode)
        assertEquals(sample, back)
    }

    @Test
    fun builtInsStartWithTheDefaultAndAreAllPresets() {
        assertEquals(Profile.Default, Profile.builtIns.first())
        assertEquals(Profile.presets, Profile.builtIns.drop(1))
        assertTrue(Profile.builtIns.all { it.isPreset })
    }

    @Test
    fun onlyAChangeThatWouldBeSavedCountsAsModified() {
        val s = sample.toSettings()
        assertFalse(sample.isModifiedBy(s))
        assertFalse(sample.isModifiedBy(s.copy(intensity = s.intensity + 0.001)), "below a whole percent")
        assertTrue(sample.isModifiedBy(s.copy(contrast = s.contrast + 0.05)))
        assertTrue(sample.isModifiedBy(s.copy(profile = s.profile.scaledTo(0.9))))
        assertFalse(sample.copy(mode = Mode.Isolate).isModifiedBy(s), "the mode is not a setting")
    }

    @Test
    fun aMixedProfileRoundTripsAndOlderAppsReadItsMainAxis() {
        val mix = Profile("p3", "Moi", protan = 100, deutan = 0, tritan = 30)
        val text = ProfileCodec.encode(mix)
        assertEquals(mix, ProfileCodec.decode(text))
        // 0.1.x reads only a= and s=: the main axis, at its severity.
        assertTrue(";a=p;s=100;" in text, text)
    }

    @Test
    fun profilesWrittenBy01xStillRead() {
        val p = ProfileCodec.decode("nuance1;id=x;n=Ancien;a=t;s=40;m=s")!!
        assertEquals(listOf(0, 0, 40), listOf(p.protan, p.deutan, p.tritan))
        assertEquals(Axis.Tritan, p.axis)
        assertEquals(40, p.severity)
    }

    @Test
    fun theMainAxisIsTheStrongest() {
        val mix = Profile("p4", "", protan = 20, deutan = 0, tritan = 70)
        assertEquals(Axis.Tritan, mix.axis)
        assertEquals(70, mix.severity)
        assertTrue(mix.cvd.isMixed)
    }
}
