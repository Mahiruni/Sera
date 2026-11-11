package et.sera.nativeapp

import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * A lifecycle-scoped, user-initiated Media3 player.
 * Never autoplay videos in a scrolling feed.
 * Only pass server-authorized HTTPS media URLs.
 */
@Composable
fun SeraVideoPlayer(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val allowed = remember(url) {
        runCatching { val uri = Uri.parse(url); uri.scheme == "https" && !uri.host.isNullOrBlank() }.getOrDefault(false)
    }
    if (!allowed) {
        Text("Video unavailable", color = MaterialTheme.colorScheme.error)
        return
    }
    val player = remember(context, url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            playWhenReady = false
            repeatMode = Player.REPEAT_MODE_OFF
            prepare()
        }
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
        AndroidView(
            factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true } },
            update = { it.player = player },
            modifier = Modifier.fillMaxWidth().height(240.dp)
        )
        Text("Playback starts when you press Play.", style = MaterialTheme.typography.bodySmall)
    }
}
