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


@Composable
internal fun SeraNativeMessageRow(message:NativeMessage,originalMessage:NativeMessage?,userId:String,peerName:String,token:String,playback:SeraChatPlayback,maxBubbleWidth:androidx.compose.ui.unit.Dp,inboxInk:Color,inboxSoft:Color,chatSurface:Color,muted:Color,accent:Color,outgoing:Color,incoming:Color,menuOpen:Boolean,onMenu:(Boolean)->Unit,onReply:()->Unit,onReact:()->Unit,onForward:()->Unit,onCopy:()->Unit,onUnsend:()->Unit,onReaction:(String)->Unit,onImage:(NativeChatFile)->Unit,onError:(String)->Unit){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val haptics=LocalHapticFeedback.current
    val swipeThreshold=with(LocalDensity.current){64.dp.toPx()}
                    val own = message.senderId == userId
                    var swipe by remember(message.id){mutableFloatStateOf(0f)}
                    val swipeOffset by animateFloatAsState(swipe,animationSpec=tween(110),label="Reply gesture")
                    val actions: @Composable () -> Unit = {
                        if(!message.unsent) Box {
                            DropdownMenu(expanded=menuOpen,onDismissRequest={onMenu(false)}) {
                                DropdownMenuItem(text={Text("Reply")},onClick={onReply();onMenu(false)})
                                DropdownMenuItem(text={Text("React")},onClick={onReact();onMenu(false)})
                                DropdownMenuItem(text={Text("Forward")},onClick={onForward();onMenu(false)})
                                if(message.body.isNotBlank())DropdownMenuItem(text={Text("Copy text")},onClick={onCopy();onMenu(false)})
                                if(own)DropdownMenuItem(text={Text("Unsend")},onClick={onUnsend();onMenu(false)})
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (own) Arrangement.End else Arrangement.Start,verticalAlignment=Alignment.Bottom) {
                        Box { SeraCompactMessageBubble(own=own,
                            color = if(message.unsent) inboxSoft else if (own) outgoing else incoming,
                            modifier = Modifier.widthIn(max = maxBubbleWidth).then(if(!message.unsent)Modifier.semantics{onLongClick(label="Message actions"){onMenu(true);true}}else Modifier).offset{IntOffset(swipeOffset.roundToInt(),0)}.then(if(!message.unsent)Modifier.pointerInput(message.id){detectTapGestures(onLongPress={onMenu(true)})}.pointerInput(message.id,own,swipeThreshold){detectHorizontalDragGestures(onDragCancel={swipe=0f},onDragEnd={if(kotlin.math.abs(swipe)>=swipeThreshold){onReply();haptics.performHapticFeedback(HapticFeedbackType.LongPress)};swipe=0f},onHorizontalDrag={change,amount->change.consume();swipe=if(own)(swipe+amount).coerceIn(-swipeThreshold*1.4f,0f)else(swipe+amount).coerceIn(0f,swipeThreshold*1.4f)})}else Modifier)) {
                                if(message.forwarded&&!message.unsent)Text("Forwarded",fontSize=10.sp,color=if(own)Color.White else accent)
                                if(message.replyId.isNotBlank()&&!message.unsent){val original=originalMessage;Surface(color=if(own)Color.Black.copy(alpha=.12f)else chatSurface,shape=RoundedCornerShape(9.dp),modifier=Modifier.padding(bottom=5.dp)){Row(Modifier.height(IntrinsicSize.Min)){Box(Modifier.width(3.dp).fillMaxHeight().background(if(own)Color(0xFFB5FFF4)else accent));Column(Modifier.padding(horizontal=8.dp,vertical=5.dp)){Text(if(original?.senderId==userId)"You" else peerName,fontSize=11.sp,fontWeight=FontWeight.SemiBold,color=if(own)Color(0xFFB5FFF4)else accent,maxLines=1);Text(if(original?.unsent==true)"Message unsent" else original?.body?.take(100)?:"Earlier message",fontSize=12.sp,color=if(own)Color.White else inboxInk,maxLines=2,overflow=TextOverflow.Ellipsis)}}}}
                                if(message.unsent||message.files.isEmpty()||message.body !in listOf("Attachment","Voice message"))Text(if(message.unsent) "Message unsent" else message.body,color=if(message.unsent)muted else if(own)Color.White else inboxInk,fontSize=14.sp,lineHeight=20.sp)
                                if(!message.unsent)SeraSharedLinkCards(message.body,if(own)Color.White else inboxInk,if(own)Color.White.copy(alpha=.12f)else chatSurface)
                                if(!message.unsent&&message.reactions.isNotEmpty()) Column(verticalArrangement=Arrangement.spacedBy(3.dp)){message.reactions.groupBy{it.second}.entries.chunked(3).forEach{group->Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(3.dp)){group.forEach{(emoji,people)->Surface(color=if(own)Color.White.copy(alpha=.16f)else chatSurface,shape=CircleShape){TextButton(onClick={onReaction(emoji)},contentPadding=PaddingValues(horizontal=5.dp,vertical=2.dp)){Text(emoji+" "+people.size,color=if(own)Color.White else inboxInk,fontSize=12.sp)}}}}}}

                                message.files.filter{!message.unsent}.forEach { file ->
                                    if(file.mime.startsWith("audio/")){SeraChatVoicePlayer(playback,file.path,file.durationMs,if(own)Color.White else accent,onPlay={scope.launch{playback.toggle(file.path,file.mime,{readChatFile(token,file)},onError)}})}else if(file.mime=="video/mp4"){SeraVideoAttachment(token,file,if(own)Color.White else accent,onAudioStart={playback.pause()})}else if(file.mime.startsWith("image/")){SeraPrivateImageCard(token,file,onOpen={onImage(file)},foreground=if(own)Color.White else inboxInk)}else{
                                    TextButton(onClick = { scope.launch {
                                        val result = withContext(Dispatchers.IO) { runCatching { downloadChatFile(context,token,file) } }
                                        result.onSuccess { uri -> runCatching { openChatFile(context,uri,file.mime) }.onFailure { onError("No app can open this file.") } }.onFailure { onError(it.message ?: "Could not open attachment.") }
                                    } }, colors = ButtonDefaults.textButtonColors(contentColor = if (own) Color.White else accent)) {
                                        Icon(Icons.Outlined.Description,null,Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text(file.name, maxLines=2)
                                    }
                                    Text("${file.bytes / 1024} KB · ${if (file.mime == "application/pdf") "PDF" else "Image"}",fontSize=11.sp,color=if(own) Color.White.copy(alpha=.8f) else inboxInk)}
                                }
                                val time = runCatching { java.time.Instant.parse(message.createdAt).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) }.getOrDefault("")
                                val read = message.readAt.isNotBlank() && message.readAt != "null"
                                val delivered = read || (message.deliveredAt.isNotBlank() && message.deliveredAt != "null")
                                SeraCompactReceipt(time,own&&!message.unsent,delivered,read,if(own&&!message.unsent)Color.White.copy(alpha=.8f)else muted)
                        }

                        actions() }
                    }
}
