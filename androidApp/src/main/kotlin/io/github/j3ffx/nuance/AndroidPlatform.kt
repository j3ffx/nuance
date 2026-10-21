package io.github.j3ffx.nuance

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.edit
import io.github.j3ffx.nuance.platform.KeyValueStore
import io.github.j3ffx.nuance.platform.Platform

/** Android's [Platform]: SharedPreferences (never backed up), the share sheet, per-app language. */
class AndroidPlatform(private val context: Context) : Platform {
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

    override fun openLanguageSettings(): Boolean {
        // Android 13+ has a language setting per app; before, the phone's language is the only one.
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null))
        } else {
            Intent(Settings.ACTION_LOCALE_SETTINGS)
        }
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
