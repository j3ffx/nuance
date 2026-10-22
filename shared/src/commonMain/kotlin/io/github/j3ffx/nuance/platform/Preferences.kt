package io.github.j3ffx.nuance.platform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    var onboardingDone by stored("onboardingDone", false)
    var theme by storedEnum("theme", ThemeChoice.Dark)

    /** Off by default; offered (unticked) at first launch. The only network call of the app. */
    var updateChecks by stored("updateChecks", false)

    /** A newer version found by a check, until it is installed; not stored. */
    var availableUpdate by mutableStateOf<ReleaseInfo?>(null)

    /** Minutes without touch nor movement before the camera pauses; 0 = never. */
    var autoPauseMinutes by storedInt("autoPauseMinutes", 2)
    var captureFormat by storedEnum("captureFormat", CaptureFormat.Filtered)

    /** The person's own profiles; presets come from [Profile.presets] and are never stored. */
    var profiles: List<Profile> by mutableStateOf(ProfileCodec.decodeAll(store.getString("profiles").orEmpty()))
        private set

    /** The profile the camera opens with. */
    var defaultProfileId by storedString("defaultProfileId", Profile.Default.id)

    /** The last "Find my profile" result ([ScreeningSummary]), empty if never taken. */
    var lastScreening by storedString("lastScreening", "")

    val allProfiles: List<Profile> get() = profiles + Profile.presets

    fun profile(id: String): Profile? =
        allProfiles.firstOrNull { it.id == id } ?: Profile.Default.takeIf { it.id == id }

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
