package io.github.j3ffx.nuance

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.net.toUri
import io.github.j3ffx.nuance.platform.ReleaseInfo
import io.github.j3ffx.nuance.platform.UpdateOutcome
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.json.JSONObject

/**
 * Updates from GitHub Releases, Nuance's only network use: off by default (offered at first
 * launch), "Check now" always works. Nothing about the person or their images is sent: a plain
 * request for the latest release, then the APK download, checked against its SHA-256.
 */
class Updater(private val context: Context) {
    /** The latest release, or null if GitHub can't be reached or the release has no APK. */
    fun latest(): ReleaseInfo? = runCatching {
        val json = JSONObject(get("https://api.github.com/repos/${BuildConfig.UPDATE_REPOSITORY}/releases/latest"))
        val assets = json.getJSONArray("assets")
        val urls = (0 until assets.length()).map { assets.getJSONObject(it) }
            .associate { it.getString("name") to it.getString("browser_download_url") }
        val apk = urls.entries.firstOrNull { it.key.endsWith(".apk") } ?: return null
        val sha = urls["${apk.key}.sha256"] ?: return null
        ReleaseInfo(json.getString("tag_name").removePrefix("v"), apk.value, sha)
    }.getOrNull()

    /** Downloads [release], checks it, and hands it to Android's installer. */
    fun install(release: ReleaseInfo): UpdateOutcome {
        val packages = context.packageManager
        if (!packages.canRequestPackageInstalls()) {
            val settings = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                "package:${context.packageName}".toUri()
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settings)
            return UpdateOutcome.NeedsPermission
        }
        val file = File(context.cacheDir, "update.apk")
        return try {
            download(release.apkUrl, file)
            val expected = get(release.sha256Url).trim().substringBefore(' ').lowercase()
            if (sha256(file) != expected) return UpdateOutcome.Failed
            installer(file)
            UpdateOutcome.Installing
        } catch (_: Exception) {
            UpdateOutcome.Failed
        } finally {
            file.delete()
        }
    }

    private fun installer(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("nuance.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            // Android answers on InstallStatusActivity (not exported), which shows its confirmation screen.
            val status = Intent(context, InstallStatusActivity::class.java)
            val pending = PendingIntent.getActivity(
                context,
                id,
                status,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                statusOptions()
            )
            session.commit(pending.intentSender)
        }
    }

    /**
     * Since Android 14, the system sending our PendingIntent may only open our activity if we, its
     * creator, allow it: without this, the installer's answer was blocked ("Background activity
     * launch blocked") and the confirmation screen never showed. Allowed only while Nuance is on
     * screen, which it is when the person taps "Install".
     */
    private fun statusOptions(): Bundle? {
        val options = ActivityOptions.makeBasic()
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA ->
                options.setPendingIntentCreatorBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
                )

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                @Suppress("DEPRECATION")
                options.setPendingIntentCreatorBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )

            else -> return null
        }
        return options.toBundle()
    }

    private fun get(url: String): String = connect(url).run {
        try {
            inputStream.bufferedReader().readText()
        } finally {
            disconnect()
        }
    }

    private fun download(url: String, to: File) = connect(url).run {
        try {
            inputStream.use { input -> to.outputStream().use { input.copyTo(it) } }
        } finally {
            disconnect()
        }
    }

    private fun connect(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 30_000
        setRequestProperty("Accept", "application/vnd.github+json")
        // GitHub requires a user agent; this one says nothing but the app's name.
        setRequestProperty("User-Agent", "Nuance")
        if (responseCode !in 200..299) throw java.io.IOException("HTTP $responseCode for $url")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
