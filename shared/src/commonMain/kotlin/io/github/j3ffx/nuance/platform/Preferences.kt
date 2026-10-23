package io.github.j3ffx.nuance.platform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.j3ffx.nuance.core.correction.CorrectionStrategy
import io.github.j3ffx.nuance.core.profile.Profile
import io.github.j3ffx.nuance.core.profile.ProfileCodec

/** The app's colour theme. Dark by default: the camera image stays the brightest thing on screen. */
enum class ThemeChoice { Dark, Light, System }

/** What photos and videos keep: the filtered image only, or the original too. */
enum class CaptureFormat { Filtered, FilteredAndOriginal }

/**
 * Everything the app remembers, on the phone only, as Compose state: the UI follows each change,
 * and each change is written at once.
 */
class Preferences(private val store: KeyValueStore) {
    private companion object {
        const val STRATEGY_MIGRATED = "strategyMigrated"
    }

    var onboardingDone by stored("onboardingDone", false)
    var theme by storedEnum("theme", ThemeChoice.Dark)

    /** Off by default; offered (unticked) at first launch. The only network call of the app. */
    var updateChecks by stored("updateChecks", false)

    /** A newer version found by a check, until it is installed; not stored. */
    var availableUpdate by mutableStateOf<ReleaseInfo?>(null)

    /**
     * The version being installed: Android closes Nuance to update it, so the next launch reads
     * this to say "up to date" ([justUpdated]).
     */
    var updatingTo by storedString("updatingTo", "")

    /** Set at launch when the previous run installed this very version; not stored. */
    var justUpdated by mutableStateOf<String?>(null)

    /**
     * Set by the first tap on one of the camera's side buttons: until then their names are
     * written beside them (icons alone were not understood).
     */
    var cameraButtonsLearned by stored("cameraButtonsLearned", false)

    /** Set by the first use of the pipette: until then a hint over the image says how. */
    var pipetteUsed by stored("pipetteUsed", false)

    /** Minutes without touch nor movement before the camera pauses; 0 = never. */
    var autoPauseMinutes by storedInt("autoPauseMinutes", 2)

    /** The original too by default: a filtered capture can't be put through another mode later. */
    var captureFormat by storedEnum("captureFormat", CaptureFormat.FilteredAndOriginal)

    /** The person's own profiles; built-in ones come from [Profile.builtIns] and are never stored. */
    var profiles: List<Profile> by mutableStateOf(ProfileCodec.decodeAll(store.getString("profiles").orEmpty()))
        private set

    init {
        // 0.1.x saved every profile with error redistribution, the default then, never picked on
        // purpose (it was preselected): once, they move to the new default.
        if (store.getString(STRATEGY_MIGRATED) == null) {
            val moved = profiles.map {
                if (it.strategyId == CorrectionStrategy.FORMER_DEFAULT_ID) {
                    it.copy(strategyId = CorrectionStrategy.Default.id)
                } else {
                    it
                }
            }
            if (moved != profiles) {
                profiles = moved
                store.putString("profiles", ProfileCodec.encodeAll(moved))
            }
            store.putString(STRATEGY_MIGRATED, "true")
        }
    }

    /** The profile the camera opens with. */
    var defaultProfileId by storedString("defaultProfileId", Profile.Default.id)

    /** The last "Find my profile" result ([ScreeningSummary]), empty if never taken. */
    var lastScreening by storedString("lastScreening", "")

    val allProfiles: List<Profile> get() = profiles + Profile.builtIns

    fun profile(id: String): Profile? = allProfiles.firstOrNull { it.id == id }

    fun saveProfile(p: Profile) {
        profiles = if (profiles.any { it.id == p.id }) profiles.map { if (it.id == p.id) p else it } else profiles + p
        store.putString("profiles", ProfileCodec.encodeAll(profiles))
    }

    fun deleteProfile(id: String) {
        profiles = profiles.filterNot { it.id == id }
        store.putString("profiles", ProfileCodec.encodeAll(profiles))
        if (defaultProfileId == id) defaultProfileId = Profile.Default.id
    }

    private fun stored(key: String, default: Boolean) = Stored(
        store.getString(key)?.toBooleanStrictOrNull() ?: default
    ) { store.putString(key, it.toString()) }

    private fun storedInt(key: String, default: Int) = Stored(
        store.getString(key)?.toIntOrNull() ?: default
    ) { store.putString(key, it.toString()) }

    private fun storedString(key: String, default: String) = Stored(
        store.getString(key) ?: default
    ) { store.putString(key, it) }

    private inline fun <reified E : Enum<E>> storedEnum(key: String, default: E) = Stored(
        store.getString(key)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
    ) { store.putString(key, it.name) }
}

/** A Compose state that writes itself to the store on each change. */
class Stored<T>(initial: T, private val write: (T) -> Unit) {
    private var state by mutableStateOf(initial)

    operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): T = state

    operator fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: T) {
        state = value
        write(value)
    }
}
