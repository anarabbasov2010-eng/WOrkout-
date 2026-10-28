package com.workout.app.data

import android.content.Context
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Device-local AES-GCM store. A failed decrypt is surfaced; it is never treated as an empty database. */
class EncryptedLocalStore(context: Context) {
    private val dataFile = File(context.filesDir, "workout-private.bin")
    private val backupFile = File(context.filesDir, "workout-private.previous.bin")
    private val alias = "workout-local-aes-v1"

    @Synchronized
    fun write(plain: ByteArray) {
        require(plain.size <= MAX_BYTES) { "Local data is unexpectedly large." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plain)
        val payload = byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + encrypted
        val temp = File(dataFile.parentFile, dataFile.name + ".tmp")
        temp.outputStream().buffered().use { it.write(payload); it.flush() }
        if (dataFile.exists()) {
            if (backupFile.exists() && !backupFile.delete()) {
                temp.delete()
                throw IllegalStateException("Could not rotate local recovery copy.")
            }
            if (!dataFile.renameTo(backupFile)) {
                temp.delete()
                throw IllegalStateException("Could not preserve the previous local data.")
            }
        }
        if (!temp.renameTo(dataFile)) {
            if (backupFile.exists()) backupFile.renameTo(dataFile)
            temp.delete()
            throw IllegalStateException("Could not commit encrypted local data.")
        }
    }

    @Synchronized
    fun read(): ByteArray? {
        if (!dataFile.exists()) {
            if (backupFile.exists()) throw IllegalStateException("Main local store is missing but a recovery copy exists.")
            return null
        }
        return decryptPayload(dataFile.readBytes())
    }

    @Synchronized
    fun readRecoveryCopy(): ByteArray? =
        if (backupFile.exists()) decryptPayload(backupFile.readBytes()) else null

    private fun decryptPayload(payload: ByteArray): ByteArray {
        require(payload.size in 30..MAX_BYTES + 64) { "Encrypted local data is truncated or too large." }
        val ivLength = payload[0].toInt() and 0xff
        require(ivLength in 12..16 && payload.size > 1 + ivLength + 16) { "Encrypted local data header is invalid." }
        val iv = payload.copyOfRange(1, 1 + ivLength)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(payload.copyOfRange(1 + ivLength, payload.size))
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        generator.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                alias,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object { private const val MAX_BYTES = 20_000_000 }
}
