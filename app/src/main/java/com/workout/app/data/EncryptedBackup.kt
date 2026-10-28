package com.workout.app.data

import android.content.Context
import android.net.Uri
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.PBEKeySpec

/**
 * Portable password-protected backup. Format:
 * 5-byte magic/version, 16-byte random salt, 12-byte GCM IV, authenticated ciphertext.
 * AAD binds the header to the ciphertext. No key or password is stored in the archive.
 */
object EncryptedBackup {
    private val random = SecureRandom()
    private val magic = byteArrayOf(0x57, 0x4f, 0x42, 0x4b, 0x01)
    private const val ITERATIONS = 210_000
    private const val MAX_ARCHIVE_BYTES = 20_000_000

    fun encrypt(plain: ByteArray, passphrase: CharArray): ByteArray {
        require(plain.size <= MAX_ARCHIVE_BYTES) { "Backup exceeds the supported size." }
        require(passphrase.size >= 10) { "Use at least 10 characters for the backup password." }
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, derive(passphrase, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(magic)
        return magic + salt + iv + cipher.doFinal(plain)
    }

    fun decrypt(payload: ByteArray, passphrase: CharArray): ByteArray {
        require(payload.size in (magic.size + 16 + 12 + 16)..(MAX_ARCHIVE_BYTES + magic.size + 16 + 12 + 16)) {
            "Backup is truncated or too large."
        }
        require(payload.copyOfRange(0, magic.size).contentEquals(magic)) { "Unsupported backup format." }
        val salt = payload.copyOfRange(magic.size, magic.size + 16)
        val iv = payload.copyOfRange(magic.size + 16, magic.size + 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, derive(passphrase, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(magic)
        return cipher.doFinal(payload.copyOfRange(magic.size + 28, payload.size))
    }

    fun writeToUri(context: Context, uri: Uri, plain: ByteArray, passphrase: CharArray) {
        val payload = encrypt(plain, passphrase)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(payload); it.flush() }
            ?: throw IllegalStateException("Could not open backup destination.")
    }

    fun readFromUri(context: Context, uri: Uri, passphrase: CharArray): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readNBytes(MAX_ARCHIVE_BYTES + magic.size + 16 + 12 + 16 + 1)
            decrypt(bytes, passphrase)
        } ?: throw IllegalStateException("Could not open backup source.")

    private fun derive(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
