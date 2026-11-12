package et.sera.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.runtime.*
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.sp

/** Compact, consistent branded app bar with native accessible icon controls. */
@Composable
internal fun SeraNativeHeader(
    notificationCount:Int=0,
    inboxCount:Int=0,
    section: String,
    canCompose: Boolean,
    onCompose: () -> Unit,
    showCreate: Boolean,
    onMessaging: () -> Unit,
    onSearch: () -> Unit,
    onNotifications: () -> Unit,
    onMenu: () -> Unit,
    avatarUrl:String?=null,
    onProfile:()->Unit={},
    onSettings:()->Unit={}
) {
    var profileOpen by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenu, modifier = Modifier.size(44.dp).semantics{contentDescription="Open navigation menu"}) {
                Canvas(Modifier.size(23.dp)){val ink=Color(0xFF087E83);val w=size.width;val h=size.height;for(i in 0..2)drawLine(ink,Offset(w*.12f,h*(.24f+i*.26f)),Offset(w*(if(i==1).68f else .9f),h*(.24f+i*.26f)),strokeWidth=2.dp.toPx(),cap=StrokeCap.Round)}
            }
            Spacer(Modifier.width(3.dp))
            Text(buildAnnotatedString{withStyle(SpanStyle(color=Color(0xFF087E83),fontWeight=FontWeight.Black)){append("SERA")};withStyle(SpanStyle(color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold)){append(".et")}},modifier=Modifier.weight(1f),fontSize=24.sp,maxLines=1,overflow=TextOverflow.Ellipsis,letterSpacing=(-1).sp)
            IconButton(onClick = onSearch, modifier = Modifier.size(44.dp)) { Icon(Icons.Outlined.Search, "Search", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp)) }
            IconButton(onClick = onNotifications, modifier = Modifier.size(44.dp)) { BadgedBox(badge={SeraUnreadBadge(notificationCount)}){Icon(Icons.Outlined.NotificationsNone, if(notificationCount>0)"Notifications, $notificationCount unread" else "Notifications", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))} }
            IconButton(onClick = onMessaging, modifier = Modifier.size(40.dp)) { BadgedBox(badge={SeraUnreadBadge(inboxCount)}){Icon(Icons.Outlined.ChatBubbleOutline, if(inboxCount>0)"Inbox, $inboxCount unread messages" else "Inbox", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(23.dp))} }
            Box{IconButton(onClick={profileOpen=true},modifier=Modifier.size(44.dp).semantics{contentDescription="Profile menu"}){if(!avatarUrl.isNullOrBlank())SeraMediaImage(avatarUrl,"Profile photo",96,96,Modifier.size(32.dp).clip(CircleShape)) else Icon(Icons.Outlined.AccountCircle,null,Modifier.size(28.dp),tint=Color(0xFF087E83))};DropdownMenu(expanded=profileOpen,onDismissRequest={profileOpen=false}){DropdownMenuItem(text={Text("View profile")},leadingIcon={Icon(Icons.Outlined.AccountCircle,null)},onClick={profileOpen=false;onProfile()});DropdownMenuItem(text={Text("Settings")},leadingIcon={Icon(Icons.Outlined.Settings,null)},onClick={profileOpen=false;onSettings()})}}
        }
    }
}

@Composable private fun SeraUnreadBadge(count:Int){if(count>0)Badge(containerColor=SeraPurple,contentColor=Color.White){Text(if(count>99)"99+" else count.toString(),fontSize=10.sp,fontWeight=FontWeight.SemiBold)}}
