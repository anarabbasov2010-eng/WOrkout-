package com.workout.app.data

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
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
fun BackupScreen(onBack: () -> Unit, onImported: (ByteArray) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var passphrase by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Choose a strong backup password (10+ characters).") }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            val secret = passphrase.toCharArray()
            scope.launch {
                status = try {
                    withContext(Dispatchers.IO) {
                        val payload = EncryptedLocalStore(context).read()
                            ?: """{"formatVersion":1,"completedWorkouts":0,"waterMl":750,"totalReps":0}""".toByteArray()
                        EncryptedBackup.writeToUri(context, uri, payload, secret)
                    }
                    "Encrypted backup exported. Keep the password safe; it cannot be recovered."
                } catch (error: Exception) {
                    "Export failed: ${error.localizedMessage ?: "unknown error"}"
                } finally {
                    secret.fill('\u0000')
                }
            }
        }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val secret = passphrase.toCharArray()
            scope.launch {
                status = try {
                    val plain = withContext(Dispatchers.IO) { EncryptedBackup.readFromUri(context, uri, secret) }
                    require(plain.size in 2..20_000_000) { "Backup is too large or empty." }
                    withContext(Dispatchers.Main) { onImported(plain) }
                    "Backup authenticated and restored."
                } catch (error: Exception) {
                    "Import failed. Data was not changed: ${error.localizedMessage ?: "invalid backup"}"
                } finally {
                    secret.fill('\u0000')
                }
            }
        }
    }
    Column(Modifier.fillMaxSize().background(Color(0xFF0B0D0C)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("PRIVATE BACKUPS", color = Color(0xFFB7F34A), fontSize = 11.sp)
                Text("Encryption & restore", color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
            Icon(Icons.Filled.Lock, null, tint = Color(0xFFB7F34A))
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF191D1B)), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Backup password", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text("10+ characters") }
                )
                Button(
                    onClick = { exportPicker.launch("WOrkout-backup.wobk") },
                    enabled = passphrase.length >= 10,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB7F34A), contentColor = Color(0xFF0B0D0C))
                ) { Text("Export encrypted backup") }
                OutlinedButton(onClick = { importPicker.launch(arrayOf("application/octet-stream", "*/*")) }, enabled = passphrase.length >= 10, modifier = Modifier.fillMaxWidth()) {
                    Text("Import encrypted backup")
                }
            }
        }
        Text(status, color = Color(0xFFB7F34A), fontSize = 13.sp)
        Text("Backups are encrypted with AES-GCM and a password-derived key. Losing the password may make recovery impossible. Import validates the archive before replacing the current local state.", color = Color(0xFFADB6AE), fontSize = 12.sp)
    }
}
