package com.workout.app.data

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Direct-LAN transport for password-encrypted WOBK backups only.
 * The caller authenticates/decrypts the archive before accepting it.
 */
object LocalTransfer {
    private val random = SecureRandom()
    const val MAX_TRANSFER_BYTES = 20_000_000

    fun newPairingCode(): String = (100_000 + random.nextInt(900_000)).toString()

    fun receive(
        port: Int,
        pairingCode: String,
        shouldStop: () -> Boolean = { false },
        onPayload: (ByteArray) -> Unit
    ) {
        require(pairingCode.matches(Regex("\\d{6}"))) { "Pairing code must have six digits." }
        require(port in 1024..65535) { "Invalid receiving port." }

        ServerSocket(port).use { server ->
            server.reuseAddress = true
            // Poll periodically so the screen can cancel a waiting receiver.
            server.soTimeout = 1_000
            while (!shouldStop()) {
                val socket = try {
                    server.accept()
                } catch (_: SocketTimeoutException) {
                    continue
                }
                socket.use { accepted ->
                    accepted.soTimeout = 30_000
                    val input = DataInputStream(accepted.getInputStream())
                    val output = DataOutputStream(accepted.getOutputStream())
                    val suppliedCode = input.readUTF().toByteArray(Charsets.UTF_8)
                    require(MessageDigest.isEqual(suppliedCode, pairingCode.toByteArray(Charsets.UTF_8))) {
                        "Pairing code did not match."
                    }
                    val size = input.readInt()
                    require(size in 1..MAX_TRANSFER_BYTES) { "Invalid archive size." }
                    val expectedDigest = ByteArray(32).also(input::readFully)
                    val payload = ByteArray(size).also(input::readFully)
                    val actualDigest = MessageDigest.getInstance("SHA-256").digest(payload)
                    require(MessageDigest.isEqual(expectedDigest, actualDigest)) { "Transfer integrity check failed." }

                    // The callback must authenticate and validate the archive before ACK.
                    onPayload(payload)
                    output.writeUTF("OK")
                    output.flush()
                    return
                }
            }
        }
        throw InterruptedException("Transfer receiver stopped.")
    }

    fun send(host: String, port: Int, pairingCode: String, encryptedBackup: ByteArray) {
        require(pairingCode.matches(Regex("\\d{6}"))) { "Pairing code must have six digits." }
        require(host.isNotBlank()) { "Receiver address is required." }
        require(encryptedBackup.size in 1..MAX_TRANSFER_BYTES) { "Archive is too large." }
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 10_000)
            socket.soTimeout = 30_000
            val output = DataOutputStream(socket.getOutputStream())
            val input = DataInputStream(socket.getInputStream())
            output.writeUTF(pairingCode)
            output.writeInt(encryptedBackup.size)
            output.write(MessageDigest.getInstance("SHA-256").digest(encryptedBackup))
            output.write(encryptedBackup)
            output.flush()
            require(input.readUTF() == "OK") { "Receiver did not confirm the transfer." }
        }
    }
}
