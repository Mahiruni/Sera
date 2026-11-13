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
import androidx.compose.material.icons.outlined.Reply
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Forward
import androidx.compose.material.icons.outlined.SentimentSatisfiedAlt
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
internal fun SeraNativeMessageRow(message:NativeMessage,originalMessage:NativeMessage?,userId:String,peerName:String,token:String,playback:SeraChatPlayback,maxBubbleWidth:androidx.compose.ui.unit.Dp,inboxInk:Color,inboxSoft:Color,chatSurface:Color,muted:Color,accent:Color,outgoing:Color,incoming:Color,menuOpen:Boolean,onMenu:(Boolean)->Unit,onReply:()->Unit,onReact:()->Unit,onForward:()->Unit,onCopy:()->Unit,onUnsend:()->Unit,onReaction:(String)->Unit,onImage:(NativeChatFile)->Unit,onError:(String)->Unit,conversationId:String="",onLibraryChanged:()->Unit={},readOnly:Boolean=false,onQuote:(String)->Unit={},highlighted:Boolean=false,selectionMode:Boolean=false,selected:Boolean=false,onSelect:()->Unit={}){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val haptics=LocalHapticFeedback.current
    val swipeThreshold=with(LocalDensity.current){48.dp.toPx()}
                    val own = message.senderId == userId
                    var swipe by remember(message.id){mutableFloatStateOf(0f)}
                    val swipeOffset by animateFloatAsState(swipe,animationSpec=tween(if(swipe==0f)150 else 0),label="Reply gesture")
                    var armed by remember(message.id){mutableStateOf(false)}
                    val latestReply by rememberUpdatedState(onReply)
                    val latestReaction by rememberUpdatedState(onReaction)
                    val latestMenu by rememberUpdatedState(onMenu)
                    val highlight by animateFloatAsState(if(highlighted)1f else 0f,tween(240),label="Message focus")
                    var info by remember(message.id){mutableStateOf(false)}
                    if(info)SeraMessageInfo(message,own,onDismiss={info=false})
                    var editOpen by remember(message.id){mutableStateOf(false)}
                    if(editOpen)SeraEditMessageDialog(token,message,onDismiss={editOpen=false},onSaved={editOpen=false;onLibraryChanged()})
                    val actions: @Composable () -> Unit = {
                        if(!message.unsent&&!readOnly&&!selectionMode) Box {
                            DropdownMenu(expanded=menuOpen,onDismissRequest={onMenu(false)}) {
                                Row(Modifier.padding(horizontal=8.dp,vertical=5.dp).width(240.dp),horizontalArrangement=Arrangement.SpaceEvenly){listOf("👍","❤️","😂","😮","😢","🔥").forEach{emoji->TextButton(onClick={onReaction(emoji);onMenu(false)},contentPadding=PaddingValues(0.dp),modifier=Modifier.size(38.dp).semantics{contentDescription="React "+emoji}){Text(emoji,fontSize=20.sp)}}}
                                HorizontalDivider(Modifier.padding(vertical=4.dp))
                                DropdownMenuItem(leadingIcon={Icon(Icons.Outlined.Check,null)},text={Text("Select message")},onClick={onMenu(false);onSelect()})
                                DropdownMenuItem(leadingIcon={Icon(Icons.Outlined.Reply,null)},text={Text("Reply")},onClick={onReply();onMenu(false)})
                                DropdownMenuItem(leadingIcon={Icon(Icons.Outlined.SentimentSatisfiedAlt,null)},text={Text("React")},onClick={onReact();onMenu(false)})
                                DropdownMenuItem(leadingIcon={Icon(Icons.Outlined.Forward,null)},text={Text("Forward")},onClick={onForward();onMenu(false)})
                                if(message.body.isNotBlank())DropdownMenuItem(leadingIcon={Icon(Icons.Outlined.ContentCopy,null)},text={Text("Copy text")},onClick={onCopy();onMenu(false)})
                                if(conversationId.isNotBlank())SeraMessageLibraryActions(token,conversationId,message,userId,menuOpen,onClose={onMenu(false)},onChanged=onLibraryChanged,onError=onError)
                                if(own&&message.files.isEmpty()&&seraCanEdit(message.createdAt))DropdownMenuItem(leadingIcon={Icon(Icons.Outlined.Edit,null)},text={Text("Edit message")},onClick={editOpen=true;onMenu(false)})
                                if(own)DropdownMenuItem(leadingIcon={Icon(Icons.Outlined.Info,null)},text={Text("Message info")},onClick={info=true;onMenu(false)})
                                if(own)DropdownMenuItem(leadingIcon={Icon(Icons.Outlined.DeleteOutline,null,tint=MaterialTheme.colorScheme.error)},text={Text("Unsend",color=MaterialTheme.colorScheme.error)},onClick={onUnsend();onMenu(false)})
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().then(if(selectionMode)Modifier.background(if(selected)accent.copy(alpha=.12f)else Color.Transparent).clickable(enabled=!message.unsent,onClick=onSelect)else Modifier), horizontalArrangement = if (own) Arrangement.End else Arrangement.Start,verticalAlignment=Alignment.Bottom) {
                        if(selectionMode)Checkbox(checked=selected,onCheckedChange={onSelect()},enabled=!message.unsent,modifier=Modifier.align(Alignment.CenterVertically))
                        Box {
                            if(kotlin.math.abs(swipe)>4f)Box(Modifier.align(if(own)Alignment.CenterEnd else Alignment.CenterStart).size(36.dp),contentAlignment=Alignment.Center){Icon(Icons.Outlined.Reply,null,tint=accent.copy(alpha=(kotlin.math.abs(swipe)/swipeThreshold).coerceIn(.25f,1f)),modifier=Modifier.size(20.dp))}
                            SeraCompactMessageBubble(own=own,
                            color = if(message.unsent) inboxSoft else if (own) outgoing else incoming,
                            modifier = Modifier.widthIn(max = if(selectionMode)maxBubbleWidth*.86f else maxBubbleWidth).testTag("sera_message_"+message.id).then(if(highlight>0f)Modifier.border(2.dp,accent.copy(alpha=highlight),RoundedCornerShape(18.dp))else Modifier).then(if(!message.unsent&&!readOnly&&!selectionMode)Modifier.semantics{onLongClick(label="Message actions"){onMenu(true);true};customActions=listOf(CustomAccessibilityAction("Reply"){onReply();true},CustomAccessibilityAction("Quick reaction"){onReaction("❤️");true})}else Modifier).offset{IntOffset(swipeOffset.roundToInt(),0)}.then(if(!message.unsent&&!readOnly&&!selectionMode)Modifier.pointerInput(message.id){detectTapGestures(onLongPress={latestMenu(true)},onDoubleTap={haptics.performHapticFeedback(HapticFeedbackType.LongPress);latestReaction("❤️")})}.pointerInput(message.id,own,swipeThreshold){detectHorizontalDragGestures(onDragStart={armed=false},onDragCancel={swipe=0f;armed=false},onDragEnd={if(kotlin.math.abs(swipe)>=swipeThreshold)latestReply();swipe=0f;armed=false},onHorizontalDrag={change,amount->change.consume();swipe=seraReplyDrag(swipe,amount,own,swipeThreshold);val reached=kotlin.math.abs(swipe)>=swipeThreshold;if(reached&&!armed)haptics.performHapticFeedback(HapticFeedbackType.LongPress);armed=reached})}else Modifier)) {
                                if(message.forwarded&&!message.unsent)Text("Forwarded",fontSize=10.sp,color=if(own)Color.White else accent)
                                if(message.replyId.isNotBlank()&&!message.unsent){val original=originalMessage;Surface(color=if(own)Color.Black.copy(alpha=.12f)else chatSurface,shape=RoundedCornerShape(9.dp),modifier=Modifier.padding(bottom=5.dp).testTag("sera_quote_"+message.id).then(if(!readOnly&&!selectionMode)Modifier.clickable{onQuote(message.replyId)}else Modifier)){Row(Modifier.height(IntrinsicSize.Min)){Box(Modifier.width(3.dp).fillMaxHeight().background(if(own)Color(0xFFB5FFF4)else accent));Column(Modifier.padding(horizontal=8.dp,vertical=5.dp)){Text(if(original?.senderId==userId)"You" else peerName,fontSize=11.sp,fontWeight=FontWeight.SemiBold,color=if(own)Color(0xFFB5FFF4)else accent,maxLines=1);Text(if(original?.unsent==true)"Message unsent" else original?.body?.take(100)?:"Earlier message",fontSize=12.sp,color=if(own)Color.White else inboxInk,maxLines=2,overflow=TextOverflow.Ellipsis)}}}}
                                if(message.unsent||message.files.isEmpty()||message.body !in listOf("Attachment","Voice message"))SeraRichMessageText(if(message.unsent) "Message unsent" else message.body,color=if(message.unsent)muted else if(own)Color.White else inboxInk,linkColor=if(own)Color(0xFFB5FFF4)else accent)
                                if(!message.unsent)SeraSharedLinkCards(message.body,if(own)Color.White else inboxInk,if(own)Color.White.copy(alpha=.12f)else chatSurface)
                                if(!message.unsent&&message.reactions.isNotEmpty()) Column(verticalArrangement=Arrangement.spacedBy(3.dp)){message.reactions.groupBy{it.second}.entries.chunked(3).forEach{group->Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(3.dp)){group.forEach{(emoji,people)->Surface(color=if(own)Color.White.copy(alpha=.16f)else chatSurface,shape=CircleShape){TextButton(enabled=!readOnly&&!selectionMode,onClick={onReaction(emoji)},contentPadding=PaddingValues(horizontal=5.dp,vertical=2.dp)){Text(emoji+" "+people.size,color=if(own)Color.White else inboxInk,fontSize=12.sp)}}}}}}

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
                                if(message.editedAt.isNotBlank()&&!message.unsent)Text("Edited",fontSize=10.sp,color=if(own)Color.White.copy(alpha=.8f)else muted)
                                SeraCompactReceipt(time,own&&!message.unsent,delivered,read,if(own&&!message.unsent)Color.White.copy(alpha=.8f)else muted)
                        }

                        actions() }
                    }
}

internal fun seraReplyDrag(current:Float,delta:Float,own:Boolean,threshold:Float):Float = if(own)(current+delta).coerceIn(-threshold*1.4f,0f) else (current+delta).coerceIn(0f,threshold*1.4f)
