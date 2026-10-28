package com.workout.app.music

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.workout.app.R
import kotlinx.coroutines.delay

private val MusicLime = Color(0xFFB7F34A)
private val MusicInk = Color(0xFF0B0D0C)
private val MusicPanel = Color(0xFF191D1B)
private val MusicRaised = Color(0xFF242B25)
private val MusicMuted = Color(0xFFADB6AE)
private val WavePattern = listOf(8, 14, 20, 12, 27, 37, 22, 15, 30, 42, 23, 14, 33, 24, 45, 27, 16, 35, 23, 39, 18, 28, 43, 22, 12, 31, 18, 39, 25, 14, 33, 21, 11, 28, 16)

@Composable
fun MusicScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    var volume by remember { mutableFloatStateOf(.65f) }
    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var selectedName by remember { mutableStateOf(context.getString(R.string.music_no_track)) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val displayName = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull()
            selectedName = displayName?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: context.getString(R.string.music_local_track)
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.volume = volume
            player.play()
            isPlaying = true
            positionMs = 0L
            durationMs = 0L
        }
    }

    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(volume) { player.volume = volume }
    LaunchedEffect(player) {
        while (true) {
            isPlaying = player.isPlaying
            durationMs = player.duration.takeIf { it > 0L } ?: 0L
            if (!dragging) positionMs = player.currentPosition.coerceAtLeast(0L)
            delay(250)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MusicInk),
        contentPadding = PaddingValues(start = 18.dp, top = 12.dp, end = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(44.dp).background(MusicPanel, RoundedCornerShape(15.dp))
                ) {
                    Icon(Icons.Filled.ArrowBack, stringResource(R.string.music_back), tint = Color.White)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        stringResource(R.string.music_kicker),
                        color = MusicLime,
                        fontSize = 10.sp,
                        letterSpacing = 1.7.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        stringResource(R.string.music_title),
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Box(Modifier.size(42.dp).background(MusicPanel, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.GraphicEq, null, tint = MusicLime, modifier = Modifier.size(23.dp))
                }
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF2B3B23), Color(0xFF1A211B), Color(0xFF171A18))))
                    .border(1.dp, Color(0xFF35432F), RoundedCornerShape(28.dp))
                    .padding(19.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(stringResource(R.string.music_subtitle), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(stringResource(R.string.music_source_label), color = MusicMuted, fontSize = 11.sp)
                    }
                    Box(Modifier.background(Color(0x33242B25), RoundedCornerShape(11.dp)).padding(horizontal = 9.dp, vertical = 6.dp)) {
                        Text(
                            if (isPlaying) stringResource(R.string.music_playing) else stringResource(R.string.music_paused),
                            color = MusicLime,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = .7.sp
                        )
                    }
                }

                Box(
                    Modifier.size(158.dp).background(
                        Brush.linearGradient(listOf(Color(0xFF3B4E2C), Color(0xFF171D17))),
                        RoundedCornerShape(31.dp)
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.size(132.dp).border(1.dp, Color.White.copy(alpha = .09f), CircleShape)
                            .background(Color(0xFF111611), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        repeat(3) { index ->
                            Box(Modifier.size((104 - index * 23).dp).border(1.dp, Color.White.copy(alpha = .07f), CircleShape))
                        }
                        Box(Modifier.size(54.dp).background(MusicLime, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.MusicNote, null, tint = MusicInk, modifier = Modifier.size(30.dp))
                        }
                    }
                }

                Text(
                    selectedName,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 21.sp
                )

                Row(Modifier.fillMaxWidth().height(34.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    val waveOffset = if (isPlaying) ((positionMs / 250L).toInt() % WavePattern.size) else 0
                    WavePattern.forEachIndexed { index, base ->
                        val height = if (isPlaying) WavePattern[(index + waveOffset) % WavePattern.size] else base.coerceAtMost(18)
                        Box(
                            Modifier.padding(horizontal = 1.5.dp).width(3.dp).height(height.dp)
                                .clip(CircleShape)
                                .background(if (isPlaying && index % 4 != 0) MusicLime else MusicLime.copy(alpha = .32f))
                        )
                    }
                }

                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Slider(
                        value = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f,
                        onValueChange = {
                            dragging = true
                            if (durationMs > 0L) positionMs = (it * durationMs).toLong()
                        },
                        onValueChangeFinished = {
                            if (durationMs > 0L) player.seekTo(positionMs)
                            dragging = false
                        },
                        enabled = durationMs > 0L,
                        colors = SliderDefaults.colors(
                            thumbColor = MusicLime,
                            activeTrackColor = MusicLime,
                            inactiveTrackColor = Color(0xFF394139),
                            disabledThumbColor = MusicMuted,
                            disabledActiveTrackColor = Color(0xFF394139)
                        )
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatTime(positionMs), color = MusicMuted, fontSize = 10.sp)
                        Text(formatTime(durationMs), color = MusicMuted, fontSize = 10.sp)
                    }
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    IconButton(onClick = { player.seekTo(0L); positionMs = 0L }, enabled = player.mediaItemCount > 0) {
                        Icon(Icons.Filled.Replay, stringResource(R.string.music_restart), tint = MusicMuted, modifier = Modifier.size(25.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    FilledIconButton(
                        onClick = {
                            if (player.mediaItemCount > 0) {
                                if (player.isPlaying) player.pause() else player.play()
                                isPlaying = player.isPlaying
                            }
                        },
                        enabled = player.mediaItemCount > 0,
                        modifier = Modifier.size(62.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MusicLime,
                            contentColor = MusicInk,
                            disabledContainerColor = Color(0xFF394139),
                            disabledContentColor = MusicMuted
                        )
                    ) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = stringResource(if (isPlaying) R.string.music_pause else R.string.music_play),
                            modifier = Modifier.size(31.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    FilledTonalButton(
                        onClick = { picker.launch(arrayOf("audio/*")) },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MusicRaised, contentColor = MusicLime)
                    ) {
                        Text(stringResource(R.string.music_choose_file), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MusicPanel), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.VolumeDown, null, tint = MusicLime)
                        Text(stringResource(R.string.music_volume), color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text("${(volume * 100).toInt()}%", color = MusicLime, fontWeight = FontWeight.ExtraBold)
                    }
                    Slider(
                        value = volume,
                        onValueChange = { volume = it },
                        colors = SliderDefaults.colors(thumbColor = MusicLime, activeTrackColor = MusicLime, inactiveTrackColor = Color(0xFF394139))
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.VolumeDown, null, tint = MusicMuted, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.music_volume_hint),
                            color = MusicMuted,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Filled.VolumeUp, null, tint = MusicMuted, modifier = Modifier.size(17.dp))
                    }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().background(MusicPanel, RoundedCornerShape(19.dp)).padding(15.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Box(Modifier.size(35.dp).background(MusicRaised, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.MusicNote, null, tint = MusicLime, modifier = Modifier.size(18.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.music_privacy_title), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(stringResource(R.string.music_privacy_body), color = MusicMuted, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000L
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}
