package com.workout.app.data

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Small direct-LAN transfer for already encrypted backup blobs. Keep both devices on
 * a trusted network, share the short-lived pairing code privately, and never send raw
 * fitness data to this socket. This is a transport primitive, not cloud synchronization.
 */
object LocalTransfer {
    private val random = SecureRandom()
    private const val MAX_TRANSFER_BYTES = 20_000_000

    fun newPairingCode(): String = (100_000 + random.nextInt(900_000)).toString()

    fun receive(port: Int, pairingCode: String, onPayload: (ByteArray) -> Unit) {
        require(pairingCode.matches(Regex("\\d{6}"))) { "Pairing code must have six digits." }
        require(port in 1024..65535) { "Invalid receiving port." }
        ServerSocket(port).use { server ->
            server.reuseAddress = true
            server.soTimeout = 120_000
            server.accept().use { socket ->
                socket.soTimeout = 30_000
                val input = DataInputStream(socket.getInputStream())
                val output = DataOutputStream(socket.getOutputStream())
                require(MessageDigest.isEqual(input.readUTF().toByteArray(Charsets.UTF_8), pairingCode.toByteArray(Charsets.UTF_8))) {
                    "Pairing code did not match."
                }
                val size = input.readInt()
                require(size in 1..MAX_TRANSFER_BYTES) { "Invalid archive size." }
                val expectedDigest = ByteArray(32).also(input::readFully)
                val payload = ByteArray(size).also(input::readFully)
                val actualDigest = MessageDigest.getInstance("SHA-256").digest(payload)
                require(MessageDigest.isEqual(expectedDigest, actualDigest)) { "Transfer integrity check failed." }
                onPayload(payload)
                output.writeUTF("OK")
                output.flush()
            }
        }
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
