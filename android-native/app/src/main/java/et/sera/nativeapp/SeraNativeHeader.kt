package et.sera.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Compact, consistent branded app bar with native accessible icon controls. */
@Composable
internal fun SeraNativeHeader(
    section: String,
    canCompose: Boolean,
    onCompose: () -> Unit,
    onLanguage: () -> Unit,
    onInbox: () -> Unit
) {
    Surface(color = Color(0xFFF5FAFB), shadowElevation = 1.dp) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("SERA", color = Color(0xFF087E83), fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
            Text(".et", color = Color(0xFF183B40), fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            if (section == "Home") {
                IconButton(onClick = onCompose, enabled = canCompose, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.AddCircleOutline, contentDescription = "Create post",
                        tint = Color(0xFF087E83))
                }
            }
            IconButton(onClick = onInbox, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Inbox",
                    tint = Color(0xFF183B40))
            }
            IconButton(onClick = onLanguage, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.Language, contentDescription = "Language",
                    tint = Color(0xFF183B40))
            }
        }
    }
}
