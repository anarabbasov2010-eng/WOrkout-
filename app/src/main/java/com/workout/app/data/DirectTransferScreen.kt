package com.workout.app.data

import android.os.Handler
import android.os.Looper
import java.net.Inet4Address
import java.net.NetworkInterface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DirectTransferScreen(onBack: () -> Unit, onImported: (ByteArray) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var localCode by remember { mutableStateOf(LocalTransfer.newPairingCode()) }
    var remoteCode by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    val detectedIp = remember { findLocalIpv4() }
    var passphrase by remember { mutableStateOf("") }
    var receiving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Connect both phones to the same trusted Wi-Fi network.") }

    val senderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val secretHost = host.trim()
            val secretCode = remoteCode.trim()
            scope.launch {
                status = try {
                    val encrypted = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                                val buffer = ByteArray(8192)
                                val output = java.io.ByteArrayOutputStream()
                                var total = 0
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    total += count
                                    require(total <= LocalTransfer.MAX_TRANSFER_BYTES) { "Backup exceeds the supported transfer size." }
                                    output.write(buffer, 0, count)
                                }
                                output.toByteArray()
                            } ?: error("Could not read the selected backup.")
                    }
                    withContext(Dispatchers.IO) { LocalTransfer.send(secretHost, 45872, secretCode, encrypted) }
                    "Encrypted archive sent and acknowledged by the receiver."
                } catch (error: Exception) {
                    "Transfer failed: ${error.localizedMessage ?: "check address, code and network"}"
                }
            }
        }
    }

    LaunchedEffect(receiving, localCode) {
        if (receiving) {
            status = "Waiting on TCP port 45872. Share the code and local IP shown below."
            val receiveCode = localCode
            val secret = passphrase.toCharArray()
            val receiverJob = currentCoroutineContext()[Job]
            try {
                withContext(Dispatchers.IO) {
                    LocalTransfer.receive(
                        port = 45872,
                        pairingCode = receiveCode,
                        shouldStop = { receiverJob?.isActive != true }
                    ) { encrypted ->
                        val plain = EncryptedBackup.decrypt(encrypted, secret)
                        val json = org.json.JSONObject(String(plain, Charsets.UTF_8))
                        require(json.optInt("formatVersion", 0) == 1) { "Unsupported backup data format." }
                        require(json.optInt("completedWorkouts", 0) in 0..1_000_000) { "Invalid workout count." }
                        require(json.optInt("waterMl", 750) in 0..20_000) { "Invalid water total." }
                        require(json.optInt("totalReps", 0) in 0..100_000) { "Invalid repetition total." }
                        Handler(Looper.getMainLooper()).post { onImported(plain) }
                    }
                }
                status = "Encrypted archive authenticated and transferred."
            } catch (error: Exception) {
                if (receiverJob?.isActive == true) {
                    status = "Receiver stopped: ${error.localizedMessage ?: "connection ended"}"
                }
            } finally {
                secret.fill('\u0000')
                receiving = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF0B0D0C)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { receiving = false; onBack() }) { Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("DEVICE-TO-DEVICE", color = Color(0xFFB7F34A), fontSize = 11.sp)
                Text("Direct local transfer", color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
            Icon(Icons.Filled.Wifi, null, tint = Color(0xFFB7F34A))
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF191D1B)), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Receive on this phone", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(localCode, color = Color(0xFFB7F34A), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { receiving = false; localCode = LocalTransfer.newPairingCode() }) { Text("New code") }
                }
                OutlinedTextField(value = passphrase, onValueChange = { passphrase = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(), label = { Text("Backup password") })
                Button(
                    onClick = { receiving = !receiving },
                    enabled = passphrase.length >= 10,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB7F34A), contentColor = Color(0xFF0B0D0C))
                ) { Text(if (receiving) "Stop receiver" else "Receive encrypted backup") }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF191D1B)), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Send to another phone", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                OutlinedTextField(value = host, onValueChange = { host = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Receiver local IPv4 address") })
                OutlinedTextField(value = remoteCode, onValueChange = { remoteCode = it.filter { char -> char.isDigit() }.take(6) }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Receiver's six-digit code") })
                Text("This phone's local IP: $detectedIp · port 45872", color = Color(0xFFADB6AE), fontSize = 12.sp)
                Text("Enter the destination phone's code (not this phone's code), then choose a .wobk encrypted backup file.", color = Color(0xFFADB6AE), fontSize = 12.sp)
                OutlinedButton(onClick = { senderPicker.launch(arrayOf("application/octet-stream", "*/*")) }, enabled = host.isNotBlank() && remoteCode.matches(Regex("\\d{6}")), modifier = Modifier.fillMaxWidth()) { Text("Choose backup and send") }
            }
        }
        Text(status, color = Color(0xFFB7F34A), fontSize = 12.sp)
        Text("Transfer uses a direct TCP connection on the same local network. Backup contents remain AES-GCM encrypted; use a trusted network and the same backup password on both devices.", color = Color(0xFFADB6AE), fontSize = 11.sp)
    }
}

private fun findLocalIpv4(): String = runCatching {
    val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
    interfaces.asSequence()
        .filter { it.isUp && !it.isLoopback && !it.isVirtual }
        .flatMap { it.inetAddresses.toList().asSequence() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { it.isSiteLocalAddress && !it.isLoopbackAddress }
        ?.hostAddress
        ?: "Unavailable (enter address manually)"
}.getOrElse { "Unavailable (enter address manually)" }
