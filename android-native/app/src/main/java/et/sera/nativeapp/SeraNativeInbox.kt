package et.sera.nativeapp

import androidx.compose.ui.platform.testTag

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
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.clickable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.gestures.scrollBy
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val inboxTeal = Color(0xFF087E83)
private val inboxInk = Color(0xFF183B40)
private val inboxSoft = Color(0xFFF1F7F6)
/** Drafts are scoped to the signed-in user and individual conversation. */
private class SeraInboxDrafts(context: Context) {
    private val preferences = EncryptedSharedPreferences.create(
        context, "sera_inbox_drafts",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    fun read(userId: String, conversationId: String): String =
        preferences.getString("$userId:$conversationId", "").orEmpty()
    fun write(userId: String, conversationId: String, text: String) {
        preferences.edit().putString("$userId:$conversationId", text).apply()
    }
    fun clear(userId: String, conversationId: String) {
        preferences.edit().remove("$userId:$conversationId").apply()
    }
}

internal data class NativeConversation(val id: String, val peerId: String, val peerName: String, val preview:String="", val unread:Int=0, val photo:String?=null, val lastAt:String="",val pinned:Boolean=false,val archived:Boolean=false,val markedUnread:Boolean=false,val mutedUntil:String?=null)
internal data class NativeMessage(val id: String, val senderId: String, val body: String, val readAt: String, val createdAt: String, val deliveredAt: String, val files: List<NativeChatFile>, val replyId:String="", val forwarded:Boolean=false, val unsent:Boolean=false, val clientId:String="", val reactions:List<Pair<String,String>> = emptyList(),val privateReadAt:String="",val editedAt:String="")

private fun inboxRequest(token: String, path: String, method: String = "GET", body: JSONObject? = null): Pair<String?, String> {
    if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank())
        return null to "Inbox is not configured."
    var conn: HttpURLConnection? = null
    return try {
        conn = (URL(BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/" + path)
            .openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12000
            readTimeout = 16000
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Prefer", if (path == "professional_messages" && method == "POST") "return=representation" else "return=minimal")
            }
        }
        if (body != null) conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val status = conn.responseCode
        if (status !in 200..299) {
            return null to when (status) {
                401, 403 -> "Your session cannot access this conversation."
                else -> "Request failed (HTTP $status). Please retry."
            }
        }
        (conn.inputStream?.bufferedReader()?.use { it.readText() } ?: "[]") to ""
    } catch (_: Exception) {
        null to "Connection failed. Check your network and retry."
    } finally { conn?.disconnect() }
}

internal fun resolveNativeMessagePeer(token:String,userId:String,conversation:String):String? {
    if(!SeraMessageAlerts.validId(conversation))return null
    val raw=inboxRequest(token,"professional_conversations?select=member_a,member_b&id=eq.$conversation&limit=1").first?:return null
    return runCatching{val row=JSONArray(raw).optJSONObject(0)?:return null;val a=row.optString("member_a");val b=row.optString("member_b");when(userId){a->b;b->a;else->null}}.getOrNull()
}

private suspend fun loadNativeConversations(token: String, userId: String, prior:List<NativeConversation>, onCore:suspend (List<NativeConversation>)->Unit): Pair<List<NativeConversation>?, String> = coroutineScope {
    val controlsRequest=async(Dispatchers.IO){inboxRequest(token,"sera_conversation_preferences?select=conversation_id,pinned,archived,marked_unread,muted_until&user_id=eq.$userId")}
    val (raw, error) = inboxRequest(token,
        "rpc/sera_inbox_v2","POST",JSONObject().put("p_limit",100))
    if (raw == null){controlsRequest.cancel();return@coroutineScope null to error}
    try {
        val rows = JSONArray(raw)
        val peers = (0 until rows.length()).map { rows.getJSONObject(it) }
        val ids = peers.map { if (it.optString("member_a") == userId) it.optString("member_b") else it.optString("member_a") }
        val names = mutableMapOf<String, String>()
        val paths = mutableMapOf<String, String>()
        val photos = mutableMapOf<String, String>()
        val cardRequest=async(Dispatchers.IO){if(ids.isEmpty()) "[]" to "" else inboxRequest(token,"rpc/member_cards","POST",JSONObject().put("ids",JSONArray(ids.distinct())))}
        val controlResult=controlsRequest.await()
        val priorById=prior.associateBy{it.id}
        val controls=controlResult.first?.let{rawControls->runCatching{val rows=JSONArray(rawControls);(0 until rows.length()).map{rows.getJSONObject(it)}.associateBy{it.getString("conversation_id")}}.getOrNull()}
        // Only authorized inbox rows are published; retain previously known names and preferences.
        fun buildRows():List<NativeConversation> = peers.map { row ->
            val id=if(row.optString("member_a")==userId)row.optString("member_b") else row.optString("member_a")
            val old=priorById[row.optString("id")]?.takeIf{it.peerId==id}
            val control=controls?.get(row.optString("id"))
            NativeConversation(row.optString("id"),id,names[id]?.takeIf{it.isNotBlank()&&it!="null"}?:old?.peerName?:"Sera member",
                row.optString("last_body").takeUnless{it=="null"}.orEmpty(),row.optInt("unread_count"),photos[id]?:old?.photo,
                row.optString("last_created_at").takeUnless{it=="null"}.orEmpty(),
                control?.optBoolean("pinned")?:if(controls==null)old?.pinned?:false else false,
                control?.optBoolean("archived")?:if(controls==null)old?.archived?:false else false,
                control?.optBoolean("marked_unread")?:if(controls==null)old?.markedUnread?:false else false,
                if(controls==null)old?.mutedUntil else control?.optString("muted_until")?.takeUnless{it=="null"||it.isBlank()})
        }
        onCore(buildRows())
        if (ids.isNotEmpty()) {
            val (cards, _) = cardRequest.await()
            if (cards != null) {
                val entries = JSONArray(cards)
                for (i in 0 until entries.length()) {
                    val entry = entries.getJSONObject(i)
                    names[entry.optString("id")] = entry.optString("full_name")
                    entry.optString("avatar_path").takeIf{it.isNotBlank()&&it!="null"&&it.matches(Regex("[a-zA-Z0-9/_.-]+"))&&!it.contains("..")}?.let{paths[entry.optString("id")]=it}
                }
            }
        }
        onCore(buildRows())
        if(paths.isNotEmpty()) runCatching {
            val base=BuildConfig.SUPABASE_URL.trimEnd('/')
            val connection=(URL(base+"/storage/v1/object/sign/sera-account-private").openConnection() as HttpURLConnection).apply{requestMethod="POST";doOutput=true;connectTimeout=10000;readTimeout=10000;setRequestProperty("apikey",BuildConfig.SUPABASE_ANON_KEY);setRequestProperty("Authorization","Bearer $token");setRequestProperty("Content-Type","application/json")}
            try{connection.outputStream.use{it.write(JSONObject().put("paths",JSONArray(paths.values.toList())).put("expiresIn",300).toString().toByteArray())};if(connection.responseCode in 200..299){val signed=JSONArray(connection.inputStream.bufferedReader().use{it.readText()});for(i in 0 until signed.length()){val item=signed.getJSONObject(i);val path=item.optString("path");val url=item.optString("signedURL").ifBlank{item.optString("signedUrl")};if(url.startsWith("/object/sign/")){paths.entries.filter{it.value==path}.forEach{photos[it.key]=base+"/storage/v1"+url}}}}}finally{connection.disconnect()}
        }
        buildRows() to ""
    } catch (cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
    catch (_: Exception) { null to "Unable to display conversations." }
}

private fun loadNativeMessages(token: String, conversationId: String, limit:Int=100, ids:List<String>?=null,cache:SeraMessageHistoryCache?=null,owner:String=""): Pair<List<NativeMessage>?, String> {
    val (raw, error) = inboxRequest(token,
        "professional_messages?select=id,sender_id,body,read_at,created_at,delivered_at,reply_to_id,is_forwarded,unsent_at,client_message_id,edited_at,sera_private_message_reads(read_at),professional_message_reactions(member_id,emoji),professional_message_attachments(file_name,mime_type,storage_path,byte_size,duration_ms,position)&conversation_id=eq.$conversationId&order=created_at.desc,id.desc&limit=$limit"+(ids?.let{"&id=in.("+it.joinToString(",")+")"}?:""))
    if (raw == null) return null to error
    val parsed=parseNativeMessageRows(raw)
    if(parsed.first!=null&&ids==null)runCatching{cache?.write(owner,conversationId,raw)}
    return parsed
}
private fun parseNativeMessageRows(raw:String):Pair<List<NativeMessage>?,String>{
    return try {
        val rows = JSONArray(raw)
        (0 until rows.length()).map { i ->
            val item = rows.getJSONObject(i)
            val attachments = item.optJSONArray("professional_message_attachments") ?: JSONArray()
            val files = (0 until attachments.length()).map{attachments.getJSONObject(it)}.sortedBy{it.optInt("position")}.map { a -> NativeChatFile(a.optString("file_name"),a.optString("mime_type"),a.optString("storage_path"),a.optInt("byte_size"),a.optInt("duration_ms").takeIf{it>0}) }
            NativeMessage(item.optString("id"), item.optString("sender_id"), item.optString("body"), item.optString("read_at"), item.optString("created_at"), item.optString("delivered_at"), files,item.optString("reply_to_id").takeUnless{it=="null"}.orEmpty(),item.optBoolean("is_forwarded"),!item.isNull("unsent_at"),item.optString("client_message_id"),(item.optJSONArray("professional_message_reactions")?:JSONArray()).let{rs->(0 until rs.length()).map{rs.getJSONObject(it)}.filter{!it.isNull("emoji")}.map{it.optString("member_id") to it.optString("emoji")}},privateReadAt=item.optJSONObject("sera_private_message_reads")?.optString("read_at")?:item.optJSONArray("sera_private_message_reads")?.optJSONObject(0)?.optString("read_at").orEmpty(),editedAt=item.optString("edited_at").takeUnless{it=="null"}.orEmpty())
        }.asReversed() to ""
    } catch (_: Exception) { null to "Unable to display messages." }
}

internal fun loadNativeMessageContext(token:String,thread:String,target:String):Pair<List<NativeMessage>?,String>{
    if(!SeraMessageAlerts.validId(thread)||!SeraMessageAlerts.validId(target))return null to "Message unavailable."
    val r=chatRpc(token,"sera_message_context",JSONObject().put("p_conversation",thread).put("p_message",target).put("p_radius",20))
    if(r.first==null)return null to r.second
    val ids=runCatching{val a=JSONArray(r.first);(0 until a.length()).map{a.getJSONObject(it).getString("id")}.filter{SeraMessageAlerts.validId(it)}}.getOrDefault(emptyList())
    if(target !in ids)return null to "Message unavailable or removed."
    val result=loadNativeMessages(token,thread,ids.size,ids)
    return if(result.first?.any{it.id==target&&!it.unsent}==true)result else null to (result.second.ifBlank{"Message unavailable or removed."})
}

private fun markNativeMessagesRead(token: String, conversationId: String, userId: String) {
    if (conversationId.isBlank() || userId.isBlank()) return
    var conn: HttpURLConnection? = null
    try {
        val path = "professional_messages?conversation_id=eq.$conversationId&sender_id=neq.$userId&read_at=is.null"
        conn = (URL(BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/" + path)
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "PATCH"
            doOutput = true
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
        }
        conn.outputStream.use {
            it.write(JSONObject().put("read_at", java.time.Instant.now().toString()).toString().toByteArray())
        }
        conn.responseCode
    } catch (_: Exception) {
        // Reading the conversation remains possible even if receipt sync fails.
    } finally { conn?.disconnect() }
}

@Composable
fun SeraNativeInbox(token: String, userId: String, modifier: Modifier = Modifier, openCompany: Boolean=false, onCompanyConsumed:()->Unit={}, initialThread:String?=null, initialPeer:String="", initialName:String="", onThreadExit:(()->Unit)?=null, onThreadVisibility:(Boolean)->Unit={}, onInboxExit:(()->Unit)?=null,overlayOnPage:Boolean=false) {
    val dark=androidx.compose.foundation.isSystemInDarkTheme()
    val palette=seraChatPalette(dark)
    val scheme=seraChatScheme(if(dark)darkColorScheme()else lightColorScheme(),palette,dark)
    MaterialTheme(colorScheme=scheme,typography=seraChatTypography(MaterialTheme.typography),shapes=MaterialTheme.shapes){
        SeraMessagingWrapper(modifier,palette,overlayOnPage,onInboxExit) {
            SeraNativeInboxContent(token,userId,Modifier.fillMaxSize(),openCompany,onCompanyConsumed,initialThread,initialPeer,initialName,onThreadExit,onThreadVisibility,onInboxExit)
        }
    }
}

/** Keep keyboard expansion separate from the user's chosen sheet position. */
internal fun seraMessagingHeightFraction(keyboardVisible:Boolean,restingFraction:Float=1f):Float = if(keyboardVisible)1f else restingFraction.coerceIn(.60f,1f)

internal fun seraMessagingDragFraction(current:Float,deltaPx:Float,availablePx:Float):Float =
    if(availablePx<=0f)current else (current-deltaPx/availablePx).coerceIn(.60f,1f)

internal fun seraMessagingSnapFraction(current:Float,velocityPx:Float,flingThresholdPx:Float):Float {
    val anchors=listOf(.60f,.90f,1f)
    return when {
        velocityPx < -flingThresholdPx -> anchors.firstOrNull{it>current+.005f}?:1f
        velocityPx > flingThresholdPx -> anchors.lastOrNull{it<current-.005f}?:.60f
        else -> anchors.minByOrNull{kotlin.math.abs(it-current)}?:.90f
    }
}

@Composable
internal fun SeraMessagingWrapper(modifier:Modifier,palette:SeraChatPalette,overlayOnPage:Boolean=false,onDismiss:(()->Unit)?=null,content:@Composable ()->Unit) {
    val density=LocalDensity.current
    val keyboardVisible=WindowInsets.ime.getBottom(density)>0
    BackHandler(enabled=overlayOnPage&&!keyboardVisible&&onDismiss!=null){onDismiss?.invoke()}
    var restingFraction by rememberSaveable{mutableStateOf(1f)}
    var dragFraction by remember{mutableStateOf(1f)}
    var dragging by remember{mutableStateOf(false)}
    val target=seraMessagingHeightFraction(keyboardVisible,if(dragging)dragFraction else restingFraction)
    val animatedFraction by animateFloatAsState(target,tween(if(dragging||keyboardVisible)0 else 210),label="messaging sheet height")
    LaunchedEffect(keyboardVisible){if(keyboardVisible)dragging=false}
    BoxWithConstraints(modifier.fillMaxSize().then(if(overlayOnPage)Modifier else Modifier.background(palette.quote)).imePadding()){
        // Full height by default; the underlying destination remains mounted for intentional collapse.
        if(overlayOnPage)Box(Modifier.matchParentSize().then(if(onDismiss!=null)Modifier.clickable(indication=null,interactionSource=remember{androidx.compose.foundation.interaction.MutableInteractionSource()}){onDismiss()} else Modifier.pointerInput(Unit){detectTapGestures{}}))
        val availablePx=with(density){maxHeight.toPx()}
        val flingThreshold=with(density){420.dp.toPx()}
        val dragState=rememberDraggableState{delta->
            if(!keyboardVisible)dragFraction=seraMessagingDragFraction(dragFraction,delta,availablePx)
        }
        val fullHeight=keyboardVisible||animatedFraction>=.999f
        Surface(
            modifier=Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(if(keyboardVisible)1f else animatedFraction).then(if(overlayOnPage)Modifier.semantics{contentDescription="Messages sheet"} else Modifier),
            shape=RoundedCornerShape(topStart=if(fullHeight)0.dp else 28.dp,topEnd=if(fullHeight)0.dp else 28.dp),
            color=palette.canvas,
            tonalElevation=0.dp,
            shadowElevation=if(fullHeight)0.dp else 5.dp
        ){
            Column(Modifier.fillMaxSize().then(if(keyboardVisible)Modifier else Modifier.navigationBarsPadding())){
                if(!keyboardVisible)Box(
                    Modifier.fillMaxWidth().height(28.dp).background(palette.header)
                        .semantics{contentDescription="Resize messages";stateDescription=when{restingFraction<.8f->"Compact";restingFraction<.95f->"Default";else->"Expanded"}}
                        .draggable(
                            state=dragState,orientation=Orientation.Vertical,
                            onDragStarted={dragFraction=animatedFraction;dragging=true},
                            onDragStopped={velocity->restingFraction=seraMessagingSnapFraction(dragFraction,velocity,flingThreshold);dragging=false}
                        )
                        .clickable{restingFraction=if(restingFraction>=.95f).90f else 1f},
                    contentAlignment=Alignment.Center
                ){
                    Box(Modifier.size(width=38.dp,height=5.dp).clip(CircleShape).background(palette.muted.copy(alpha=.55f)))
                }
                Box(Modifier.weight(1f).fillMaxWidth()){content()}
            }
        }
    }
}
@Composable
private fun SeraNativeInboxContent(token:String,userId:String,modifier:Modifier,openCompany:Boolean,onCompanyConsumed:()->Unit,initialThread:String?,initialPeer:String,initialName:String,onThreadExit:(()->Unit)?,onThreadVisibility:(Boolean)->Unit,onInboxExit:(()->Unit)?){
    var companyInbox by remember { mutableStateOf(false) }
    LaunchedEffect(openCompany){if(openCompany){companyInbox=true;onCompanyConsumed()}}
    if(companyInbox){Box(modifier.fillMaxSize()){SeraCompanyInbox(token,userId,onBack={companyInbox=false})};return}
    val context = LocalContext.current
    // Fail safely if encrypted storage is temporarily unavailable; never
    // downgrade private message drafts to unencrypted preferences.
    val savedDrafts = remember(context) { runCatching { SeraInboxDrafts(context) }.getOrNull() }
    val global = remember { InboxGlobalState(context) }
    val selection = remember(initialThread) { InboxSelectionState(initialThread, initialPeer, initialName, savedDrafts, userId) }
    val thread = remember(selection.selected?.id) { InboxThreadState() }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val messageListState = key(selection.selected?.id) { rememberLazyListState() }
    val scope = rememberCoroutineScope()
    val playback = remember(context, selection.selected?.id) { SeraChatPlayback(context.applicationContext) }
    val outbox=remember(context){runCatching{NativeChatOutbox(context)}.getOrNull()}
    LaunchedEffect(selection.selected?.id) { onThreadVisibility(selection.selected != null) }
    DisposableEffect(Unit) { onDispose { onThreadVisibility(false) } }
    DisposableEffect(userId,selection.selected?.id,lifecycle){
        val threadId=selection.selected?.id
        fun sync(){SeraMessageAlerts.setVisible(context,userId,threadId.takeIf{lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)})}
        val observer=androidx.lifecycle.LifecycleEventObserver{_,_->sync()}
        lifecycle.addObserver(observer);sync()
        onDispose{lifecycle.removeObserver(observer);SeraMessageAlerts.clearVisible(userId,threadId)}
    }
    val controller = InboxController(global, selection, thread, token, userId, context, savedDrafts, lifecycle, messageListState, scope, playback, outbox, onThreadExit)
    controller.PresenceAndScroll()
    controller.KeyboardScroll()
    controller.JumpToMessage()
    controller.ConnectionAndPlayback()
    controller.TransferEffects()
    controller.TextEffects()
    controller.Dialogs()
    controller.ConversationPolling()
    controller.MessagePolling()
    controller.ReadReceipts()
    controller.TypingEffects()
    controller.Render(modifier, onInboxExit, onCompany = { companyInbox = true })
}

// Keep snapshot state in independently remembered holders and split the effects
// into separate composable functions to bound Kotlin FIR data-flow analysis.
private class InboxGlobalState(context: Context) {
    var conversations by mutableStateOf<List<NativeConversation>>(emptyList())
    var globalStars by mutableStateOf(false)
    var historicalTarget by mutableStateOf<Pair<String,String>?>(null)
    var messages by mutableStateOf<List<NativeMessage>>(emptyList())
    var error by mutableStateOf("")
    var sending by mutableStateOf(false)
    var loading by mutableStateOf(true)
    var refresh by mutableIntStateOf(0)
    var search by mutableStateOf("")
    var peerTyping by mutableStateOf(false)
    var typingPulse by mutableIntStateOf(0)
    var lastVisibleConversation by mutableStateOf<String?>(null)
    var lastMessageId by mutableStateOf<String?>(null)
    var checkingFile by mutableStateOf(false)
    var forwardClient by mutableStateOf("")
    var sound by mutableStateOf(context.getSharedPreferences("sera_chat_settings",0).getBoolean("sound",true))
    var connectionAvailable by mutableStateOf(true)
}

private class InboxSelectionState(initialThread: String?, initialPeer: String, initialName: String, savedDrafts: SeraInboxDrafts?, userId: String) {
    var selected by mutableStateOf(initialThread?.let{NativeConversation(it,initialPeer,initialName)})
    var draft by mutableStateOf(initialThread?.let{savedDrafts?.read(userId,it)}.orEmpty())
}

private class InboxThreadState {
    val syncMutex=Mutex()
    val deliveryInFlight=mutableSetOf<String>()
    var manualSyncJob:kotlinx.coroutines.Job?=null
    var manualSyncRequested=false
    var callEvents by mutableStateOf<List<SeraCallEvent>>(emptyList())
    var peerActivity by mutableStateOf<String?>(null)
    var attachment by mutableStateOf<NativeChatSelection?>(null)
    var album by mutableStateOf<List<NativeChatSelection>>(emptyList())
    var transferProgress by mutableIntStateOf(-1)
    var activeTransfer by mutableStateOf<NativeChatTransfer?>(null)
    var mediaViewer by mutableStateOf<NativeChatFile?>(null)
    var recordingVoice by mutableStateOf(false)
    var reply by mutableStateOf<NativeMessage?>(null)
    var menu by mutableStateOf<String?>(null)
    var reaction by mutableStateOf<NativeMessage?>(null)
    var remove by mutableStateOf<NativeMessage?>(null)
    var forwarding by mutableStateOf<NativeMessage?>(null)
    var historyLimit by mutableIntStateOf(SERA_INITIAL_MESSAGE_WINDOW)
    var messageSearch by mutableStateOf("")
    var showSearch by mutableStateOf(false)
    var sendFailed by mutableStateOf(false)
    var queued by mutableStateOf<NativePending?>(null)
    var retrySignal by mutableIntStateOf(0)
    var attachmentStudio by mutableStateOf(false)
    var textQueue by mutableStateOf<List<NativeQueuedText>>(emptyList())
    var textAcknowledged by mutableStateOf<List<NativeMessage>>(emptyList())
    var textLoaded by mutableStateOf(false)
    var unreadBoundary by mutableStateOf<String?>(null)
    var cachedHistory by mutableStateOf(false)
    var safetyDialog by mutableStateOf(false)
    var initialMessagesLoaded by mutableStateOf(false)
    var initialScrollComplete by mutableStateOf(false)
    var detailsOpen by mutableStateOf(false)
    var libraryMode by mutableStateOf<String?>(null)
    var scheduledOpen by mutableStateOf(false)
    var selectedMessages by mutableStateOf<Set<String>>(emptySet())
    var bulkForward by mutableStateOf(false)
    var bulkBusy by mutableStateOf(false)
    var scheduleDraft by mutableStateOf<String?>(null)
    var libraryRevision by mutableIntStateOf(0)
    var jumpMessageId by mutableStateOf<String?>(null)
    var highlightedId by mutableStateOf<String?>(null)
    val reactionBusy=mutableSetOf<String>()
    var suppressImeAnchor by mutableStateOf(false)
}

private class InboxController(
    private val global: InboxGlobalState,
    private val selection: InboxSelectionState,
    private val thread: InboxThreadState,
    private val token: String,
    private val userId: String,
    private val context: Context,
    private val savedDrafts: SeraInboxDrafts?,
    private val lifecycle: androidx.lifecycle.Lifecycle,
    private val messageListState: androidx.compose.foundation.lazy.LazyListState,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val playback: SeraChatPlayback,
    private val outbox: NativeChatOutbox?,
    private val onThreadExit: (() -> Unit)?
) {
    var conversations
        get() = global.conversations
        set(value) { global.conversations = value }
    var messages
        get() = global.messages
        set(value) { global.messages = value }
    var error
        get() = global.error
        set(value) { global.error = value }
    var sending
        get() = global.sending
        set(value) { global.sending = value }
    var loading
        get() = global.loading
        set(value) { global.loading = value }
    var refresh
        get() = global.refresh
        set(value) { global.refresh = value }
    var search
        get() = global.search
        set(value) { global.search = value }
    var peerTyping
        get() = global.peerTyping
        set(value) { global.peerTyping = value }
    var typingPulse
        get() = global.typingPulse
        set(value) { global.typingPulse = value }
    var lastVisibleConversation
        get() = global.lastVisibleConversation
        set(value) { global.lastVisibleConversation = value }
    var lastMessageId
        get() = global.lastMessageId
        set(value) { global.lastMessageId = value }
    var checkingFile
        get() = global.checkingFile
        set(value) { global.checkingFile = value }
    var forwardClient
        get() = global.forwardClient
        set(value) { global.forwardClient = value }
    var sound
        get() = global.sound
        set(value) { global.sound = value }
    var connectionAvailable
        get() = global.connectionAvailable
        set(value) { global.connectionAvailable = value }
    var selected
        get() = selection.selected
        set(value) { selection.selected = value }
    var draft
        get() = selection.draft
        set(value) { selection.draft = value }
    var callEvents
        get() = thread.callEvents
        set(value) { thread.callEvents = value }
    var peerActivity
        get() = thread.peerActivity
        set(value) { thread.peerActivity = value }
    var attachment
        get() = thread.attachment
        set(value) { thread.attachment = value }
    var album
        get() = thread.album
        set(value) { thread.album = value }
    var transferProgress
        get() = thread.transferProgress
        set(value) { thread.transferProgress = value }
    var activeTransfer
        get() = thread.activeTransfer
        set(value) { thread.activeTransfer = value }
    var mediaViewer
        get() = thread.mediaViewer
        set(value) { thread.mediaViewer = value }
    var recordingVoice
        get() = thread.recordingVoice
        set(value) { thread.recordingVoice = value }
    var reply
        get() = thread.reply
        set(value) { thread.reply = value }
    var menu
        get() = thread.menu
        set(value) { thread.menu = value }
    var reaction
        get() = thread.reaction
        set(value) { thread.reaction = value }
    var remove
        get() = thread.remove
        set(value) { thread.remove = value }
    var forwarding
        get() = thread.forwarding
        set(value) { thread.forwarding = value }
    var historyLimit
        get() = thread.historyLimit
        set(value) { thread.historyLimit = value }
    var messageSearch
        get() = thread.messageSearch
        set(value) { thread.messageSearch = value }
    var showSearch
        get() = thread.showSearch
        set(value) { thread.showSearch = value }
    var sendFailed
        get() = thread.sendFailed
        set(value) { thread.sendFailed = value }
    var queued
        get() = thread.queued
        set(value) { thread.queued = value }
    var retrySignal
        get() = thread.retrySignal
        set(value) { thread.retrySignal = value }
    var attachmentStudio
        get() = thread.attachmentStudio
        set(value) { thread.attachmentStudio = value }

    fun chime(){if(sound)runCatching{val tone=ToneGenerator(AudioManager.STREAM_NOTIFICATION,35);tone.startTone(ToneGenerator.TONE_PROP_BEEP,120);android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({tone.release()},180)}}
    fun loadEarlier(){
        if(historyLimit>=1000||!connectionAvailable)return
        historyLimit=(historyLimit+100).coerceAtMost(1000)
        reload()
    }

    fun reload(){
        val c=selected?:return
        thread.manualSyncRequested=true
        if(thread.manualSyncJob?.isActive==true)return
        thread.manualSyncJob=scope.launch{
            val cache=runCatching{SeraMessageHistoryCache(context.applicationContext)}.getOrNull()
            while(thread.manualSyncRequested&&selected?.id==c.id&&connectionAvailable){
                thread.manualSyncRequested=false
                syncMessages(c,cache)
            }
        }
    }

    /** One serialized reconciliation path for realtime, manual mutations and reconnect. */
    private suspend fun syncMessages(conversation:NativeConversation,cache:SeraMessageHistoryCache?,hint:SeraMessageSyncHint=SeraMessageSyncHint.Full){
        thread.syncMutex.withLock {
            if(selected?.id!=conversation.id||!connectionAvailable)return@withLock
            val existing=messages
            val limit=historyLimit
            // Full snapshots on reconnect/timer cover missed events, deletes and private receipts.
            val ids=(hint as? SeraMessageSyncHint.Rows)?.ids?.takeIf{thread.initialMessagesLoaded&&!thread.cachedHistory&&it.isNotEmpty()&&it.size<=64}
            val result=withContext(Dispatchers.IO){
                if(ids!=null){
                    val delta=loadNativeMessages(token,conversation.id,ids.size,ids.toList())
                    val rows=delta.first
                    if(rows!=null&&rows.map{it.id}.toSet()==ids)seraMergeMessageDelta(existing,rows,limit) to ""
                    else loadNativeMessages(token,conversation.id,limit,cache=cache,owner=userId)
                }else loadNativeMessages(token,conversation.id,limit,cache=cache,owner=userId)
            }
            if(selected?.id!=conversation.id)return@withLock
            result.first?.let{latest->
                if(!thread.initialMessagesLoaded||thread.cachedHistory){
                    thread.unreadBoundary=latest.firstOrNull{isUnreadIncoming(it,userId)}?.id
                    thread.initialMessagesLoaded=true
                }
                if(!thread.cachedHistory&&messages.isNotEmpty()&&latest.lastOrNull()?.id!=messages.lastOrNull()?.id&&latest.lastOrNull()?.senderId!=userId){
                    latest.lastOrNull()?.let{SeraMessageAlerts.playIncomingOnce(context,userId,conversation.id,it.id)}
                    SeraUnreadRefresh.changed()
                }
                thread.cachedHistory=false
                if(messages!=latest)messages=latest
                val serverIds=latest.mapTo(hashSetOf()){it.id}
                thread.textAcknowledged=thread.textAcknowledged.filterNot{it.id in serverIds}
                val undelivered=latest.filter{isUnreadIncoming(it,userId)&&(it.deliveredAt.isBlank()||it.deliveredAt=="null")}
                val pendingDelivery=undelivered.map{it.id}.filter{thread.deliveryInFlight.add(it)}
                if(pendingDelivery.isNotEmpty())scope.launch{
                    try{withContext(Dispatchers.IO){pendingDelivery.chunked(100).forEach{batch->chatRpc(token,"sera_message_receipt",JSONObject().put("p_conversation",conversation.id).put("p_ids",JSONArray(batch)).put("p_read",false))}}}
                    finally{thread.deliveryInFlight.removeAll(pendingDelivery.toSet())}
                }
            }
            if(selected?.id==conversation.id)error=result.second
        }
    }
    fun react(m:NativeMessage,emoji:String){reaction=null;if(!thread.reactionBusy.add(m.id))return;scope.launch{try{val r=withContext(Dispatchers.IO){chatRpc(token,"sera_set_message_reaction",JSONObject().put("p_message",m.id).put("p_emoji",if(m.reactions.any{it.first==userId&&it.second==emoji})JSONObject.NULL else emoji))};if(r.first==null)error=r.second else reload()}finally{thread.reactionBusy.remove(m.id)}}}

    @Composable
    fun PresenceAndScroll() {
        val link=LocalSeraLink.current
        val activeCall=LocalSeraGlobalCall.current?.pinned==true
        LaunchedEffect(token,selected?.id,lifecycle,link,activeCall,thread.initialMessagesLoaded) { peerActivity=null; val peer=selected?.peerId?:return@LaunchedEffect; if(!link.available||!thread.initialMessagesLoaded)return@LaunchedEffect; lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED){while(true){peerActivity=withContext(Dispatchers.IO){peerPresence(token,peer)};delay(seraSecondarySyncDelay(link,activeCall,30000L))}} }
        val ime = WindowInsets.ime
        val density = LocalDensity.current
        val tail = thread.textQueue.lastOrNull()?.id ?: thread.textAcknowledged.lastOrNull()?.id ?: messages.lastOrNull()?.id
        LaunchedEffect(selected?.id, tail,thread.initialMessagesLoaded,thread.detailsOpen) {
            val id = selected?.id ?: return@LaunchedEffect
            val last = tail ?: return@LaunchedEffect
            if(!thread.initialMessagesLoaded||thread.detailsOpen||thread.jumpMessageId!=null)return@LaunchedEffect
            val changedConversation = !thread.initialScrollComplete
            val layout=messageListState.layoutInfo
            val nearBottom=layout.visibleItemsInfo.lastOrNull()?.index?.let{it>=layout.totalItemsCount-3}?:true
            if(changedConversation||last!=lastMessageId&&(nearBottom||thread.textQueue.isNotEmpty())){
                withFrameNanos{}
                val count=messageListState.layoutInfo.totalItemsCount
                if(count>0){val target=if(changedConversation&&(ime.getBottom(density)==0||showSearch))unreadTimelineIndex(messages,callEvents,thread.unreadBoundary,historyLimit) else null;messageListState.scrollToItem((target?:count-1).coerceIn(0,count-1));thread.initialScrollComplete=true}
            }
            lastVisibleConversation = id
            lastMessageId = last
        }
    }

    fun toggleSelected(id:String){
        if(messages.none{it.id==id&&!it.unsent})return
        if(id in thread.selectedMessages)thread.selectedMessages=thread.selectedMessages-id
        else if(thread.selectedMessages.size<50)thread.selectedMessages=thread.selectedMessages+id
        else error="Select up to 50 messages at a time."
        menu=null
    }
    fun openQuotedMessage(id:String){
        val c=selected?:return
        if(!SeraMessageAlerts.validId(id))return
        messageSearch="";showSearch=false;thread.suppressImeAnchor=true
        if(messages.any{it.id==id})thread.jumpMessageId=id else global.historicalTarget=c.id to id
    }
    @Composable
    fun JumpToMessage() {
        LaunchedEffect(thread.highlightedId){if(thread.highlightedId!=null){delay(1800);thread.highlightedId=null}}
        LaunchedEffect(selected?.id,thread.jumpMessageId,thread.detailsOpen){
            val id=thread.jumpMessageId?:return@LaunchedEffect
            if(thread.detailsOpen)return@LaunchedEffect
            val timeline=(mergeAcknowledgedText(messages,thread.textAcknowledged).map{SeraTimelineItem(it.createdAt,it,null)}+callEvents.map{SeraTimelineItem(it.createdAt,null,it)}).sortedBy{runCatching{java.time.Instant.parse(it.at).toEpochMilli()}.getOrDefault(0)}
            val index=timeline.indexOfFirst{it.message?.id==id}
            if(index>=0){
                withFrameNanos{}
                val offset=if(mergeAcknowledgedText(messages,thread.textAcknowledged).size>=historyLimit&&historyLimit<1000)1 else 0
                messageListState.scrollToItem(index+offset)
                thread.highlightedId=id
                thread.initialScrollComplete=true
            }else error="This message is no longer available."
            thread.jumpMessageId=null
        }
    }

    // IME resize changes the LazyColumn viewport without changing the message
    // tail. Re-anchor after layout, including the final keyboard animation frame.
    // Observe geometry, not scroll position: browsing history does not retrigger it.
    @Composable
    fun KeyboardScroll() {
        val ime = WindowInsets.ime
        val density = LocalDensity.current
        LaunchedEffect(selected?.id, showSearch, thread.detailsOpen, density, ime) {
            if(selected==null||showSearch||thread.detailsOpen)return@LaunchedEffect
            var previousIme = 0
            var previousHeight = 0
            var previousCount = 0
            snapshotFlow {
                val layout = messageListState.layoutInfo
                Triple(ime.getBottom(density), layout.viewportSize.height, layout.totalItemsCount)
            }.collectLatest { (bottom, height, count) ->
                if(bottom==0)thread.suppressImeAnchor=false
                val shouldAnchor = bottom>0 && count>0 && !thread.suppressImeAnchor &&
                    (bottom!=previousIme || height!=previousHeight || previousCount==0)
                previousIme=bottom
                previousHeight=height
                previousCount=count
                if(shouldAnchor){
                    // Wait for the resized list to be measured before scrolling.
                    withFrameNanos{}
                    val latestCount=messageListState.layoutInfo.totalItemsCount
                    if(latestCount>0 && ime.getBottom(density)>0 && !showSearch && thread.jumpMessageId==null && !thread.suppressImeAnchor && messageListState.canScrollForward){
                        messageListState.scrollToItem(latestCount-1)
                        // A very tall last bubble may still extend below the viewport.
                        val layout=messageListState.layoutInfo
                        val last=layout.visibleItemsInfo.lastOrNull()
                        if(last?.index==latestCount-1){
                            val clipped=(last.offset+last.size+layout.afterContentPadding-layout.viewportEndOffset).coerceAtLeast(0)
                            if(clipped>0)messageListState.scrollBy(clipped.toFloat())
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ConnectionAndPlayback() {
        LaunchedEffect(selected?.id,userId){val c=selected?:return@LaunchedEffect;queued=withContext(Dispatchers.IO){runCatching{outbox?.load(userId,c.id)}.getOrNull()};if(queued!=null){draft="";attachment=null;album=emptyList();reply=null}}
        val link=LocalSeraLink.current
        SideEffect{connectionAvailable=link.available}
        LaunchedEffect(playback,playback.playing){while(playback.playing){playback.tick();delay(250)}}
        DisposableEffect(playback,lifecycle){val observer=androidx.lifecycle.LifecycleEventObserver{_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_PAUSE)playback.stop()};lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer);playback.stop()}}
        LaunchedEffect(attachment){if(playback.activeId?.startsWith("preview:")==true&&playback.activeId!="preview:"+System.identityHashCode(attachment))playback.stop()}
    }

    @Composable
    fun TransferEffects() {
        LaunchedEffect(token,selected?.id,queued?.clientId,retrySignal,connectionAvailable){
            val c=selected?:return@LaunchedEffect;val p=queued?:return@LaunchedEffect
            if(!connectionAvailable||p.paused)return@LaunchedEffect
            val transfer=NativeChatTransfer();activeTransfer=transfer
            sending=true;transferProgress=if(p.file!=null||p.album.isNotEmpty())0 else -1
            try{
                val progress:(Int)->Unit={percent->scope.launch{if(selected?.id==c.id)transferProgress=percent}}
                val result=withContext(Dispatchers.IO){if(p.album.isNotEmpty())sendNativePhotoAlbum(token,userId,c.id,p,progress,transfer)else sendNativeMessageV2(token,userId,c.id,p.clientId,p.body,p.file,p.replyId,onProgress=progress,transfer=transfer)}
                if(result.first!=null){withContext(Dispatchers.IO){runCatching{outbox?.remove(userId,c.id,p.clientId)}};if(selected?.id==c.id){queued=null;sendFailed=false;error="";chime();reload()};runCatching{savedDrafts?.clear(userId,c.id)}}
                else{sendFailed=true;error=result.second}
            }finally{if(activeTransfer===transfer){activeTransfer=null;sending=false;transferProgress=-1}}
        }
    }

    private fun persistText(conversationId:String, entry:NativeQueuedText){
        scope.launch {
            val result=withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO){
                runCatching{check(outbox!=null);outbox.saveText(userId,conversationId,entry)}
            }
            thread.textQueue=thread.textQueue.map{if(it.id==entry.id)it.copy(stage=if(result.isSuccess)TextSendStage.Ready else TextSendStage.StorageFailed) else it}
            if(result.isSuccess && selected?.id==conversationId && draft.isEmpty())runCatching{savedDrafts?.clear(userId,conversationId)}
            if(result.isFailure && selected?.id==conversationId && draft.isEmpty())runCatching{savedDrafts?.write(userId,conversationId,entry.body)}
        }
    }
    private fun enqueueText(conversationId:String,body:String){
        val entry=NativeQueuedText(UUID.randomUUID().toString(),body,reply?.id,java.time.Instant.now().toString(),TextSendStage.Saving)
        if(thread.textQueue.size>=100){error="Too many pending messages. Retry or reconnect.";return}
        thread.textQueue=thread.textQueue+entry
        draft="";reply=null;error="";typingPulse++
        persistText(conversationId,entry)
    }
    private fun retryText(id:String){
        val c=selected?:return
        val p=thread.textQueue.firstOrNull{it.id==id}
        if(p==null){if(queued?.clientId==id)retrySignal++;return}
        if(p.stage==TextSendStage.StorageFailed){
            thread.textQueue=thread.textQueue.map{if(it.id==id)it.copy(stage=TextSendStage.Saving)else it}
            persistText(c.id,p)
        }else if(p.stage==TextSendStage.Failed){
            thread.textQueue=thread.textQueue.map{if(it.id==id)it.copy(stage=TextSendStage.Ready)else it}
        }
    }
    @Composable
    fun TextEffects(){
        LaunchedEffect(token,userId,selected?.id){
            val c=selected?:return@LaunchedEffect
            val loaded=withContext(Dispatchers.IO){runCatching{check(outbox!=null);outbox.loadTexts(userId,c.id)}}
            thread.textQueue=loaded.getOrDefault(emptyList())
            thread.textLoaded=true
            // If storage could not open, preserve the current draft and let the
            // next send surface a storage error rather than pretending it sent.
        }
        LaunchedEffect(connectionAvailable){if(connectionAvailable)thread.textQueue=thread.textQueue.map{if(it.stage==TextSendStage.Failed)it.copy(stage=TextSendStage.Ready)else it}}
        val next=thread.textQueue.firstOrNull{it.stage==TextSendStage.Saving||it.stage==TextSendStage.Ready}?.takeIf{it.stage==TextSendStage.Ready}
        LaunchedEffect(token,selected?.id,next?.id,connectionAvailable){
            val c=selected?:return@LaunchedEffect
            val p=next?:return@LaunchedEffect
            if(!connectionAvailable)return@LaunchedEffect
            val result=withContext(Dispatchers.IO){sendNativeMessageV2(token,userId,c.id,p.id,p.body,null,p.replyId)}
            val id=result.first
            if(id!=null){
                // Server ID is authoritative; retain a compact acknowledged echo
                // until realtime/polling provides the actual server row.
                thread.textAcknowledged=(thread.textAcknowledged+p.acknowledged(id,userId)).takeLast(100)
                withContext(kotlinx.coroutines.NonCancellable+Dispatchers.IO){runCatching{outbox?.removeText(userId,c.id,p.id)}}
                thread.textQueue=thread.textQueue.filterNot{it.id==p.id}
                if(selected?.id==c.id){chime();reload()}
            }else thread.textQueue=thread.textQueue.map{if(it.id==p.id)it.copy(stage=TextSendStage.Failed)else it}
        }
    }

    @Composable
    fun Dialogs() {
        SeraNativeMessageDialogs(remove=remove,reaction=reaction,forwarding=forwarding,conversations=conversations,token=token,userId=userId,forwardClient=forwardClient,
            onRemoveDismiss={remove=null},onReactionDismiss={reaction=null},onForwardDismiss={forwarding=null},
            onReact={message,emoji->react(message,emoji)},onChanged={refresh++;reload()},onError={error=it})
        SeraNativeAttachmentPicker(token=token,userId=userId,conversationId=selected?.id,open=attachmentStudio,onDismiss={attachmentStudio=false},
            onChecking={checkingFile=it},onSelected={attachment=it;album=emptyList();error=""},onAlbum={album=it;attachment=null;error=""},onShare={url->if(parseSeraMeeting(url)!=null&&draft.isNotBlank()){error="Finish your existing draft before adding a meeting."}else{val next=draft.trimEnd()+(if(draft.isBlank())"" else "\n")+url;if(next.length<=4000){draft=next;selected?.id?.let{runCatching{savedDrafts?.write(userId,it,next)}}}else error="Message is too long to add this link."}},onError={error=it})
        LaunchedEffect(messages,mediaViewer){val file=mediaViewer;if(file!=null&&messages.none{m->!m.unsent&&m.files.any{it.path==file.path}})mediaViewer=null}
        mediaViewer?.let{file->SeraPrivateImageViewer(token,file,onDismiss={mediaViewer=null})}
        // The system keyboard owns Back while visible; do not close the thread.
        val imeVisible=WindowInsets.ime.getBottom(LocalDensity.current)>0
        BackHandler(selected != null && !imeVisible) { if(thread.selectedMessages.isNotEmpty())thread.selectedMessages=emptySet() else if(thread.detailsOpen)thread.detailsOpen=false else if(onThreadExit!=null)onThreadExit() else {selected = null; messages = emptyList(); error = ""} }
    }

    @Composable
    fun ConversationPolling() {
        val link=LocalSeraLink.current
        val activeCall=LocalSeraGlobalCall.current?.pinned==true
        val unreadRevision=SeraUnreadRefresh.revision
        val cache=remember(context){runCatching{SeraMessageHistoryCache(context.applicationContext)}.getOrNull()}
        LaunchedEffect(token,userId,refresh,selected?.id,lifecycle,unreadRevision,connectionAvailable,link,activeCall) {
            if(userId.isBlank())return@LaunchedEffect
            if(conversations.isEmpty()){val saved=withContext(Dispatchers.IO){runCatching{cache?.readInbox(userId).orEmpty()}.getOrDefault(emptyList())};if(saved.isNotEmpty()){conversations=saved;loading=false}}
            if(!connectionAvailable){loading=false;return@LaunchedEffect}
            if(selected!=null&&selected?.peerId?.isNotBlank()==true&&selected?.peerName !in listOf("SERA member","Sera member","SERA professional",""))return@LaunchedEffect
            lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED){
                while(true){
                    val prior=conversations
                    fun publish(rows:List<NativeConversation>){conversations=rows;loading=false;val current=selected;if(current!=null&&(current.peerId.isBlank()||current.peerName in listOf("SERA member","Sera member","SERA professional","")))rows.firstOrNull{it.id==current.id}?.let{selected=it}}
                    val result=withContext(Dispatchers.IO){loadNativeConversations(token,userId,prior){rows->withContext(Dispatchers.Main){publish(rows)}}}
                    result.first?.let{rows->publish(rows);withContext(Dispatchers.IO){runCatching{cache?.writeInbox(userId,rows)}}}
                    if(selected==null)error=result.second;loading=false
                    if(selected!=null)break
                    delay(seraSecondarySyncDelay(link,activeCall,30000L))
                }
            }
        }
    }

    @Composable
    fun MessagePolling() {
        val link=LocalSeraLink.current
        val recoveryRevision=SeraPushRecovery.revision
        val cache=remember(context){runCatching{SeraMessageHistoryCache(context.applicationContext)}.getOrNull()}
        LaunchedEffect(token, selected?.id, lifecycle,recoveryRevision,connectionAvailable,link.networkId) {
            val conversation = selected ?: return@LaunchedEffect
            if(!thread.initialMessagesLoaded&&messages.isEmpty()){
                val cached=withContext(Dispatchers.IO){runCatching{cache?.read(userId,conversation.id)?.let{parseNativeMessageRows(it).first}}.getOrNull()}
                if(!cached.isNullOrEmpty()){messages=cached;thread.cachedHistory=true;thread.initialMessagesLoaded=true}
            }
            if(!connectionAvailable)return@LaunchedEffect
            lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                SeraRealtime(token,conversation.id).run { hint->syncMessages(conversation,cache,hint) }
            }
        }
    }

    @Composable
    fun ReadReceipts() {
        LaunchedEffect(token,selected?.id,lifecycle,thread.detailsOpen,thread.libraryMode,global.historicalTarget,thread.cachedHistory,connectionAvailable){
            val c=selected?:return@LaunchedEffect
            if(thread.detailsOpen||thread.libraryMode!=null||global.historicalTarget!=null||thread.cachedHistory||!connectionAvailable)return@LaunchedEffect
            lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED){
                val seen=mutableSetOf<String>()
                snapshotFlow{val layout=messageListState.layoutInfo;layout.visibleItemsInfo.filter{isReceiptVisible(it.offset,it.size,layout.viewportStartOffset,layout.viewportEndOffset)}.map{it.key.toString()}.toSet() to messages}
                    .collectLatest{(visible,latest)->
                        delay(600)
                        val ids=latest.filter{(it.id in visible||messageTimelineKey(it) in visible)&&isUnreadIncoming(it,userId)&&it.id !in seen}.map{it.id}.take(100)
                        if(ids.isNotEmpty()){val r=withContext(Dispatchers.IO){chatRpc(token,"sera_message_receipt",JSONObject().put("p_conversation",c.id).put("p_ids",JSONArray(ids)).put("p_read",true))};if(r.first!=null){seen.addAll(ids);SeraUnreadRefresh.changed()}}
                    }
            }
        }
    }

    @Composable
    fun TypingEffects() {
        val link=LocalSeraLink.current
        val activeCall=LocalSeraGlobalCall.current?.pinned==true
        LaunchedEffect(token, selected?.id, lifecycle,link,activeCall,thread.initialMessagesLoaded) {
            if(!link.available||!thread.initialMessagesLoaded){peerTyping=false;return@LaunchedEffect}
            val conversation = selected ?: return@LaunchedEffect
            lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                while (true) {
                    val result = withContext(Dispatchers.IO) { inboxRequest(token, "professional_typing_status?select=member_id&conversation_id=eq.${conversation.id}&member_id=neq.$userId&limit=1") }
                    peerTyping = runCatching { JSONArray(result.first ?: "[]").length() > 0 }.getOrDefault(false)
                    delay(seraSecondarySyncDelay(link,activeCall,2500L))
                }
            }
        }
        LaunchedEffect(token,selected?.id,lifecycle,link.available) {
            if(!link.available)return@LaunchedEffect
            val conversation=selected?:return@LaunchedEffect
            lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED){
                var lastPulse=typingPulse
                var lastEdit=0L
                var announced=false
                var lastSent=0L
                while(true){
                    val now=android.os.SystemClock.elapsedRealtime()
                    if(lastPulse!=typingPulse){lastPulse=typingPulse;lastEdit=now}
                    val active=draft.isNotBlank()&&lastEdit>0&&now-lastEdit<4000
                    if(active!=announced||(active&&now-lastSent>=4000)){
                        val result=withContext(Dispatchers.IO){inboxRequest(token,"rpc/sera_set_typing","POST",JSONObject().put("p_conversation",conversation.id).put("p_typing",active))}
                        if(result.first!=null){announced=active;lastSent=now}
                    }
                    delay(750)
                }
            }
        }
    }

    @Composable
    fun Render(modifier: Modifier, onInboxExit: (() -> Unit)?, onCompany: () -> Unit) {
        if(global.globalStars)SeraMessageLibraryDialog(token,null,userId,"star",onDismiss={global.globalStars=false},onJump={entry->global.globalStars=false;global.historicalTarget=entry.conversationId to entry.id})
        global.historicalTarget?.let{(chat,id)->SeraHistoricalMessageDialog(token,userId,chat,id,playback,conversations.firstOrNull{it.id==chat}?.peerName?:"Contact",onDismiss={playback.stop();global.historicalTarget=null})}
        val clipboard = LocalClipboardManager.current
        val dark = androidx.compose.foundation.isSystemInDarkTheme()
        val palette=seraChatPalette(dark)
        val inboxInk=palette.ink
        val inboxSoft=palette.quote
        val chatSurface=palette.canvas
        val outgoing=palette.outgoing
        val incoming=palette.incoming
        val muted=palette.muted
        val accent=palette.accent
        Column(modifier.fillMaxSize().background(chatSurface)) {
            if (selected == null) {
                Column(Modifier.fillMaxSize().padding(horizontal=12.dp)){
                Spacer(Modifier.height(4.dp))
                TextButton(onClick={global.globalStars=true},modifier=Modifier.align(Alignment.End)){Text("All starred messages")}
                SeraNativeConversationList(conversations,search,loading,error,inboxInk,inboxSoft,chatSurface,muted,accent,inboxTeal,onInboxExit,onCompany=onCompany,onSearch={search=it},onRetry={refresh++},token=token,userId=userId,onControlChanged={refresh++},onSelect={conversation->
                    draft=savedDrafts?.read(userId,conversation.id).orEmpty();selected=conversation;messages=emptyList();error="";typingPulse=0;peerTyping=false
                })
                }
            } else {
                val conversation = selected!!
                if(thread.detailsOpen){
                    val current=conversations.firstOrNull{it.id==conversation.id}?:conversation
                    SeraConversationDetails(current,token,userId,peerActivity,messages,historyLimit,!thread.initialMessagesLoaded,playback,inboxInk,inboxSoft,muted,accent,
                        onBack={thread.detailsOpen=false},onLoadEarlier={loadEarlier()},onChanged={refresh++},onImage={mediaViewer=it},onSearch={thread.detailsOpen=false;thread.libraryMode="search"},onJumpToMessage={id->thread.suppressImeAnchor=true;thread.jumpMessageId=id;messageSearch="";showSearch=false;thread.detailsOpen=false})
                }else{
                SeraThreadHeader(conversation,conversations.firstOrNull{it.id==conversation.id}?.photo,token,userId,peerTyping,peerActivity,sound,inboxInk,inboxSoft,muted,accent,inboxTeal,
                    onBack={if(onThreadExit!=null)onThreadExit() else {selected=null;messages=emptyList();error=""}},onSearch={thread.libraryMode="search"},
                    onSound={sound=!sound;context.getSharedPreferences("sera_chat_settings",0).edit().putBoolean("sound",sound).apply()},
                    onAudioStart={playback.pause()},onHistory={callEvents=it},onDetails={thread.detailsOpen=true},onSafety={thread.safetyDialog=true})
                if(thread.safetyDialog)SeraConversationSafetyDialog(conversation.peerName,onDismiss={thread.safetyDialog=false},onBlock={scope.launch{val result=withContext(Dispatchers.IO){inboxRequest(token,"professional_blocks","POST",JSONObject().put("blocker_id",userId).put("blocked_id",conversation.peerId))};if(result.first!=null){withContext(Dispatchers.IO){runCatching{SeraMessageHistoryCache(context.applicationContext).clear(userId)}};thread.safetyDialog=false;messages=emptyList();conversations=conversations.filterNot{it.peerId==conversation.peerId};selected=null;refresh++;onThreadExit?.invoke()}else error=result.second}},onReport={reason,details->scope.launch{val result=withContext(Dispatchers.IO){inboxRequest(token,"professional_reports","POST",JSONObject().put("reporter_id",userId).put("reported_id",conversation.peerId).put("reason",reason).put("details",details))};if(result.first!=null){thread.safetyDialog=false;android.widget.Toast.makeText(context,"Report submitted",android.widget.Toast.LENGTH_SHORT).show()}else error=result.second}})
                HorizontalDivider(modifier=Modifier.testTag("sera_thread_header_divider"),color=palette.border,thickness=.7.dp)
                Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal=12.dp)){
                val chosen=messages.filter{it.id in thread.selectedMessages&&!it.unsent}
                if(thread.selectedMessages.isNotEmpty()){
                    SeraMultiSelectToolbar(chosen.size,thread.bulkBusy,chosen.all{it.files.isEmpty()},onClose={thread.selectedMessages=emptySet()},onCopy={clipboard.setText(AnnotatedString(chosen.joinToString("\n\n"){it.body}));thread.selectedMessages=emptySet()},onStar={
                        thread.bulkBusy=true
                        scope.launch{try{val r=withContext(Dispatchers.IO){chatRpc(token,"sera_bulk_star_messages",JSONObject().put("p_conversation",conversation.id).put("p_ids",JSONArray(chosen.map{it.id})))};if(r.first!=null){thread.selectedMessages=emptySet();thread.libraryRevision++}else error=r.second}finally{thread.bulkBusy=false}}
                    },onForward={thread.bulkForward=true})
                }else SeraMessageLibraryBar(token,conversation.id,thread.libraryRevision,accent,onOpen={thread.libraryMode=it},onScheduled={thread.scheduledOpen=true},loadPins=thread.initialMessagesLoaded)
                if(thread.bulkForward)SeraBulkForwardDialog(token,conversation.id,chosen,conversations,onDismiss={thread.bulkForward=false},onSuccess={thread.bulkForward=false;thread.selectedMessages=emptySet();refresh++;reload()})
                if(thread.scheduledOpen)SeraScheduledMessagesScreen(token,conversation.id,conversation.peerName,thread.scheduleDraft,onDismiss={thread.scheduledOpen=false;thread.scheduleDraft=null},onCreated={body->if(thread.scheduleDraft!=null&&draft==body){draft="";runCatching{savedDrafts?.clear(userId,conversation.id)}};thread.scheduleDraft=null})
                thread.libraryMode?.let{mode->SeraMessageLibraryDialog(token,conversation.id,userId,mode,onDismiss={thread.libraryMode=null},onJump={entry->thread.libraryMode=null;if(messages.any{it.id==entry.id}){thread.suppressImeAnchor=true;thread.jumpMessageId=entry.id;messageSearch="";showSearch=false}else global.historicalTarget=entry.conversationId to entry.id})}
                HorizontalDivider(color=inboxSoft,thickness=1.dp)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                if(!thread.initialMessagesLoaded&&error.isBlank())LinearProgressIndicator(Modifier.fillMaxWidth())
                if(thread.cachedHistory)Text("Saved messages · syncing…",fontSize=11.sp,color=muted,modifier=Modifier.padding(horizontal=12.dp,vertical=4.dp))
                val shown = mergeAcknowledgedText(messages,thread.textAcknowledged)
                val legacy = queued?.takeIf{it.file==null&&it.album.isEmpty()}?.let{NativeQueuedText(it.clientId,it.body,it.replyId,java.time.Instant.now().toString(),if(sendFailed)TextSendStage.Failed else TextSendStage.Ready)}
                val pending = (thread.textQueue + listOfNotNull(legacy)).filterNot{p->shown.any{it.senderId==userId&&it.clientId==p.id}}
                SeraThreadTimeline(Modifier.weight(1f),messageListState,shown,callEvents,messageSearch,historyLimit,menu,conversation.peerName,token,userId,playback,inboxInk,inboxSoft,chatSurface,muted,accent,outgoing,incoming,
                    onLoadEarlier={loadEarlier()},onMenu={menu=it},onReply={reply=it},onReact={reaction=it},onForward={forwarding=it;forwardClient=UUID.randomUUID().toString()},onCopy={clipboard.setText(AnnotatedString(it.body))},onUnsend={remove=it},onReaction={m,e->react(m,e)},onImage={mediaViewer=it},onError={error=it},pendingTexts=pending,connectionAvailable=connectionAvailable,onRetryText={retryText(it)},unreadBoundary=thread.unreadBoundary,conversationId=conversation.id,onLibraryChanged={thread.libraryRevision++;reload()},onQuote={openQuotedMessage(it)},highlightedId=thread.highlightedId,selectedIds=thread.selectedMessages,onSelectMessage={toggleSelected(it)},peerPhoto=conversation.photo?:conversations.firstOrNull{it.id==conversation.id}?.photo,onMeetingResponse={m,text->if(draft.isBlank()){reply=m;draft=text;runCatching{savedDrafts?.write(userId,conversation.id,text)}}else error="Finish your existing draft before responding."})
                if(thread.selectedMessages.isEmpty())key(conversation.id) { RenderComposer(conversation, inboxInk, inboxSoft, muted, accent) }
                }
                }
            }
        }
    }

    @Composable
    fun RenderComposer(conversation: NativeConversation, inboxInk: Color, inboxSoft: Color, muted: Color, accent: Color) {
        val haptics = LocalHapticFeedback.current
        SeraNativeComposer(draft=draft,attachment=attachment,album=album,reply=reply,queued=queued,sending=sending,checkingFile=checkingFile||!thread.textLoaded,connectionAvailable=connectionAvailable,sendFailed=sendFailed,transferProgress=transferProgress,recordingVoice=recordingVoice,ink=inboxInk,soft=inboxSoft,muted=muted,accent=accent,teal=seraChatColors().button,playback=playback,
            onDraftChange={draft=it;typingPulse++;runCatching{savedDrafts?.write(userId,conversation.id,it)}},
            onAttach={attachmentStudio=true;haptics.performHapticFeedback(HapticFeedbackType.LongPress)},
            onRemoveAttachment={attachment=null},onRemovePhoto={index->val next=album.filterIndexed{i,_->i!=index};if(next.size==1){attachment=next.first();album=emptyList()}else album=next},onCancelReply={reply=null},onCancelUpload={val q=queued;val transfer=activeTransfer;if(q!=null&&transfer?.requestCancel()==true){queued=q.copy(paused=true);scope.launch(Dispatchers.IO){runCatching{transfer.disconnect();check(outbox!=null);outbox.setPaused(userId,conversation.id,q.clientId,true)}.onFailure{withContext(Dispatchers.Main){error="Could not persist upload pause. Keep this conversation open."}}}}},onRetry={val q=queued;if(q!=null&&q.paused){scope.launch{val saved=withContext(Dispatchers.IO){runCatching{check(outbox!=null);outbox.setPaused(userId,conversation.id,q.clientId,false)}};if(saved.isSuccess&&selected?.id==conversation.id){queued=q.copy(paused=false);retrySignal++}else error="Could not resume pending upload."}}else retrySignal++},
            onEditQueued={val q=queued;if(q!=null)scope.launch{withContext(Dispatchers.IO){runCatching{outbox?.remove(userId,conversation.id,q.clientId)}};queued=null;draft=q.body;attachment=q.file;album=q.album;sendFailed=false}},
            onVoice={attachment=it;album=emptyList();error=""},onRecording={recordingVoice=it;if(it)playback.pause()},onError={error=it},
            onSchedule={thread.scheduleDraft=draft;thread.scheduledOpen=true},
            onSend={
    
                val body = draft.trim()
                val file=attachment
                if ((body.isBlank() && file == null && album.isEmpty()) || sending || checkingFile || queued!=null || !thread.textLoaded) return@SeraNativeComposer
                if(file==null&&album.isEmpty()){enqueueText(conversation.id,body);return@SeraNativeComposer}
                sending = true
                scope.launch {
                    val p=NativePending(UUID.randomUUID().toString(),body,reply?.id,file,album)
                    val saved=withContext(Dispatchers.IO){runCatching{check(outbox!=null);outbox.save(userId,conversation.id,p)}}
                    if(saved.isSuccess){
                        runCatching{savedDrafts?.clear(userId,conversation.id)}
                        if(selected?.id==conversation.id){draft="";attachment=null;album=emptyList();reply=null;queued=p;sendFailed=false;error=""}
                    }else error="Secure message storage is unavailable. Your draft was retained."
                    sending = false
                }
            })
    }
}
