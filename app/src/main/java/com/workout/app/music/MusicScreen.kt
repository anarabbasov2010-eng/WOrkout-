package com.workout.app.music

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

@Composable
fun MusicScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    var volume by remember { mutableFloatStateOf(.65f) }
    var isPlaying by remember { mutableStateOf(false) }
    var selectedName by remember { mutableStateOf("Choose a local audio file") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            selectedName = uri.lastPathSegment ?: "Local audio"
            player.volume = volume
            player.play()
            isPlaying = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(volume) { player.volume = volume }
    LaunchedEffect(player) {
        while (true) { isPlaying = player.isPlaying; delay(400) }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF0B0D0C)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("MUSIC STUDIO", color = Color(0xFFB7F34A), fontSize = 11.sp)
                Text("Your training soundtrack", color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
            Icon(Icons.Filled.MusicNote, null, tint = Color(0xFFB7F34A))
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF191D1B)), shape = RoundedCornerShape(26.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(128.dp).background(Color(0xFF263220), RoundedCornerShape(28.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.MusicNote, null, tint = Color(0xFFB7F34A), modifier = Modifier.size(72.dp))
                }
                Text(selectedName, color = Color.White, style = MaterialTheme.typography.titleMedium)
                Button(onClick = { picker.launch(arrayOf("audio/*")) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB7F34A), contentColor = Color(0xFF0B0D0C))) {
                    Text("Choose local music")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = {
                        if (player.isPlaying) player.pause() else player.play()
                        isPlaying = player.isPlaying
                    }) { Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null) }
                    Text(if (isPlaying) "Playing" else "Paused", color = Color.LightGray)
                }
                Text("Music volume · ${(volume * 100).toInt()}%", color = Color.LightGray)
                Slider(value = volume, onValueChange = { volume = it }, colors = SliderDefaults.colors(thumbColor = Color(0xFFB7F34A), activeTrackColor = Color(0xFFB7F34A)))
            }
        }
        Text("This player uses audio files you choose. Built-in music, adaptive ducking and external-app controls need separate licensed assets and device-specific integration.", color = Color(0xFFADB6AE), fontSize = 12.sp)
    }
}
