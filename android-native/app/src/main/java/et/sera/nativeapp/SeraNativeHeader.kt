package et.sera.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
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
    onSearch: () -> Unit,
    onNotifications: () -> Unit,
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
            Text(buildAnnotatedString{withStyle(SpanStyle(color=Color(0xFF087E83),fontWeight=FontWeight.Black)){append("SERA")};withStyle(SpanStyle(color=Color(0xFF183B40),fontWeight=FontWeight.Bold)){append(".et")}},modifier=Modifier.weight(1f),fontSize=24.sp,maxLines=1,overflow=TextOverflow.Ellipsis,letterSpacing=(-1).sp)
            IconButton(onClick = onSearch, modifier = Modifier.size(44.dp)) { Icon(Icons.Outlined.Search, "Search", tint = Color(0xFF183B40), modifier = Modifier.size(24.dp)) }
            IconButton(onClick = onNotifications, modifier = Modifier.size(44.dp)) { Icon(Icons.Outlined.NotificationsNone, "Notifications", tint = Color(0xFF183B40), modifier = Modifier.size(24.dp)) }
            IconButton(onClick = onMessaging, modifier = Modifier.size(44.dp)) { Icon(Icons.Outlined.ChatBubbleOutline, "Inbox", tint = Color(0xFF183B40), modifier = Modifier.size(24.dp)) }
        }
    }
}
