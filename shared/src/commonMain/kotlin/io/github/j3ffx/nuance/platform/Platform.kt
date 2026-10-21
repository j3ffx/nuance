package io.github.j3ffx.nuance.platform

/**
 * What the common UI needs from the phone, besides the camera ([io.github.j3ffx.nuance.render.CameraRenderer]).
 * Each platform provides its own; nothing here touches the network.
 */
interface Platform {
    /** e.g. "0.0.0-dev (7e9fe1b)". */
    val versionName: String

    /** Where preferences and profiles are kept, on the phone only. */
    val store: KeyValueStore

    /** Opens the system share sheet with [text] (a profile, to send to someone or save as a file). */
    fun shareText(text: String, title: String)

    /**
     * The app's own language, "fr" or "en"; null follows the phone. On Android the screen is
     * recreated in the new language; on iOS it applies at the next launch.
     */
    var appLanguage: String?
}

/** A tiny persistent key-value store: SharedPreferences on Android, NSUserDefaults on iOS. */
interface KeyValueStore {
    fun getString(key: String): String?

    fun putString(key: String, value: String?)
}

/** A store in memory: previews, tests, and iOS until its side is written. */
class MemoryStore : KeyValueStore {
    private val values = HashMap<String, String>()

    override fun getString(key: String) = values[key]

    override fun putString(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }
}
