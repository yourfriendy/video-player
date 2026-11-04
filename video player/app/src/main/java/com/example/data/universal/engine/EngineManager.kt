package com.example.data.universal.engine

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.example.data.universal.UniversalException
import com.example.data.update.AppUpdateService
import com.example.data.update.InstallLaunch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Gets the optional OffScroll Engine onto the phone: downloads OffScroll-Engine.apk from the repo's latest
 * GitHub release, checks it is the engine and is signed with the same key as this app, then hands it to
 * Android's installer (Android never installs apps silently, so the user taps "Install" once).
 */
class EngineManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun isInstalled(): Boolean = installedVersionCode() >= 0

    /** The engine's build number, or -1 when it isn't installed. */
    fun installedVersionCode(): Long = try {
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(EngineProtocol.ENGINE_PACKAGE, 0))
    } catch (e: PackageManager.NameNotFoundException) {
        -1L
    }

    /** Downloads the engine APK into the private cache. [onProgress] gets 0f..1f. */
    suspend fun download(onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, APK_NAME)
        val part = File(dir, "$APK_NAME.part")
        val request = Request.Builder().url(APK_URL).header("User-Agent", "OffScroll-engine-installer").build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    throw UniversalException("The engine isn't on GitHub yet. It is published together with the next build.")
                }
                if (!response.isSuccessful) throw UniversalException("The download failed (HTTP ${response.code}).")
                val body = response.body ?: throw UniversalException("The download came back empty.")
                val total = body.contentLength()
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
                if (total > 0 && part.length() != total) throw UniversalException("The download was cut short. Please try again.")
            }
            if (!part.renameTo(target)) throw UniversalException("Couldn't save the engine file.")
            target
        } catch (e: Exception) {
            part.delete()
            throw e
        }
    }

    /** Makes sure the file really is the engine, and that Android will let it work with this app (same signing key). */
    @Suppress("DEPRECATION")
    fun verify(file: File) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0
        val archive = pm.getPackageArchiveInfo(file.path, flags)
            ?: throw UniversalException("The downloaded file isn't a valid app package.")
        if (archive.packageName != EngineProtocol.ENGINE_PACKAGE) {
            throw UniversalException("The downloaded file isn't the OffScroll Engine.")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val mine = signers(pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES))
            val theirs = signers(archive)
            if (mine != null && theirs != null && mine.intersect(theirs).isEmpty()) {
                throw UniversalException(
                    "The engine on GitHub is signed with a different key than this OffScroll, so they can't work together. " +
                        "Install the latest OffScroll from GitHub too, then try again."
                )
            }
        }
    }

    fun launchInstall(file: File): InstallLaunch = AppUpdateService(context).launchInstall(file)

    fun deleteDownload() {
        File(context.cacheDir, DIR).listFiles()?.forEach { it.delete() }
    }

    private fun signers(info: android.content.pm.PackageInfo): Set<String>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val signing = info.signingInfo ?: return null
        val certs = (if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory) ?: return null
        val digest = MessageDigest.getInstance("SHA-256")
        return certs.map { digest.digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } }.toSet()
    }

    companion object {
        private const val DIR = "engine"
        private const val APK_NAME = "OffScroll-Engine.apk"
        const val APK_URL = "https://github.com/${AppUpdateService.REPO}/releases/latest/download/$APK_NAME"
        const val RELEASE_PAGE = "https://github.com/${AppUpdateService.REPO}/releases/latest"
    }
}
