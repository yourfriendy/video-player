package com.example.data.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

enum class InstallLaunch { Started, NeedsPermission, Failed }

/**
 * Self-update from the repo's GitHub "latest" release.
 *
 * Android never lets an app install itself silently, so the flow is: find a newer build, download
 * the APK into the app's private cache, check it really is a newer build of THIS app signed with
 * the same key, then hand it to Android's own installer (the user taps "Update" once there).
 */
class AppUpdateService(private val context: Context) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun currentBuildNumber(): Int = try {
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0)).toInt()
    } catch (e: Exception) {
        0
    }

    fun currentVersionName(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    } catch (e: Exception) {
        ""
    }

    /** Asks GitHub for the newest published build. Throws [UpdateException] with a readable message. */
    suspend fun fetchLatest(): ReleaseInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(RELEASE_API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            when {
                response.code == 404 -> throw UpdateException("No build has been published on GitHub yet.")
                response.code == 403 || response.code == 429 ->
                    throw UpdateException("GitHub is limiting update checks right now. Try again in a little while.")
                !response.isSuccessful -> throw UpdateException("GitHub answered with an error (HTTP ${response.code}).")
            }
            val body = response.body?.string().orEmpty()
            val release = UpdateParser.parseLatestRelease(body)
                ?: throw UpdateException("The latest GitHub release has no OffScroll.apk attached.")
            if (release.buildNumber == null) {
                throw UpdateException("The latest GitHub release doesn't say which build it is, so it can't be compared.")
            }
            release
        }
    }

    /** Downloads the APK into the private cache. [onProgress] gets 0f..1f. */
    suspend fun download(release: ReleaseInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, UPDATES_DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "OffScroll-${release.buildNumber ?: 0}.apk")
        val part = File(dir, target.name + ".part")

        val request = Request.Builder().url(release.apkUrl).header("User-Agent", USER_AGENT).build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw UpdateException("The download failed (HTTP ${response.code}).")
                val body = response.body ?: throw UpdateException("The download came back empty.")
                val total = if (body.contentLength() > 0) body.contentLength() else release.apkSize
                body.byteStream().use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        var lastPercent = -1
                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            done += read
                            if (total > 0) {
                                val percent = (done * 100 / total).toInt()
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                                }
                            }
                        }
                    }
                }
            }
            if (release.apkSize > 0 && part.length() != release.apkSize) {
                throw UpdateException("The download was cut short. Please try again.")
            }
            if (!part.renameTo(target)) throw UpdateException("Couldn't save the update file.")
            target
        } catch (e: Exception) {
            part.delete()
            throw e
        }
    }

    /**
     * Makes sure the downloaded file is a newer build of this app, signed with the same key.
     * Android would refuse a different signature anyway, but with a vague error: this explains it.
     */
    @Suppress("DEPRECATION")
    fun verify(file: File) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            0
        }
        val archive = pm.getPackageArchiveInfo(file.path, flags)
            ?: throw UpdateException("The downloaded file isn't a valid app package.")
        if (archive.packageName != context.packageName) {
            throw UpdateException("The downloaded file belongs to a different app.")
        }
        if (PackageInfoCompat.getLongVersionCode(archive) <= currentBuildNumber().toLong()) {
            throw UpdateException("That build isn't newer than the one you already have.")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val installed = signerHashes(pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES))
            val incoming = signerHashes(archive)
            if (installed != null && incoming != null && installed.intersect(incoming).isEmpty()) {
                throw UpdateException(
                    "This update is signed with a different key, so Android won't install it over your " +
                        "current version. Uninstall OffScroll once and install the latest APK from GitHub; " +
                        "after that, updates will work."
                )
            }
        }
    }

    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Opens Android's installer for [file], or the "allow installs from this app" screen if that is needed first. */
    fun launchInstall(file: File): InstallLaunch {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !canInstall()) {
                val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(settings)
                return InstallLaunch.NeedsPermission
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
            val install = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(install)
            InstallLaunch.Started
        } catch (e: ActivityNotFoundException) {
            InstallLaunch.Failed
        } catch (e: SecurityException) {
            InstallLaunch.Failed
        } catch (e: IllegalArgumentException) {
            InstallLaunch.Failed
        }
    }

    /** Removes downloaded APKs (called once the installed build has caught up). */
    fun deleteDownloads() {
        File(context.cacheDir, UPDATES_DIR).listFiles()?.forEach { it.delete() }
    }

    private fun signerHashes(info: PackageInfo): Set<String>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val signingInfo = info.signingInfo ?: return null
        val signatures = (
            if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners
            else signingInfo.signingCertificateHistory
            ) ?: return null
        val digest = MessageDigest.getInstance("SHA-256")
        return signatures.map { sig -> digest.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
    }

    companion object {
        /** Change this if the repository is ever renamed or moved. */
        const val REPO = "yourfriendy/video-player"
        private const val RELEASE_API = "https://api.github.com/repos/$REPO/releases/latest"
        private const val USER_AGENT = "OffScroll-updater"
        private const val APK_MIME = "application/vnd.android.package-archive"
        private const val UPDATES_DIR = "updates"
    }
}
