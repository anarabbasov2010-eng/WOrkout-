package com.workout.app.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

enum class UpdateChannel { STABLE, BETA }

data class UpdateManifest(
    val channel: UpdateChannel,
    val versionCode: Long,
    val versionName: String,
    val sha256: String,
    val signerCertificateSha256: String,
    val apkUrl: String
)

/** Requires a trusted signer pin compiled into the app, not merely a pin supplied by a download manifest. */
object UpdateSecurity {
    private const val MAX_APK_BYTES = 250L * 1024L * 1024L

    @Suppress("DEPRECATION")
    fun verify(
        context: Context,
        apkFile: File,
        manifest: UpdateManifest,
        expectedTrustedCertificateSha256: String,
        installedVersionCode: Long
    ): Result<Unit> = runCatching {
        require(expectedTrustedCertificateSha256.matches(Regex("[0-9a-fA-F]{64}"))) {
            "This app build has no trusted release-certificate pin; update installation is disabled."
        }
        require(manifest.versionCode > installedVersionCode) { "The update is not newer than the installed version." }
        require(manifest.sha256.matches(Regex("[0-9a-fA-F]{64}"))) { "Update manifest contains an invalid checksum." }
        require(manifest.signerCertificateSha256.equals(expectedTrustedCertificateSha256, true)) {
            "The release manifest signer does not match the trusted signer."
        }
        require(manifest.apkUrl.startsWith("https://")) { "Updates must use HTTPS." }
        require(apkFile.isFile && apkFile.length() in 1..MAX_APK_BYTES) { "Downloaded APK is missing or too large." }
        require(sha256(apkFile).equals(manifest.sha256, true)) { "APK checksum did not match the release manifest." }

        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val info = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
            ?: error("Android could not inspect the downloaded APK.")
        require(info.packageName == context.packageName) { "This APK belongs to a different application." }
        val certificates = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners?.map { it.toByteArray() }
        } else {
            info.signatures?.map { it.toByteArray() }
        }.orEmpty()
        val fingerprints = certificates.map { bytes -> hex(MessageDigest.getInstance("SHA-256").digest(bytes)) }
        require(fingerprints.any { it.equals(expectedTrustedCertificateSha256, true) }) {
            "The downloaded APK signing certificate is not trusted."
        }
        require(info.longVersionCodeSafe() == manifest.versionCode) { "APK version does not match the release manifest." }
    }

    @Suppress("DEPRECATION")
    private fun android.content.pm.PackageInfo.longVersionCodeSafe(): Long =
        if (Build.VERSION.SDK_INT >= 28) longVersionCode else versionCode.toLong()

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return hex(digest.digest())
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
