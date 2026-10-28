package com.workout.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.workout.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object UpdateClient {
    private const val API = "https://api.github.com/repos/anarabbasov2010-eng/WOrkout-"
    private const val MAX_MANIFEST_BYTES = 64 * 1024
    private const val MAX_APK_BYTES = 250L * 1024L * 1024L

    suspend fun findLatest(channel: UpdateChannel, installedVersionCode: Long): UpdateManifest? = withContext(Dispatchers.IO) {
        val candidates: List<JSONObject> = when (channel) {
            UpdateChannel.STABLE -> listOf(getJsonObject("$API/releases/latest"))
            UpdateChannel.BETA -> {
                val releases = JSONArray(getText("$API/releases?per_page=30"))
                (0 until releases.length())
                    .map { releases.getJSONObject(it) }
                    .filter { it.optBoolean("prerelease", false) && it.optString("tag_name").startsWith("beta-v") }
            }
        }

        for (release in candidates) {
            val assets = release.optJSONArray("assets") ?: continue
            val manifestAsset = (0 until assets.length())
                .map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name") == "update-manifest.json" } ?: continue
            val manifestUrl = manifestAsset.optString("browser_download_url")
            require(manifestUrl.startsWith("https://github.com/")) { "Untrusted update manifest URL." }
            val json = JSONObject(getText(manifestUrl, MAX_MANIFEST_BYTES))
            val remoteChannel = when (json.optString("channel").lowercase()) {
                "stable" -> UpdateChannel.STABLE
                "beta" -> UpdateChannel.BETA
                else -> error("Unsupported update channel in manifest.")
            }
            require(remoteChannel == channel) { "The release channel does not match the selected channel." }
            val manifest = UpdateManifest(
                channel = remoteChannel,
                versionCode = json.getLong("versionCode"),
                versionName = json.getString("versionName"),
                sha256 = json.getString("sha256"),
                signerCertificateSha256 = json.getString("signerCertificateSha256"),
                apkUrl = json.getString("apkUrl")
            )
            require(manifest.apkUrl.startsWith("https://github.com/")) { "Untrusted APK URL." }
            if (manifest.versionCode <= installedVersionCode) continue
            return@withContext manifest
        }
        null
    }

    suspend fun downloadAndVerify(
        context: Context,
        manifest: UpdateManifest,
        installedVersionCode: Long
    ): File = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "update-" + manifest.channel.name.lowercase() + "-" + manifest.versionCode + ".apk")
        if (file.exists() && UpdateSecurity.verify(context, file, manifest, BuildConfig.RELEASE_SIGNER_SHA256, installedVersionCode).isSuccess) {
            return@withContext file
        }
        file.delete()
        val connection = (URL(manifest.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "WOrkout-Android")
        }
        try {
            connection.connect()
            require(connection.url.protocol == "https") { "APK download redirected away from HTTPS." }
            require(connection.responseCode in 200..299) { "Download failed with HTTP ${connection.responseCode}." }
            require(connection.contentLengthLong in 1..MAX_APK_BYTES) { "APK is missing or larger than the supported size." }
            connection.inputStream.use { input ->
                FileOutputStream(file).buffered().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_APK_BYTES) { "APK exceeds the supported download size." }
                        output.write(buffer, 0, count)
                    }
                    output.flush()
                }
            }
            UpdateSecurity.verify(context, file, manifest, BuildConfig.RELEASE_SIGNER_SHA256, installedVersionCode)
                .getOrElse { throw it }
            file
        } catch (error: Exception) {
            file.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    fun installOrOpenPermissionSettings(context: Context, apk: File) {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settings)
            throw IllegalStateException("Allow installs from this source, then return and tap Install again.")
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun getJsonObject(url: String): JSONObject = JSONObject(getText(url, MAX_MANIFEST_BYTES))

    private fun getText(url: String, maxBytes: Int = 2 * 1024 * 1024): String {
        require(url.startsWith("https://")) { "Only HTTPS endpoints are permitted." }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "WOrkout-Android")
        }
        try {
            connection.connect()
            require(connection.url.protocol == "https") { "Request redirected away from HTTPS." }
            require(connection.responseCode in 200..299) { "Request failed with HTTP ${connection.responseCode}." }
            val output = java.io.ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(4096)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= maxBytes) { "Server response exceeds the supported size." }
                    output.write(buffer, 0, count)
                }
            }
            return output.toString(Charsets.UTF_8.name())
        } finally {
            connection.disconnect()
        }
    }
}
