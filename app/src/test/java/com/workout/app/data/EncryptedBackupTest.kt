package com.workout.app.data

import org.junit.Assert.*
import org.junit.Test

class EncryptedBackupTest {
    @Test fun encryptedBackupRoundTrips() {
        val original = """{"formatVersion":1,"completedWorkouts":3}""".toByteArray()
        val backup = EncryptedBackup.encrypt(original, "strong-test-passphrase".toCharArray())
        assertArrayEquals(original, EncryptedBackup.decrypt(backup, "strong-test-passphrase".toCharArray()))
    }

    @Test fun wrongPasswordFailsAuthentication() {
        val backup = EncryptedBackup.encrypt("private data".toByteArray(), "strong-test-passphrase".toCharArray())
        try {
            EncryptedBackup.decrypt(backup, "different-password".toCharArray())
            fail("Wrong password must not decrypt the archive.")
        } catch (_: Exception) { }
    }

    @Test fun tamperingFailsAuthentication() {
        val backup = EncryptedBackup.encrypt("private data".toByteArray(), "strong-test-passphrase".toCharArray())
        backup[backup.lastIndex] = (backup.last().toInt() xor 0x55).toByte()
        try {
            EncryptedBackup.decrypt(backup, "strong-test-passphrase".toCharArray())
            fail("Tampered backup must fail authentication.")
        } catch (_: Exception) { }
    }
}
