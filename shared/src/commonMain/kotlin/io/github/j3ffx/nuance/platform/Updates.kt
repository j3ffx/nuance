package io.github.j3ffx.nuance.platform

/** A version published on GitHub Releases: its APK and the APK's SHA-256 file. */
data class ReleaseInfo(val version: String, val apkUrl: String, val sha256Url: String)

/** How an update check or install ended, told to the person in words. */
enum class UpdateOutcome {
    /** Nothing newer than the installed version. */
    UpToDate,

    /** A newer version exists ([ReleaseInfo]). */
    Available,

    /** Installed (another app was updated; Nuance updating itself is closed by Android first). */
    Installed,

    /** The person declined Android's confirmation, or the installer gave up. */
    Cancelled,

    /** Android needs the person to allow Nuance to install apps (a settings page was opened). */
    NeedsPermission,

    /** GitHub could not be reached, or the download was damaged (its SHA-256 did not match). */
    Failed
}

/** Where an install stands, for the progress shown meanwhile. */
sealed interface UpdateProgress {
    /** [fraction] of the APK downloaded, null while its size is unknown. */
    data class Downloading(val fraction: Float?) : UpdateProgress

    /** Checking the download against its SHA-256. */
    data object Verifying : UpdateProgress

    /** Handed to Android, which checks the APK, then asks the person to confirm. */
    data object WaitingForAndroid : UpdateProgress
}

object Versions {
    /** "0.2.0" > "0.1.10"? Suffixes like "-dev" are ignored; anything unreadable is never newer. */
    fun isNewer(candidate: String, installed: String): Boolean {
        val a = parse(candidate) ?: return false
        val b = parse(installed) ?: return true
        return compareValuesBy(a, b, { it[0] }, { it[1] }, { it[2] }) > 0
    }

    private fun parse(v: String): List<Int>? =
        v.removePrefix("v").substringBefore('-').split('.').takeIf { it.size == 3 }
            ?.map { it.toIntOrNull() ?: return null }
}
