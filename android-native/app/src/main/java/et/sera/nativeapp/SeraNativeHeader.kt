package et.sera.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontFamily
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
    showCreate: Boolean,
    onMessaging: () -> Unit,
    onLanguage: () -> Unit,
    onMenu: () -> Unit
) {
    Surface(color = Color(0xF8F5FAFB), shadowElevation = 1.dp) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenu, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Outlined.Menu, contentDescription = "Open navigation menu",
                    tint = Color(0xFF123D42), modifier = Modifier.size(25.dp))
            }
            Spacer(Modifier.width(3.dp))
            Text("SERA", color = Color(0xFF087E83), fontSize = 25.sp, letterSpacing = (-1.1).sp,
                fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black)
            Text(".et", color = Color(0xFF183B40), fontSize = 25.sp, letterSpacing = (-1.2).sp,
                fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (showCreate) {
                IconButton(onClick = onCompose, enabled = canCompose, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Outlined.AddCircleOutline, contentDescription = "Create post",
                        tint = Color(0xFF087E83))
                }
            }
            IconButton(onClick = onMessaging, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Messaging", tint = Color(0xFF183B40), modifier = Modifier.size(24.dp))
            }
            IconButton(onClick = onLanguage, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.Language, contentDescription = "Language",
                    tint = Color(0xFF183B40))
            }
        }
    }
}
