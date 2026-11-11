package et.sera.nativeapp

import androidx.compose.foundation.background
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
    onSignOut: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.width(304.dp),
        drawerContainerColor = Color(0xFFF7FCFB),
        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)
    ) {
        Column(Modifier.fillMaxHeight().padding(18.dp)) {
            Text("SERA.et", color = drawerTeal, fontSize = 28.sp,
                fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
            Text("Your professional world", color = Color(0xFF637A7E), fontSize = 12.sp)
            Spacer(Modifier.height(26.dp))
            Surface(color = Color(0xFFE5F5F1), shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(47.dp).background(Color.White, CircleShape),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.AccountCircle, contentDescription = null,
                            tint = drawerTeal, modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(identity.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() }
                            ?: "SERA professional", maxLines = 1, fontWeight = FontWeight.Bold,
                            fontSize = 14.sp, color = drawerInk)
                        Text("View your professional identity", color = Color(0xFF5B7B7D), fontSize = 11.sp)
                    }
                }
            }
            Spacer(Modifier.height(17.dp))
            listOf("Home", "Network", "Jobs", "Inbox", "Profile").forEach { section ->
                NavigationDrawerItem(
                    label = { Text(section, fontWeight = if (section == selected) FontWeight.Bold else FontWeight.Medium) },
                    selected = section == selected,
                    icon = { Icon(drawerIcon(section), contentDescription = null) },
                    onClick = { onNavigate(section) },
                    shape = RoundedCornerShape(16.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = Color(0xFFDDF5EE),
                        selectedIconColor = drawerTeal,
                        selectedTextColor = drawerInk,
                        unselectedIconColor = Color(0xFF6B8384),
                        unselectedTextColor = drawerInk
                    ),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 15.dp), color = Color(0xFFD9E7E5))
            NavigationDrawerItem(label = { Text("Create post") }, selected = false,
                icon = { Icon(Icons.Outlined.AddCircleOutline, contentDescription = null) },
                onClick = onCompose)
            NavigationDrawerItem(label = { Text("Language") }, selected = false,
                icon = { Icon(Icons.Outlined.Language, contentDescription = null) },
                onClick = onLanguage)
            Spacer(Modifier.weight(1f))
            NavigationDrawerItem(label = { Text("Sign out") }, selected = false,
                icon = { Icon(Icons.Outlined.Logout, contentDescription = null) },
                onClick = onSignOut)
            Text("SERA.et · Built for people and ideas",
                fontSize = 10.sp, color = Color(0xFF71888A),
                modifier = Modifier.padding(start = 14.dp, bottom = 8.dp))
        }
    }
}
