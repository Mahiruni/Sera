package et.sera.nativeapp

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.CheckCircleOutline
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
    onClose:()->Unit={},
    updateStatus:String="Not checked yet",
    updateVersion:String?=null,
    updateProgress:Int?=null,
    updateNotice:String="",
    updateChecking:Boolean=false,
    updateCanInstall:Boolean=false,
    updatePermission:Boolean=false,
    onInstallUpdate:()->Unit={}
) {
    var confirmSignOut by remember { mutableStateOf(false) }
    ModalDrawerSheet(
        modifier = Modifier.width(304.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerShape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp)
    ) {
        Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=12.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Sera",color=drawerTeal,fontSize=25.sp,fontWeight=FontWeight.Black,modifier=Modifier.weight(1f));IconButton(onClick=onClose){Icon(Icons.Outlined.Close,"Close menu")}}
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
                    label = { Text(stringResource(when(section){"Home"->R.string.home;"Network"->R.string.network;"Search"->R.string.discover;"Jobs"->R.string.jobs;"Inbox"->R.string.messages;"Saved"->R.string.saved;else->R.string.workspace}), fontWeight = if (section == selected) FontWeight.Bold else FontWeight.Medium) },
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
            NavigationDrawerItem(label = { Text(stringResource(R.string.create_post)) }, selected = false,
                icon = { Icon(Icons.Outlined.AddCircleOutline, contentDescription = null) },
                onClick = onCompose)
            NavigationDrawerItem(label={Text(stringResource(R.string.language))},selected=false,icon={Icon(Icons.Outlined.Language,null)},onClick=onLanguage)
            NavigationDrawerItem(label={Text(stringResource(R.string.settings))},selected=selected=="Settings",icon={Icon(Icons.Outlined.Settings,null)},onClick={onNavigate("Settings")})
            Spacer(Modifier.height(10.dp))
            SeraDrawerUpdateCard(updateStatus,updateVersion,updateProgress,updateNotice,updateChecking,updateCanInstall,updatePermission,onCheckUpdates,onInstallUpdate)
            HorizontalDivider(Modifier.padding(vertical=8.dp))
            NavigationDrawerItem(label = { Text(stringResource(R.string.sign_out_short)) }, selected = false,
                icon = { Icon(Icons.Outlined.Logout, contentDescription = null) },
                onClick = {confirmSignOut=true})
            Text("Sera · v${BuildConfig.VERSION_NAME} · Developed by Hisab Technologies",
                fontSize = 10.sp, color = Color(0xFF71888A),
                modifier = Modifier.padding(start = 14.dp, bottom = 8.dp))
        }
    }
    if(confirmSignOut)AlertDialog(onDismissRequest={confirmSignOut=false},title={Text(stringResource(R.string.sign_out_question))},text={Text(stringResource(R.string.sign_out_explainer))},confirmButton={TextButton(onClick={confirmSignOut=false;onSignOut()}){Text(stringResource(R.string.sign_out_short))}},dismissButton={TextButton(onClick={confirmSignOut=false}){Text(stringResource(R.string.cancel))}})
}

/** Installed version is always the running APK's BuildConfig, never a server claim. */
@Composable
private fun SeraDrawerUpdateCard(status:String,version:String?,progress:Int?,notice:String,checking:Boolean,canInstall:Boolean,permission:Boolean,onCheck:()->Unit,onInstall:()->Unit) {
    val colors=MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),color=colors.surfaceVariant.copy(alpha=.42f),border=BorderStroke(1.dp,colors.outlineVariant)){
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                Surface(shape=RoundedCornerShape(12.dp),color=colors.primaryContainer){
                    Box(Modifier.size(42.dp),contentAlignment=Alignment.Center){Icon(Icons.Outlined.SystemUpdate,null,tint=colors.onPrimaryContainer)}
                }
                Column(Modifier.weight(1f)){
                    Text(stringResource(R.string.app_updates),fontWeight=FontWeight.Bold,fontSize=15.sp,color=colors.onSurface)
                    Text("Installed v${BuildConfig.VERSION_NAME}",fontSize=12.sp,color=colors.onSurfaceVariant)
                }
            }
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
                if(status=="Up to date")Icon(Icons.Outlined.CheckCircleOutline,null,tint=colors.primary,modifier=Modifier.size(17.dp))
                Text(status,fontWeight=FontWeight.SemiBold,fontSize=12.sp,color=colors.primary,modifier=Modifier.weight(1f))
            }
            if(version!=null)Text("New version: v$version",fontSize=12.sp,color=colors.onSurfaceVariant)
            if(progress!=null)LinearProgressIndicator(progress={progress.coerceIn(0,100)/100f},modifier=Modifier.fillMaxWidth())
            Text("Automatic downloads enabled",fontSize=11.sp,color=colors.onSurfaceVariant)
            if(notice.isNotBlank())Text(notice,fontSize=12.sp,color=colors.onSurfaceVariant)
            if(canInstall||permission) Button(onClick=onInstall,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){Text(if(permission)"Allow installation" else "Install update")}
            else OutlinedButton(onClick=onCheck,enabled=!checking&&version==null,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){
                Icon(Icons.Outlined.Refresh,null,Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(if(checking)"Checking…" else "Check for updates",fontSize=12.sp)
            }
        }
    }
}
