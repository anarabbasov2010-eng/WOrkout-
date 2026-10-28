package com.workout.app.data

import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun UpdateScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val installedVersion = remember { installedVersionCode(context) }
    var channel by remember { mutableStateOf(UpdateChannel.STABLE) }
    var manifest by remember { mutableStateOf<UpdateManifest?>(null) }
    var verifiedApk by remember { mutableStateOf<java.io.File?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember {
        mutableStateOf(
            if (BuildConfigHolder.signerPinReady) "Choose a channel and check for a signed release."
            else "This build has no embedded production signer pin; verified APK installation is intentionally disabled."
        )
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF0B0D0C)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("RELEASE CHANNELS", color = Color(0xFFB7F34A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Stable & Beta updates", color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
            Icon(Icons.Filled.Security, null, tint = Color(0xFFB7F34A))
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF191D1B)), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Update channel", color = Color.White, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = channel == UpdateChannel.STABLE, onClick = { channel = UpdateChannel.STABLE; manifest = null; verifiedApk = null }, label = { Text("Stable") })
                    FilterChip(selected = channel == UpdateChannel.BETA, onClick = { channel = UpdateChannel.BETA; manifest = null; verifiedApk = null }, label = { Text("Beta") })
                }
                Text("Installed version code: $installedVersion", color = Color(0xFFADB6AE), fontSize = 12.sp)
                Button(
                    onClick = {
                        busy = true
                        status = "Checking GitHub releases…"
                        scope.launch {
                            try {
                                val update = UpdateClient.findLatest(channel, installedVersion)
                                manifest = update
                                verifiedApk = null
                                status = if (update == null) {
                                    "No newer release is available in the selected channel."
                                } else {
                                    "Found ${update.versionName} · ${update.channel.name}. APK signature and checksum will be verified before installation."
                                }
                            } catch (error: Exception) {
                                status = "Update check failed: ${error.localizedMessage ?: "network or release unavailable"}"
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB7F34A), contentColor = Color(0xFF0B0D0C))
                ) { Icon(Icons.Filled.CloudDownload, null); Spacer(Modifier.width(8.dp)); Text("Check for updates") }

                if (manifest != null) {
                    OutlinedButton(
                        onClick = {
                            val selected = manifest ?: return@OutlinedButton
                            busy = true
                            status = "Downloading and validating APK…"
                            scope.launch {
                                try {
                                    val apk = UpdateClient.downloadAndVerify(context, selected, installedVersion)
                                    verifiedApk = apk
                                    status = "APK verified: checksum, package identity, version and signer pin match."
                                } catch (error: Exception) {
                                    verifiedApk = null
                                    status = "Verification failed; APK will not be installed: ${error.localizedMessage ?: "unknown validation error"}"
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy && BuildConfigHolder.signerPinReady,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Download & verify APK") }
                }
                if (verifiedApk != null) {
                    Button(
                        onClick = {
                            try {
                                UpdateClient.installOrOpenPermissionSettings(context, verifiedApk!!)
                                status = "Android opened the package installer. Confirm the update in the system dialog."
                            } catch (error: Exception) {
                                status = error.localizedMessage ?: "Installation could not be opened."
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB7F34A), contentColor = Color(0xFF0B0D0C))
                    ) { Text("Install verified APK") }
                }
                if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color(0xFFB7F34A))
            }
        }
        Text(status, color = Color(0xFFB7F34A), fontSize = 13.sp)
        Text("The app accepts only a newer APK with a matching SHA-256 checksum and the release certificate pinned in this build. Android requires user confirmation for installation. Debug builds without a trusted signer pin cannot install releases.", color = Color(0xFFADB6AE), fontSize = 12.sp)
    }
}

private object BuildConfigHolder {
    val signerPinReady: Boolean = runCatching {
        com.workout.app.BuildConfig.RELEASE_SIGNER_SHA256.matches(Regex("[0-9a-fA-F]{64}"))
    }.getOrDefault(false)
}

@Suppress("DEPRECATION")
private fun installedVersionCode(context: Context): Long {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
}
