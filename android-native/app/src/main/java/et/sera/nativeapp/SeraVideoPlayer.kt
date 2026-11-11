package et.sera.nativeapp

import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * A lifecycle-scoped, user-initiated Media3 player.
 * Never autoplay videos in a scrolling feed.
 * Only pass server-authorized HTTPS media URLs.
 */
@Composable
fun SeraVideoPlayer(url: String, modifier: Modifier = Modifier, localPreview: Boolean = false) {
    val context = LocalContext.current
    val allowed = remember(url, localPreview) {
        runCatching { val uri = Uri.parse(url); (uri.scheme == "https" && !uri.host.isNullOrBlank()) || (localPreview && uri.scheme == "content") }.getOrDefault(false)
    }
    if (!allowed) {
        Text("Video unavailable", color = MaterialTheme.colorScheme.error)
        return
    }
    var fullscreen by remember(url) { mutableStateOf(false) }
    var buffering by remember(url) { mutableStateOf(false) }
    var playbackError by remember(url) { mutableStateOf(false) }
    var muted by remember(url) { mutableStateOf(false) }
    var speed by remember(url) { mutableFloatStateOf(1f) }
    var playing by remember(url) { mutableStateOf(false) }
    val player = remember(context, url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            playWhenReady = false
            repeatMode = Player.REPEAT_MODE_OFF
            // Defer network and local decoding until the user presses Play.
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) playbackError = false
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlayerError(error: PlaybackException) {
                playbackError = true
                buffering = false
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { player.prepare(); player.play() }) { Text(if (playbackError) "Retry video" else "Play video") }
            OutlinedButton(onClick = { player.pause() }, enabled = playing) { Text("Pause") }
            OutlinedButton(onClick = { muted = !muted; player.volume = if (muted) 0f else 1f }) {
                Text(if (muted) "Unmute" else "Mute")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Speed", style = MaterialTheme.typography.labelMedium)
            listOf(0.75f, 1f, 1.5f, 2f).forEach { value ->
                FilterChip(selected = speed == value, onClick = {
                    speed = value; player.setPlaybackSpeed(value)
                }, label = { Text("${value}×") })
            }
        }
        if (buffering) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (playbackError) Text("Video could not be played. Check your connection or try again.", color = MaterialTheme.colorScheme.error)
        if (!fullscreen) AndroidView(
            factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true } },
            update = { it.player = player },
            modifier = Modifier.fillMaxWidth().height(240.dp)
        )
        TextButton(onClick = { fullscreen = true }) { Text("Fullscreen video") }
        Text("Playback starts when you press Play.", style = MaterialTheme.typography.bodySmall)
    }
    if (fullscreen) {
        Dialog(onDismissRequest = { fullscreen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.fillMaxWidth()) {
                TextButton(onClick = { fullscreen = false }) { Text("Close fullscreen") }
                AndroidView(factory = { ctx -> PlayerView(ctx).apply { useController = true } },
                    update = { it.player = player },
                    modifier = Modifier.fillMaxWidth().height(400.dp))
            }
        }
    }
}
