package io.github.j3ffx.nuance.platform

import io.github.j3ffx.nuance.core.correction.CorrectionStrategy
import io.github.j3ffx.nuance.core.cvd.Axis
import io.github.j3ffx.nuance.core.profile.Profile
import io.github.j3ffx.nuance.core.profile.ProfileCodec
import kotlin.test.Test
import kotlin.test.assertEquals

class PreferencesTest {
    private fun storeWith(vararg profiles: Profile) = MemoryStore().apply {
        putString("profiles", ProfileCodec.encodeAll(profiles.toList()))
    }

    @Test
    fun savedProfilesMoveOnceFromTheFormerDefaultMethod() {
        val store = storeWith(
            Profile("p1", "Mon test", Axis.Protan, 100, strategyId = CorrectionStrategy.FORMER_DEFAULT_ID)
        )
        assertEquals(CorrectionStrategy.Default.id, Preferences(store).profiles.single().strategyId)

        // Picked again on purpose afterwards: kept.
        val prefs = Preferences(store)
        prefs.saveProfile(prefs.profiles.single().copy(strategyId = CorrectionStrategy.FORMER_DEFAULT_ID))
        assertEquals(CorrectionStrategy.FORMER_DEFAULT_ID, Preferences(store).profiles.single().strategyId)
    }

    @Test
    fun theNewDefaultIsTheRemap() {
        assertEquals("lab-remap", CorrectionStrategy.Default.id)
        assertEquals("lab-remap", Profile.Default.strategyId)
    }
}
