package et.sera.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Accessible image viewer: tap to open, pinch to zoom, drag to pan, close to return. */
@Composable
fun SeraMediaLightbox(url: String, description: String, accessToken:String?=null, onDismiss: () -> Unit) {
    var zoom by remember(url) { mutableFloatStateOf(1f) }
    var dx by remember(url) { mutableFloatStateOf(0f) }
    var dy by remember(url) { mutableFloatStateOf(0f) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF10181C))) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Image viewer", color = Color.White, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onDismiss) { Text("Close", color = Color.White) }
            }
            Box(Modifier.fillMaxWidth().weight(1f).pointerInput(url) {
                detectTransformGestures { _, pan, scale, _ ->
                    zoom = (zoom * scale).coerceIn(1f, 5f)
                    if (zoom > 1f) {
                        dx += pan.x
                        dy += pan.y
                    } else { dx = 0f; dy = 0f }
                }
            }) {
                SeraMediaImage(
                    url = url, description = description, widthPx = 1800, heightPx = 1800,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = zoom; scaleY = zoom; translationX = dx; translationY = dy
                    },
                    scale = androidx.compose.ui.layout.ContentScale.Fit, accessToken=accessToken
                )
            }
            TextButton(onClick = { zoom = 1f; dx = 0f; dy = 0f }, modifier = Modifier.fillMaxWidth()) {
                Text("Reset zoom", color = Color.White)
            }
        }
    }
}
