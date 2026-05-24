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
            // First pass: check what kind of file this is
            val probeInput = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(Exception("Cannot open file"))

            val fileType = probeFileType(probeInput)
            probeInput.close()

            when (fileType) {
                FileType.APKS -> {
                    // Zip containing .apk files — extract and install as splits
                    val input = context.contentResolver.openInputStream(uri)!!
                    val apkFiles = extractApks(input)
                    input.close()
                    if (apkFiles.isEmpty()) return Result.failure(Exception("No APKs found in archive"))
                    installApks(apkFiles)
                    apkFiles.forEach { it.delete() }
                    Result.success("Installing ${apkFiles.size} split APK(s)...")
                }
                FileType.APK -> {
                    // Single APK — install directly
                    val input = context.contentResolver.openInputStream(uri)!!
                    installSingleStream(input)
                    input.close()
                    Result.success("Installing APK...")
                }
                FileType.RAW_AAB -> {
                    Result.failure(Exception(
                        "Raw AAB cannot be installed directly. Download the .apks file from the release page instead."
                    ))
                }
                FileType.UNKNOWN -> {
                    // Try as single APK anyway
                    val input = context.contentResolver.openInputStream(uri)!!
                    installSingleStream(input)
                    input.close()
                    Result.success("Installing...")
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private enum class FileType { APK, APKS, RAW_AAB, UNKNOWN }

    /** Probe the file to determine its type */
    private fun probeFileType(input: InputStream): FileType {
        try {
            val zip = ZipInputStream(input)
            var hasApkEntries = false
            var hasClassesDex = false
            var hasAndroidManifest = false
            var hasBaseModule = false

            var entry = zip.nextEntry
            var count = 0
            while (entry != null && count < 200) {
                val name = entry.name
                when {
                    name.endsWith(".apk") && !entry.isDirectory -> hasApkEntries = true
                    name == "classes.dex" || name.startsWith("classes") && name.endsWith(".dex") -> hasClassesDex = true
                    name == "AndroidManifest.xml" -> hasAndroidManifest = true
                    name.startsWith("base/") -> hasBaseModule = true
                }
                zip.closeEntry()
                entry = zip.nextEntry
                count++
            }
            zip.close()

            return when {
                hasApkEntries -> FileType.APKS
                hasClassesDex || hasAndroidManifest -> FileType.APK
                hasBaseModule -> FileType.RAW_AAB
                else -> FileType.UNKNOWN
            }
        } catch (_: Exception) {
            return FileType.UNKNOWN
        }
    }

    /** Install a single APK stream via PackageInstaller */
    private fun installSingleStream(input: InputStream) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)

        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)

        try {
            session.openWrite("base.apk", 0, -1).use { out ->
                input.copyTo(out)
                session.fsync(out)
            }
            commitSession(session, sessionId)
        } catch (e: Exception) {
            session.abandon()
            throw e
        }
    }

    /**
     * Extract all .apk entries from a zip archive (APKS format).
     * Also handles raw AAB by extracting base module and repackaging as APK.
     * Returns empty list if not a valid zip.
     */
    private fun extractApks(input: InputStream): List<File> {
        val extractDir = File(context.cacheDir, "plugin_install").also { it.mkdirs() }
        extractDir.listFiles()?.forEach { it.delete() }

        val apks = mutableListOf<File>()
        var hasBaseManifest = false

        try {
            val zip = ZipInputStream(input)
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name.endsWith(".apk") && !entry.isDirectory) {
                    // APKS format: contains .apk files directly
                    val outFile = File(extractDir, name.substringAfterLast("/"))
                    outFile.outputStream().use { out -> zip.copyTo(out) }
                    if (outFile.length() > 0) apks.add(outFile)
                }
                if (name == "base/manifest/AndroidManifest.xml") {
                    hasBaseManifest = true
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
            zip.close()
        } catch (_: Exception) {
            // Not a valid zip
        }

        // If we found .apk entries, use those (APKS format)
        // If we found base/manifest but no APKs, it's a raw AAB — can't install directly
        return apks
    }

    /**
     * Install multiple APKs as a single session (split APKs).
     */
    private fun installApks(apks: List<File>) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)

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
            commitSession(session, sessionId)
        } catch (e: Exception) {
            session.abandon()
            throw e
        }
    }

    /**
     * Commit session with an Activity PendingIntent so the system shows
     * the install confirmation dialog to the user.
     */
    private fun commitSession(session: PackageInstaller.Session, sessionId: Int) {
        val intent = Intent(context, InstallResultActivity::class.java).apply {
            action = INSTALL_ACTION
            putExtra("session_id", sessionId)
        }
        val pi = PendingIntent.getActivity(
            context, sessionId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        session.commit(pi.intentSender)
    }
}
