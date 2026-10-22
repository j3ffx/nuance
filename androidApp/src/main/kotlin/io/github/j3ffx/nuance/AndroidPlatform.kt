package io.github.j3ffx.nuance

import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import io.github.j3ffx.nuance.platform.KeyValueStore
import io.github.j3ffx.nuance.platform.Platform
import io.github.j3ffx.nuance.platform.ReleaseInfo
import io.github.j3ffx.nuance.platform.UpdateOutcome
import io.github.j3ffx.nuance.platform.UpdateProgress
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android's [Platform]: SharedPreferences (never backed up), the share sheet, per-app language.
 * [recreate] redraws the activity, for a language change before Android 13.
 */
class AndroidPlatform(private val context: Context, private val recreate: () -> Unit) : Platform {
    override val versionName: String = BuildConfig.VERSION_NAME

    override val store: KeyValueStore = object : KeyValueStore {
        private val prefs = context.getSharedPreferences("nuance", Context.MODE_PRIVATE)

        override fun getString(key: String): String? = prefs.getString(key, null)

        override fun putString(key: String, value: String?) {
            prefs.edit { if (value == null) remove(key) else putString(key, value) }
        }
    }

    override fun shareText(text: String, title: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Android 13+ keeps a language per app (the same one its settings page shows, listed in
     * res/xml/locales_config.xml) and recreates the activity itself. Before, Nuance keeps it and
     * [MainActivity] applies it to its own context ([withAppLanguage]).
     */
    override var appLanguage: String?
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales
                .takeUnless { it.isEmpty }?.get(0)?.language
        } else {
            store.getString(KEY)
        }
        set(value) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getSystemService(LocaleManager::class.java).applicationLocales =
                    if (value == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(value)
            } else {
                store.putString(KEY, value)
                recreate()
            }
        }

    private val updater = Updater(context)

    override suspend fun latestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) { updater.latest() }

    override suspend fun installRelease(release: ReleaseInfo, onProgress: (UpdateProgress) -> Unit): UpdateOutcome =
        updater.install(release, onProgress)

    companion object {
        private const val KEY = "appLanguage"

        /** Before Android 13: [base] in the language kept by [appLanguage], if any. */
        fun withAppLanguage(base: Context): Context {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
            val tag = base.getSharedPreferences("nuance", Context.MODE_PRIVATE).getString(KEY, null) ?: return base
            val config = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
            return base.createConfigurationContext(config)
        }
    }
}
