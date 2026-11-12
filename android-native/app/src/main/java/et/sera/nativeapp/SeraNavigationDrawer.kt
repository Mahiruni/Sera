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
    onClose:()->Unit={}
) {
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
                    Box(Modifier.size(47.dp).background(Color.White, CircleShape),
                        contentAlignment = Alignment.Center) {
                        if(!avatarUrl.isNullOrBlank())SeraMediaImage(avatarUrl,"Profile photo",96,96,Modifier.fillMaxSize().clip(CircleShape)) else Text(identity.trim().take(1).uppercase().ifBlank{"S"},color=drawerTeal,fontWeight=FontWeight.Bold,fontSize=22.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(identity.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() }
                            ?: "SERA professional", maxLines = 1, fontWeight = FontWeight.Bold,
                            fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text("View profile",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)
                    }
                    Icon(Icons.Outlined.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(Modifier.padding(vertical=8.dp))
            listOf("Home", "Network", "Jobs", "Inbox").forEach { section ->
                NavigationDrawerItem(
                    label = { Text(section, fontWeight = if (section == selected) FontWeight.Bold else FontWeight.Medium) },
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
                onClick = onSignOut)
            Text("SERA.et · v${BuildConfig.VERSION_NAME} · Built for people and ideas",
                fontSize = 10.sp, color = Color(0xFF71888A),
                modifier = Modifier.padding(start = 14.dp, bottom = 8.dp))
        }
    }
}
