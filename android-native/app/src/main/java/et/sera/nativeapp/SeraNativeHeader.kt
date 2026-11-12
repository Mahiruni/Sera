package et.sera.nativeapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val headerTeal=Color(0xFF087E83)
@Composable internal fun SeraAccountAvatar(name:String,url:String?,size:Int=36){
    val initials=name.trim().split(Regex("\\s+")).filter{it.isNotBlank()}.take(2).joinToString(""){it.take(1).uppercase()}.ifBlank{"S"}
    Box(Modifier.size(size.dp).clip(CircleShape).background(Color(0xFFE0F4F1)),contentAlignment=Alignment.Center){
        if(!url.isNullOrBlank())SeraMediaImage(url,"Profile photo",128,128,Modifier.fillMaxSize())
        else Text(initials,fontSize=(size*.39f).sp,fontWeight=FontWeight.Bold,color=headerTeal)
    }
}
@Composable internal fun SeraNativeHeader(
    notificationCount:Int=0,inboxCount:Int=0,section:String,canCompose:Boolean,onCompose:()->Unit,showCreate:Boolean,
    onMessaging:()->Unit,onSearch:()->Unit,onNotifications:()->Unit,onMenu:()->Unit,
    avatarUrl:String?=null,profileName:String="SERA member",profileHeadline:String="",identityVerified:Boolean=false,
    onProfile:()->Unit={},onEditProfile:()->Unit={},onSettings:()->Unit={},onSignOut:()->Unit={}
){
    var profileOpen by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    val ink=MaterialTheme.colorScheme.onSurface
    Surface(color=MaterialTheme.colorScheme.surface,shadowElevation=1.dp){
        Row(Modifier.fillMaxWidth().heightIn(min=56.dp).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onMenu,modifier=Modifier.size(48.dp).semantics{contentDescription="Open navigation menu"}){
                Canvas(Modifier.size(23.dp)){val w=size.width;val h=size.height;for(i in 0..2)drawLine(headerTeal,Offset(w*.12f,h*(.24f+i*.26f)),Offset(w*(if(i==1).68f else .9f),h*(.24f+i*.26f)),strokeWidth=2.dp.toPx(),cap=StrokeCap.Round)}
            }
            Text(buildAnnotatedString{withStyle(SpanStyle(color=headerTeal,fontWeight=FontWeight.Black)){append("SERA")};withStyle(SpanStyle(color=ink,fontWeight=FontWeight.Bold)){append(".et")}},Modifier.weight(1f),fontSize=22.sp,maxLines=1,overflow=TextOverflow.Ellipsis,letterSpacing=(-1).sp)
            IconButton(onClick=onSearch,modifier=Modifier.size(48.dp)){Icon(Icons.Outlined.Search,"Discover",tint=ink,modifier=Modifier.size(22.dp))}
            IconButton(onClick=onNotifications,modifier=Modifier.size(48.dp)){BadgedBox(badge={SeraUnreadBadge(notificationCount)}){Icon(Icons.Outlined.NotificationsNone,if(notificationCount>0)"Notifications, $notificationCount unread" else "Notifications",tint=ink,modifier=Modifier.size(22.dp))}}
            IconButton(onClick=onMessaging,modifier=Modifier.size(48.dp)){BadgedBox(badge={SeraUnreadBadge(inboxCount)}){Icon(Icons.Outlined.ChatBubbleOutline,if(inboxCount>0)"Inbox, $inboxCount unread messages" else "Inbox",tint=ink,modifier=Modifier.size(22.dp))}}
            Box{
                IconButton(onClick={profileOpen=true},modifier=Modifier.size(48.dp).semantics{contentDescription="Profile menu"}){SeraAccountAvatar(profileName,avatarUrl,32)}
                DropdownMenu(expanded=profileOpen,onDismissRequest={profileOpen=false},modifier=Modifier.widthIn(min=250.dp,max=300.dp)){
                    Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                        SeraAccountAvatar(profileName,avatarUrl,44)
                        Column(Modifier.weight(1f)){
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){Text(profileName,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f,fill=false));if(identityVerified)SeraIdentityBadge()}
                            if(profileHeadline.isNotBlank())Text(profileHeadline,style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                        }
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text={Text("View profile")},leadingIcon={Icon(Icons.Outlined.AccountCircle,null)},onClick={profileOpen=false;onProfile()})
                    DropdownMenuItem(text={Text("Edit profile")},leadingIcon={Icon(Icons.Outlined.EditNote,null)},onClick={profileOpen=false;onEditProfile()})
                    DropdownMenuItem(text={Text("Settings & privacy")},leadingIcon={Icon(Icons.Outlined.Settings,null)},onClick={profileOpen=false;onSettings()})
                    HorizontalDivider()
                    DropdownMenuItem(text={Text("Sign out")},leadingIcon={Icon(Icons.Outlined.Logout,null)},onClick={profileOpen=false;confirmSignOut=true})
                }
            }
        }
    }
    if(confirmSignOut)AlertDialog(onDismissRequest={confirmSignOut=false},title={Text("Sign out of SERA?")},text={Text("You can sign in again to access your professional account.")},confirmButton={TextButton(onClick={confirmSignOut=false;onSignOut()}){Text("Sign out")}},dismissButton={TextButton(onClick={confirmSignOut=false}){Text("Cancel")}})
}
@Composable private fun SeraUnreadBadge(count:Int){if(count>0)Badge(containerColor=SeraPurple,contentColor=Color.White){Text(if(count>99)"99+" else count.toString(),fontSize=10.sp,fontWeight=FontWeight.SemiBold)}}
