package io.github.j3ffx.nuance

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.net.toUri
import io.github.j3ffx.nuance.platform.ReleaseInfo
import io.github.j3ffx.nuance.platform.UpdateOutcome
import io.github.j3ffx.nuance.platform.UpdateProgress
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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

    /**
     * Downloads [release], checks it, hands it to Android's installer, and waits for the end of
     * the install (the person confirms on Android's screen). [onProgress] is called on the main
     * thread. When Nuance updates itself, Android closes it before that end: nothing returns.
     */
    suspend fun install(release: ReleaseInfo, onProgress: (UpdateProgress) -> Unit): UpdateOutcome {
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
        val main = Handler(Looper.getMainLooper())
        fun report(p: UpdateProgress) = main.post { onProgress(p) }
        return try {
            val id = withContext(Dispatchers.IO) {
                report(UpdateProgress.Downloading(null))
                download(release.apkUrl, file) { fraction -> report(UpdateProgress.Downloading(fraction)) }
                report(UpdateProgress.Verifying)
                val expected = get(release.sha256Url).trim().substringBefore(' ').lowercase()
                if (sha256(file) != expected) return@withContext null
                report(UpdateProgress.WaitingForAndroid)
                installer(file)
            } ?: return UpdateOutcome.Failed
            awaitEnd(id)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            UpdateOutcome.Failed
        } finally {
            file.delete()
        }
    }

    /** Until the session ends: installed, or declined (the person cancelled Android's screen). */
    private suspend fun awaitEnd(id: Int): UpdateOutcome {
        val installer = context.packageManager.packageInstaller
        return suspendCancellableCoroutine { cont ->
            val callback = object : PackageInstaller.SessionCallback() {
                override fun onCreated(sessionId: Int) = Unit

                override fun onBadgingChanged(sessionId: Int) = Unit

                override fun onActiveChanged(sessionId: Int, active: Boolean) = Unit

                override fun onProgressChanged(sessionId: Int, progress: Float) = Unit

                override fun onFinished(sessionId: Int, success: Boolean) {
                    if (sessionId != id) return
                    installer.unregisterSessionCallback(this)
                    if (cont.isActive) cont.resume(if (success) UpdateOutcome.Installed else UpdateOutcome.Cancelled)
                }
            }
            installer.registerSessionCallback(callback, Handler(Looper.getMainLooper()))
            cont.invokeOnCancellation { installer.unregisterSessionCallback(callback) }
        }
    }

    /** Writes [apk] into a new install session and commits it; returns the session's id. */
    private fun installer(apk: File): Int {
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
        return id
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

    /** Downloads [url] into [to], telling [onFraction] how much is done (when the size is known). */
    private fun download(url: String, to: File, onFraction: (Float) -> Unit) = connect(url).run {
        try {
            val total = contentLengthLong
            var done = 0L
            var reported = -1
            inputStream.use { input ->
                to.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        val percent = if (total > 0) (done * 100 / total).toInt() else -1
                        if (percent >= 0 && percent != reported) {
                            reported = percent
                            onFraction(percent / 100f)
                        }
                    }
                }
            }
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
