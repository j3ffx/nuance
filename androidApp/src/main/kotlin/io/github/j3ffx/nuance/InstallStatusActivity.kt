package io.github.j3ffx.nuance

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Bundle

/**
 * Receives the system installer's answer about an update (through the PendingIntent [Updater]
 * gives it) and shows the confirmation screen it asks for. Not exported: no other app can send
 * it an intent to launch, unlike the launcher activity. Invisible, it finishes at once.
 */
class InstallStatusActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, 0) == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)?.let { startActivity(it) }
        }
        finish()
    }
}
