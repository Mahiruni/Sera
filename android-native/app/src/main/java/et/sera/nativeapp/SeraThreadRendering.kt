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
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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


@Composable internal fun SeraThreadHeader(conversation:NativeConversation,fallbackPhoto:String?,token:String,userId:String,peerTyping:Boolean,peerActivity:String?,sound:Boolean,inboxInk:Color,inboxSoft:Color,muted:Color,accent:Color,inboxTeal:Color,onBack:()->Unit,onSearch:()->Unit,onSound:()->Unit,onAudioStart:()->Unit,onHistory:(List<SeraCallEvent>)->Unit){
            Row(
                modifier = Modifier.fillMaxWidth().height(60.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(onClick = { onBack() },
                    modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Back to conversations",
                        tint = inboxInk)
                }
                Box(Modifier.size(42.dp).clip(CircleShape).background(inboxSoft),contentAlignment=Alignment.Center){Text(conversation.peerName.take(1).uppercase(),color=inboxTeal,fontWeight=FontWeight.Bold,fontSize=18.sp);(conversation.photo?:fallbackPhoto)?.let{SeraMediaImage(it,"Profile photo",112,112,Modifier.fillMaxSize())}}
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)){
                        Text(conversation.peerName, modifier=Modifier.weight(1f,fill=false),fontWeight=FontWeight.SemiBold,fontSize=17.sp,color=inboxInk,maxLines=1,overflow=TextOverflow.Ellipsis)
                        if(peerActivity=="Active now")SeraOnlineDot(Modifier.semantics{this.contentDescription="Active now"})
                    }
                    Text(if (peerTyping) "Typing…" else peerActivity ?: "Private conversation", fontSize = 12.sp, color = if(peerTyping||peerActivity=="Active now")accent else muted,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                SeraNativeVoiceCallButton(token,userId,conversation.id,conversation.peerName,conversation.photo,ink=inboxInk,accent=accent,onAudioStart=onAudioStart,onHistory=onHistory)
                IconButton(onClick=onSearch,modifier=Modifier.size(44.dp)){Icon(Icons.Outlined.Search,"Search messages",tint=inboxInk)}
                var threadMenu by remember{mutableStateOf(false)}
                Box{IconButton(onClick={threadMenu=true},modifier=Modifier.size(44.dp)){Icon(Icons.Outlined.MoreVert,"Conversation settings",tint=inboxInk)};DropdownMenu(expanded=threadMenu,onDismissRequest={threadMenu=false}){DropdownMenuItem(text={Text(if(sound)"Mute message sounds" else "Enable message sounds")},onClick={onSound();threadMenu=false})}}
            }

}
@Composable internal fun SeraThreadTimeline(modifier:Modifier,messageListState:androidx.compose.foundation.lazy.LazyListState,messages:List<NativeMessage>,callEvents:List<SeraCallEvent>,messageSearch:String,historyLimit:Int,menu:String?,peerName:String,token:String,userId:String,playback:SeraChatPlayback,inboxInk:Color,inboxSoft:Color,chatSurface:Color,muted:Color,accent:Color,outgoing:Color,incoming:Color,onLoadEarlier:()->Unit,onMenu:(String?)->Unit,onReply:(NativeMessage)->Unit,onReact:(NativeMessage)->Unit,onForward:(NativeMessage)->Unit,onCopy:(NativeMessage)->Unit,onUnsend:(NativeMessage)->Unit,onReaction:(NativeMessage,String)->Unit,onImage:(NativeChatFile)->Unit,onError:(String)->Unit,pendingTexts:List<NativeQueuedText> = emptyList(),connectionAvailable:Boolean=true,onRetryText:(String)->Unit={}){
            val maxBubbleWidth=(LocalConfiguration.current.screenWidthDp.dp*.76f).coerceIn(120.dp,340.dp)
            LazyColumn(modifier, state = messageListState, verticalArrangement = Arrangement.spacedBy(5.dp,Alignment.Bottom),
                contentPadding = PaddingValues(vertical = 12.dp)) {
                if(messages.size>=historyLimit && historyLimit<1000)item{TextButton(onClick=onLoadEarlier){Text("Load earlier messages")}}
                val filteredMessages=messages.filter{messageSearch.isBlank()||it.body.contains(messageSearch,ignoreCase=true)}
                val timeline=(filteredMessages.map{SeraTimelineItem(it.createdAt,it,null)}+callEvents.filter{messageSearch.isBlank()||"voice call".contains(messageSearch,true)}.map{SeraTimelineItem(it.createdAt,null,it)}).sortedBy{runCatching{java.time.Instant.parse(it.at).toEpochMilli()}.getOrDefault(0)}
                itemsIndexed(timeline,key={_,item->item.message?.let{m->if(m.clientId.isNotBlank()&&m.clientId!="null")"client-"+m.senderId+"-"+m.clientId else m.id}?:"call-"+item.event?.id}) { index,item ->
                    val date=runCatching{java.time.Instant.parse(item.at).atZone(java.time.ZoneId.systemDefault()).toLocalDate()}.getOrNull()
                    val previousDate=timeline.getOrNull(index-1)?.let{runCatching{java.time.Instant.parse(it.at).atZone(java.time.ZoneId.systemDefault()).toLocalDate()}.getOrNull()}
                    if(date!=null&&date!=previousDate)Box(Modifier.fillMaxWidth().padding(vertical=8.dp),contentAlignment=Alignment.Center){Surface(color=inboxSoft,shape=CircleShape){Text(date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy")),fontSize=11.sp,color=muted,modifier=Modifier.padding(horizontal=12.dp,vertical=5.dp))}}
                    val message=item.message
                    if(message==null){item.event?.let{SeraCallTimelineRow(it,userId,inboxSoft,inboxInk,muted,accent)}}else SeraNativeMessageRow(message=message,originalMessage=messages.firstOrNull{it.id==message.replyId},userId=userId,peerName=peerName,token=token,playback=playback,maxBubbleWidth=maxBubbleWidth,inboxInk=inboxInk,inboxSoft=inboxSoft,chatSurface=chatSurface,muted=muted,accent=accent,outgoing=outgoing,incoming=incoming,menuOpen=menu==message.id,onMenu={onMenu(if(it)message.id else null)},onReply={onReply(message)},onReact={onReact(message)},onForward={onForward(message)},onCopy={onCopy(message)},onUnsend={onUnsend(message)},onReaction={onReaction(message,it)},onImage=onImage,onError=onError)
                }
                items(pendingTexts.filter{messageSearch.isBlank()||it.body.contains(messageSearch,true)},key={"client-"+userId+"-"+it.id}){p->
                    SeraPendingTextBubble(p,connectionAvailable,outgoing,maxBubbleWidth,onRetry={onRetryText(p.id)})
                }
            }

}
