package com.beatthis.plugins.installer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Extracts APKs from an AAB (Android App Bundle) and installs them
 * as a non-sandboxed extension via PackageInstaller multi-APK session.
 *
 * AAB structure:
 *   base/dex/...
 *   base/manifest/AndroidManifest.xml
 *   base/native/...
 *   base/res/...
 *   ...splits (feature modules, configs)
 *
 * We extract the pre-built APKs from the bundle's universal.apk or
 * standalones/ directory, or if it's a raw AAB, we extract base + splits
 * and install them as a multi-APK session.
 *
 * For GitHub release AABs that are actually .apks archives (bundletool output),
 * we extract all .apk entries and install them together.
 */
class AabInstaller(private val context: Context) {

    companion object {
        const val INSTALL_ACTION = "com.beatthis.PLUGIN_INSTALL_STATUS"
    }

    /**
     * Install from a URI that points to an AAB, APKS, or APK file.
     */
    fun installFromUri(uri: Uri): Result<String> {
        return try {
            val input = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(Exception("Cannot open file"))

            // Try to extract as zip (AAB/APKS)
            val apkFiles = extractApks(input)
            input.close()

            if (apkFiles.isNotEmpty()) {
                installApks(apkFiles)
                apkFiles.forEach { it.delete() }
                Result.success("Installing ${apkFiles.size} APK(s)...")
            } else {
                // Not a zip or no APK entries — treat as single APK
                val singleInput = context.contentResolver.openInputStream(uri)
                    ?: return Result.failure(Exception("Cannot reopen file"))
                installSingleStream(singleInput)
                singleInput.close()
                Result.success("Installing APK...")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Install a single APK stream via PackageInstaller */
    private fun installSingleStream(input: InputStream) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }

        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)

        try {
            session.openWrite("base.apk", 0, -1).use { out ->
                input.copyTo(out)
                session.fsync(out)
            }
            val intent = Intent(INSTALL_ACTION).setPackage(context.packageName)
            val pi = PendingIntent.getBroadcast(context, sessionId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            session.commit(pi.intentSender)
        } catch (e: Exception) {
            session.abandon()
            throw e
        }
    }

    /**
     * Extract all .apk entries from a zip archive (AAB/APKS format).
     * Returns empty list if not a valid zip.
     */
    private fun extractApks(input: InputStream): List<File> {
        val extractDir = File(context.cacheDir, "plugin_install").also { it.mkdirs() }
        extractDir.listFiles()?.forEach { it.delete() }

        val apks = mutableListOf<File>()
        try {
            val zip = ZipInputStream(input)
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name.endsWith(".apk") && !entry.isDirectory) {
                    val outFile = File(extractDir, name.substringAfterLast("/"))
                    outFile.outputStream().use { out -> zip.copyTo(out) }
                    if (outFile.length() > 0) apks.add(outFile)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
            zip.close()
        } catch (_: Exception) {
            // Not a valid zip — return empty
        }
        return apks
    }

    /**
     * Install multiple APKs as a single session (split APKs).
     * This installs them as a regular app, not sandboxed.
     */
    private fun installApks(apks: List<File>) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            if (Build.VERSION.SDK_INT >= 31) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }

        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)

        try {
            for ((index, apk) in apks.withIndex()) {
                val name = "split_$index.apk"
                session.openWrite(name, 0, apk.length()).use { out ->
                    apk.inputStream().use { input -> input.copyTo(out) }
                    session.fsync(out)
                }
            }

            val intent = Intent(INSTALL_ACTION).apply {
                setPackage(context.packageName)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, sessionId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(pendingIntent.intentSender)
        } catch (e: Exception) {
            session.abandon()
            throw e
        }
    }
}
