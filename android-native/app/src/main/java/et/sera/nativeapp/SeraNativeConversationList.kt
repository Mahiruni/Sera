package et.sera.nativeapp

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Send
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import kotlin.math.roundToInt
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.ui.text.style.TextOverflow
import java.util.UUID
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL


@Composable internal fun SeraNativeConversationList(conversations:List<NativeConversation>,search:String,loading:Boolean,error:String,inboxInk:Color,inboxSoft:Color,chatSurface:Color,muted:Color,accent:Color,inboxTeal:Color,onInboxExit:(()->Unit)?,onCompany:()->Unit,onSearch:(String)->Unit,onRetry:()->Unit,onSelect:(NativeConversation)->Unit,token:String="",userId:String="",onControlChanged:()->Unit={}){
    var unreadOnly by remember{mutableStateOf(false)}
    var archivedOnly by remember{mutableStateOf(false)}
    var controls by remember{mutableStateOf(false)}
    var revealed by remember{mutableStateOf<String?>(null)}
    var swipeError by remember{mutableStateOf("")}
    var busy by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    fun quick(c:NativeConversation,action:String,value:Boolean,minutes:Int=0){if(busy)return;busy=true;scope.launch{val r=withContext(Dispatchers.IO){chatRpc(token,"sera_set_conversation_preference",JSONObject().put("p_conversation",c.id).put("p_action",action).put("p_value",value).put("p_mute_minutes",minutes))};busy=false;if(r.first!=null){revealed=null;onControlChanged()}else swipeError=r.second}}
    if(controls)SeraMessagingControlDialog(token,userId,onDismiss={controls=false})
    Column(Modifier.fillMaxSize()){
            Row(Modifier.fillMaxWidth().height(58.dp),verticalAlignment=Alignment.CenterVertically){
                if(onInboxExit!=null)IconButton(onClick=onInboxExit,modifier=Modifier.size(44.dp)){Icon(Icons.Outlined.ArrowBack,"Back",tint=inboxInk)}
                Text("Messages",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold,color=inboxInk,modifier=Modifier.weight(1f).padding(start=if(onInboxExit==null)6.dp else 2.dp))
                if(userId.isNotBlank())IconButton(onClick={controls=true}){Icon(Icons.Outlined.MoreVert,"Messaging settings",tint=accent)}
                TextButton(onClick=onCompany,contentPadding=PaddingValues(horizontal=8.dp)){Icon(Icons.Outlined.WorkOutline,null,tint=accent,modifier=Modifier.size(18.dp));Spacer(Modifier.width(5.dp));Text("Company",fontSize=12.sp,color=accent)}
            }
            Row(Modifier.fillMaxWidth().padding(horizontal=4.dp,vertical=8.dp).height(46.dp).clip(CircleShape).background(inboxSoft).padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                Icon(Icons.Outlined.Search,null,tint=muted,modifier=Modifier.size(20.dp))
                BasicTextField(value=search,onValueChange=onSearch,singleLine=true,textStyle=seraChatBody(MaterialTheme.typography.bodyLarge).copy(color=inboxInk),cursorBrush=SolidColor(accent),modifier=Modifier.weight(1f),decorationBox={inner->Box{if(search.isEmpty())Text("Search conversations",style=MaterialTheme.typography.bodyMedium,color=muted);inner()}})
                if(search.isNotEmpty())IconButton(onClick={onSearch("")},modifier=Modifier.size(36.dp)){Icon(Icons.Outlined.Close,"Clear search",tint=muted,modifier=Modifier.size(18.dp))}
            }
            Row(Modifier.fillMaxWidth().padding(horizontal=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=!unreadOnly&&!archivedOnly,onClick={unreadOnly=false;archivedOnly=false},label={Text("All")});FilterChip(selected=unreadOnly,onClick={unreadOnly=true;archivedOnly=false},label={Text("Unread")});FilterChip(selected=archivedOnly,onClick={archivedOnly=true;unreadOnly=false},label={Text("Archived")});Spacer(Modifier.weight(1f));val unreadTotal=conversations.sumOf{it.unread};if(unreadTotal>0)Text("${if(unreadTotal>99) "99+" else unreadTotal} unread",fontSize=12.sp,color=accent)}
            if(swipeError.isNotBlank())Row(verticalAlignment=Alignment.CenterVertically){Text(swipeError,Modifier.weight(1f),color=MaterialTheme.colorScheme.error);TextButton(onClick={swipeError=""}){Text("Dismiss")}}
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error.isNotBlank()) Row(verticalAlignment=Alignment.CenterVertically){Text(error,modifier=Modifier.weight(1f),color=MaterialTheme.colorScheme.error,fontSize=12.sp);TextButton(onClick=onRetry){Text("Retry")}}
            if (!loading && conversations.isEmpty() && error.isBlank())
                Text("No conversations yet. Start a conversation from SERA Network.", modifier = Modifier.padding(top = 20.dp))
            val filtered = conversations.filter { (if(archivedOnly)it.archived else !it.archived&&(!unreadOnly||it.unread>0||it.markedUnread))&&(it.peerName.contains(search.trim(), ignoreCase = true)||it.preview.contains(search.trim(),ignoreCase=true)) }.sortedWith(compareByDescending<NativeConversation>{it.pinned}.thenByDescending{it.lastAt})
            if(unreadOnly&&filtered.isEmpty()&&!loading&&search.isBlank())Text("You’re all caught up.",modifier=Modifier.padding(16.dp),color=muted)
            if (search.isNotBlank() && filtered.isEmpty() && !loading) Text("No matching conversations.", modifier = Modifier.padding(vertical = 16.dp), color = Color.Gray)
            LazyColumn(Modifier.weight(1f),verticalArrangement = Arrangement.spacedBy(1.dp),contentPadding=PaddingValues(bottom=12.dp)) {
                itemsIndexed(filtered, key = { _,c -> c.id }) { index,conversation ->
                    Column(Modifier.fillMaxWidth()) {
                    Card(onClick = {onSelect(conversation)},
                        shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().pointerInput(conversation.id){var dx=0f;detectHorizontalDragGestures(onDragStart={dx=0f},onHorizontalDrag={change,amount->change.consume();dx+=amount},onDragEnd={if(kotlin.math.abs(dx)>52.dp.toPx())revealed=if(revealed==conversation.id)null else conversation.id},onDragCancel={dx=0f})},
                        colors = CardDefaults.cardColors(containerColor = if(conversation.unread>0) inboxSoft else chatSurface)) {
                        Row(Modifier.padding(horizontal=12.dp,vertical=12.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(48.dp).clip(CircleShape).background(inboxSoft),
                                contentAlignment = Alignment.Center) {
                                Text(conversation.peerName.take(1).uppercase(), color = inboxTeal, fontWeight = FontWeight.Bold)
                                conversation.photo?.let{SeraMediaImage(it,"Profile photo",128,128,Modifier.fillMaxSize(),accessToken=token)}
                            }
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                Text((if(conversation.pinned)"⌖ " else "")+conversation.peerName, fontWeight = if(conversation.unread>0) FontWeight.Bold else FontWeight.SemiBold, color = inboxInk,fontSize=15.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                                Text(conversation.preview.ifBlank{"Start a conversation"}, fontSize = 13.sp, color = muted,maxLines=1,overflow=TextOverflow.Ellipsis)
                            }
                            if(userId.isNotBlank())SeraConversationControlMenu(token,conversation,onControlChanged)
                            Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(5.dp)){val stamp=runCatching{java.time.Instant.parse(conversation.lastAt).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))}.getOrDefault("");if(stamp.isNotBlank())Text(stamp,fontSize=11.sp,color=muted);if(conversation.unread>0||conversation.markedUnread) Box(Modifier.size(24.dp).clip(CircleShape).background(inboxTeal),contentAlignment=Alignment.Center){Text(if(conversation.unread>99)"99+" else if(conversation.unread>0)conversation.unread.toString() else "•",fontSize=10.sp,color=Color.White,fontWeight=FontWeight.Bold)}}
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(visible=revealed==conversation.id&&userId.isNotBlank()){
                        Row(Modifier.fillMaxWidth().padding(start=60.dp,end=4.dp),horizontalArrangement=Arrangement.SpaceEvenly){
                            TextButton(enabled=!busy,onClick={quick(conversation,"pin",!conversation.pinned)}){Text(if(conversation.pinned)"Unpin" else "Pin",fontSize=11.sp)}
                            TextButton(enabled=!busy,onClick={quick(conversation,"archive",!conversation.archived)}){Text(if(conversation.archived)"Restore" else "Archive",fontSize=11.sp)}
                            TextButton(enabled=!busy,onClick={quick(conversation,"unread",!conversation.markedUnread)}){Text(if(conversation.markedUnread)"Clear" else "Unread",fontSize=11.sp)}
                            TextButton(enabled=!busy,onClick={quick(conversation,"mute",true,if(conversation.mutedUntil!=null)0 else 60)}){Text(if(conversation.mutedUntil!=null)"Unmute" else "Mute 1h",fontSize=11.sp)}
                        }
                    }
                    if(index<filtered.lastIndex)SeraInboxConversationDivider()
                    }
                }
            }

    }
}

@Composable internal fun SeraInboxConversationDivider(){Box(Modifier.fillMaxWidth().padding(start=72.dp,end=12.dp).height(1.dp).clip(CircleShape).background(seraChatColors().border.copy(alpha=.55f)).testTag("sera_inbox_conversation_divider"))}
