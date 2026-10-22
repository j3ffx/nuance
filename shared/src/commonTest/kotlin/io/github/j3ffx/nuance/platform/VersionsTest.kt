package io.github.j3ffx.nuance.platform

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionsTest {
    @Test
    fun comparesNumbersNotText() {
        assertTrue(Versions.isNewer("0.10.0", "0.9.3"))
        assertTrue(Versions.isNewer("v0.2.0", "0.1.10"))
        assertFalse(Versions.isNewer("0.1.0", "0.1.0"))
        assertFalse(Versions.isNewer("0.1.0", "0.2.0"))
    }

    @Test
    fun ignoresSuffixesAndNeverTrustsGarbage() {
        // The dev variant is "0.0.0-dev": any release is newer.
        assertTrue(Versions.isNewer("0.1.0", "0.0.0-dev"))
        assertFalse(Versions.isNewer("latest", "0.1.0"))
        assertFalse(Versions.isNewer("1.2", "0.1.0"))
    }
}
