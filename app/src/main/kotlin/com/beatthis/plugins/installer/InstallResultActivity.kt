package com.beatthis.plugins.installer

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Bundle
import android.widget.Toast

/**
 * Receives PackageInstaller status callbacks.
 * When the system needs user confirmation, it sends EXTRA_STATUS = STATUS_PENDING_USER_ACTION
 * with a confirmation intent we must launch.
 */
class InstallResultActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
        finish()
    }

    private fun handleIntent(intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // System wants user confirmation — launch the confirm dialog
                @Suppress("DEPRECATION")
                val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(confirmIntent)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Toast.makeText(this, "Plugin installed successfully", Toast.LENGTH_SHORT).show()
            }
            else -> {
                Toast.makeText(this, "Install failed: $msg", Toast.LENGTH_LONG).show()
            }
        }
    }
}
