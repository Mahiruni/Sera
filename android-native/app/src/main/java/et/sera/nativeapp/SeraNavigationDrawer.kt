package et.sera.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.runtime.*
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val drawerInk = Color(0xFF153D42)
private val drawerTeal = Color(0xFF087E83)

private fun drawerIcon(section: String): ImageVector = when (section) {
    "Home" -> Icons.Outlined.Home
    "Network" -> Icons.Outlined.PeopleOutline
    "Jobs" -> Icons.Outlined.WorkOutline
    "Inbox" -> Icons.Outlined.ChatBubbleOutline
    "Search" -> Icons.Outlined.Search
    "Saved" -> Icons.Outlined.BookmarkBorder
    "Workspace" -> Icons.Outlined.Dashboard
    else -> Icons.Outlined.AccountCircle
}

/** Actual destinations only, with working actions and no placeholder pages. */
@Composable
internal fun SeraNavigationDrawer(
    selected: String,
    identity: String,
    onNavigate: (String) -> Unit,
    onCompose: () -> Unit,
    onLanguage: () -> Unit,
    onCheckUpdates: () -> Unit,
    onSignOut: () -> Unit,
    avatarUrl:String?=null,
    headline:String="",identityVerified:Boolean=false,
    onClose:()->Unit={}
) {
    var confirmSignOut by remember { mutableStateOf(false) }
    ModalDrawerSheet(
        modifier = Modifier.width(304.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerShape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp)
    ) {
        Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=12.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("SERA.et",color=drawerTeal,fontSize=25.sp,fontWeight=FontWeight.Black,modifier=Modifier.weight(1f));IconButton(onClick=onClose){Icon(Icons.Outlined.Close,"Close menu")}}
            Spacer(Modifier.height(8.dp))
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.fillMaxWidth().clickable{onNavigate("Profile")}.padding(vertical=12.dp,horizontal=4.dp), verticalAlignment = Alignment.CenterVertically) {
                    SeraAccountAvatar(identity.lineSequence().firstOrNull().orEmpty(),avatarUrl,48)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){Text(identity.lineSequence().firstOrNull()?.takeIf{it.isNotBlank()}?:"SERA professional",maxLines=2,fontWeight=FontWeight.Bold,fontSize=14.sp,modifier=Modifier.weight(1f,fill=false));if(identityVerified)SeraIdentityBadge()}
                        Text(headline.ifBlank{"View profile"},color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp,maxLines=2)
                    }
                    Icon(Icons.Outlined.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(Modifier.padding(vertical=8.dp))
            listOf("Home", "Network", "Search", "Jobs", "Inbox", "Saved", "Workspace").forEach { section ->
                NavigationDrawerItem(
                    label = { Text(when(section){"Search"->"Discover";"Inbox"->"Messages";else->section}, fontWeight = if (section == selected) FontWeight.Bold else FontWeight.Medium) },
                    selected = section == selected,
                    icon = { Icon(drawerIcon(section), contentDescription = null) },
                    onClick = { onNavigate(section) },
                    shape = RoundedCornerShape(10.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = Color(0xFFDDF5EE),
                        selectedIconColor = drawerTeal,
                        selectedTextColor = drawerInk,
                        unselectedIconColor = Color(0xFF6B8384),
                        unselectedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 15.dp), color = Color(0xFFD9E7E5))
            NavigationDrawerItem(label = { Text("Create post") }, selected = false,
                icon = { Icon(Icons.Outlined.AddCircleOutline, contentDescription = null) },
                onClick = onCompose)
            NavigationDrawerItem(label={Text("Language")},selected=false,icon={Icon(Icons.Outlined.Language,null)},onClick=onLanguage)
            NavigationDrawerItem(label={Text("Settings")},selected=selected=="Settings",icon={Icon(Icons.Outlined.Settings,null)},onClick={onNavigate("Settings")})
            NavigationDrawerItem(label = { Text("Check for app updates") }, selected = false,
                icon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                onClick = onCheckUpdates)
            HorizontalDivider(Modifier.padding(vertical=8.dp))
            NavigationDrawerItem(label = { Text("Sign out") }, selected = false,
                icon = { Icon(Icons.Outlined.Logout, contentDescription = null) },
                onClick = {confirmSignOut=true})
            Text("SERA.et · v${BuildConfig.VERSION_NAME} · Developed by Hisab Technologies",
                fontSize = 10.sp, color = Color(0xFF71888A),
                modifier = Modifier.padding(start = 14.dp, bottom = 8.dp))
        }
    }
    if(confirmSignOut)AlertDialog(onDismissRequest={confirmSignOut=false},title={Text("Sign out of SERA?")},text={Text("You can sign in again to access your account.")},confirmButton={TextButton(onClick={confirmSignOut=false;onSignOut()}){Text("Sign out")}},dismissButton={TextButton(onClick={confirmSignOut=false}){Text("Cancel")}})
}
