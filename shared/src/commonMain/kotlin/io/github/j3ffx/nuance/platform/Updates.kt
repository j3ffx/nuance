package io.github.j3ffx.nuance.platform

/** A version published on GitHub Releases: its APK and the APK's SHA-256 file. */
data class ReleaseInfo(val version: String, val apkUrl: String, val sha256Url: String)

/** How an update check or install ended, told to the person in words. */
enum class UpdateOutcome {
    /** Nothing newer than the installed version. */
    UpToDate,

    /** A newer version exists ([ReleaseInfo]). */
    Available,

    /** Android's installer took over: the person confirms there. */
    Installing,

    /** Android needs the person to allow Nuance to install apps (a settings page was opened). */
    NeedsPermission,

    /** GitHub could not be reached, or the download was damaged (its SHA-256 did not match). */
    Failed
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
