package et.sera.nativeapp

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** This edition deliberately excludes the native live-call transport. */
@Composable
internal fun SeraNativeVoiceCallButton(
    token: String, userId: String, conversation: String, name: String, photo: String?,
    ink: Color, accent: Color, onAudioStart: () -> Unit,
    onHistory: (List<SeraCallEvent>) -> Unit = {}
) {
    var showAvailability by remember(conversation) { mutableStateOf(false) }
    IconButton(onClick = { showAvailability = true }, modifier = Modifier.size(44.dp)) {
        Icon(Icons.Outlined.Call, "Voice call availability", tint = ink)
    }
    if (showAvailability) {
        AlertDialog(
            onDismissRequest = { showAvailability = false },
            title = { Text("Voice calling") },
            text = { Text("Live calling is not available in this edition of SERA. You can still send voice messages, photos and files.") },
            confirmButton = {
                TextButton(onClick = { showAvailability = false }) { Text("OK", color = accent) }
            }
        )
    }
}
