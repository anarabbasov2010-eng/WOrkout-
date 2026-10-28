package com.workout.app.data

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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DirectTransferScreen(onBack: () -> Unit, onImported: (ByteArray) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf(LocalTransfer.newPairingCode()) }
    var host by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var receiving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Connect both phones to the same trusted Wi-Fi network.") }

    val senderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val secretHost = host.trim()
            val secretCode = code.trim()
            scope.launch {
                status = try {
                    val encrypted = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: error("Could not read the selected backup.")
                    }
                    withContext(Dispatchers.IO) { LocalTransfer.send(secretHost, 45872, secretCode, encrypted) }
                    "Encrypted archive sent and acknowledged by the receiver."
                } catch (error: Exception) {
                    "Transfer failed: ${error.localizedMessage ?: "check address, code and network"}"
                }
            }
        }
    }

    LaunchedEffect(receiving, code, passphrase) {
        if (receiving) {
            status = "Waiting on TCP port 45872. Share the six-digit code and this phone's local IP address."
            val receiveCode = code
            val secret = passphrase.toCharArray()
            try {
                withContext(Dispatchers.IO) {
                    LocalTransfer.receive(45872, receiveCode) { encrypted ->
                        val plain = EncryptedBackup.decrypt(encrypted, secret)
                        withContext(Dispatchers.Main) { onImported(plain) }
                    }
                }
                status = "Encrypted archive received and restored."
            } catch (error: Exception) {
                status = "Receiver stopped: ${error.localizedMessage ?: "connection ended"}"
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
                Text("Pairing code", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(code, color = Color(0xFFB7F34A), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { code = LocalTransfer.newPairingCode() ; receiving = false }) { Text("New code") }
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
                Text("Send from this phone", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                OutlinedTextField(value = host, onValueChange = { host = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Receiver local IP address") })
                Text("Enter the receiver's code above, then select a .wobk encrypted backup file.", color = Color(0xFFADB6AE), fontSize = 12.sp)
                OutlinedButton(onClick = { senderPicker.launch(arrayOf("application/octet-stream", "*/*")) }, enabled = host.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Choose backup and send") }
            }
        }
        Text(status, color = Color(0xFFB7F34A), fontSize = 12.sp)
        Text("Transfer uses a direct TCP connection on the same local network. Backup contents remain AES-GCM encrypted; use a trusted network and the same backup password on both devices.", color = Color(0xFFADB6AE), fontSize = 11.sp)
    }
}
