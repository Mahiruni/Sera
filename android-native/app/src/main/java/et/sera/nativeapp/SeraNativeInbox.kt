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
internal data class NativeMessage(val id: String, val senderId: String, val body: String, val readAt: String, val createdAt: String, val deliveredAt: String, val files: List<NativeChatFile>, val replyId:String="", val forwarded:Boolean=false, val unsent:Boolean=false, val clientId:String="", val reactions:List<Pair<String,String>> = emptyList(),val privateReadAt:String="")

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

private fun loadNativeConversations(token: String, userId: String): Pair<List<NativeConversation>?, String> {
    val (raw, error) = inboxRequest(token,
        "rpc/sera_inbox_v2","POST",JSONObject().put("p_limit",100))
    if (raw == null) return null to error
    return try {
        val rows = JSONArray(raw)
        val peers = (0 until rows.length()).map { rows.getJSONObject(it) }
        val ids = peers.map { if (it.optString("member_a") == userId) it.optString("member_b") else it.optString("member_a") }
        val names = mutableMapOf<String, String>()
        val paths = mutableMapOf<String, String>()
        val photos = mutableMapOf<String, String>()
        if (ids.isNotEmpty()) {
            val (cards, _) = inboxRequest(token, "rpc/member_cards", "POST", JSONObject().put("ids", JSONArray(ids)))
            if (cards != null) {
                val entries = JSONArray(cards)
                for (i in 0 until entries.length()) {
                    val entry = entries.getJSONObject(i)
                    names[entry.optString("id")] = entry.optString("full_name")
                    entry.optString("avatar_path").takeIf{it.isNotBlank()&&it!="null"&&it.matches(Regex("[a-zA-Z0-9/_.-]+"))&&!it.contains("..")}?.let{paths[entry.optString("id")]=it}
                }
            }
        }
        if(paths.isNotEmpty()) runCatching {
            val base=BuildConfig.SUPABASE_URL.trimEnd('/')
            val connection=(URL(base+"/storage/v1/object/sign/sera-account-private").openConnection() as HttpURLConnection).apply{requestMethod="POST";doOutput=true;connectTimeout=10000;readTimeout=10000;setRequestProperty("apikey",BuildConfig.SUPABASE_ANON_KEY);setRequestProperty("Authorization","Bearer $token");setRequestProperty("Content-Type","application/json")}
            try{connection.outputStream.use{it.write(JSONObject().put("paths",JSONArray(paths.values.toList())).put("expiresIn",300).toString().toByteArray())};if(connection.responseCode in 200..299){val signed=JSONArray(connection.inputStream.bufferedReader().use{it.readText()});for(i in 0 until signed.length()){val item=signed.getJSONObject(i);val path=item.optString("path");val url=item.optString("signedURL").ifBlank{item.optString("signedUrl")};if(url.startsWith("/object/sign/")){paths.entries.filter{it.value==path}.forEach{photos[it.key]=base+"/storage/v1"+url}}}}}finally{connection.disconnect()}
        }
        val controls=runCatching{val raw=inboxRequest(token,"sera_conversation_preferences?select=conversation_id,pinned,archived,marked_unread,muted_until&user_id=eq.$userId").first;val rows=JSONArray(raw?:"[]");(0 until rows.length()).map{rows.getJSONObject(it)}.associateBy{it.getString("conversation_id")}}.getOrDefault(emptyMap())
        peers.map { row ->
            val id = if (row.optString("member_a") == userId) row.optString("member_b") else row.optString("member_a")
            val control=controls[row.optString("id")]
            NativeConversation(row.optString("id"), id, names[id]?.takeIf { it.isNotBlank() && it != "null" } ?: "SERA professional",row.optString("last_body").takeUnless{it=="null"}.orEmpty(),row.optInt("unread_count"),photos[id],row.optString("last_created_at").takeUnless{it=="null"}.orEmpty(),control?.optBoolean("pinned")?:false,control?.optBoolean("archived")?:false,control?.optBoolean("marked_unread")?:false,control?.optString("muted_until")?.takeUnless{it=="null"||it.isBlank()})
        } to ""
    } catch (_: Exception) { null to "Unable to display conversations." }
}

private fun loadNativeMessages(token: String, conversationId: String, limit:Int=100): Pair<List<NativeMessage>?, String> {
    val (raw, error) = inboxRequest(token,
        "professional_messages?select=id,sender_id,body,read_at,created_at,delivered_at,reply_to_id,is_forwarded,unsent_at,client_message_id,sera_private_message_reads(read_at),professional_message_reactions(member_id,emoji),professional_message_attachments(file_name,mime_type,storage_path,byte_size,duration_ms,position)&conversation_id=eq.$conversationId&order=created_at.desc,id.desc&limit=$limit")
    if (raw == null) return null to error
    return try {
        val rows = JSONArray(raw)
        (0 until rows.length()).map { i ->
            val item = rows.getJSONObject(i)
            val attachments = item.optJSONArray("professional_message_attachments") ?: JSONArray()
            val files = (0 until attachments.length()).map{attachments.getJSONObject(it)}.sortedBy{it.optInt("position")}.map { a -> NativeChatFile(a.optString("file_name"),a.optString("mime_type"),a.optString("storage_path"),a.optInt("byte_size"),a.optInt("duration_ms").takeIf{it>0}) }
            NativeMessage(item.optString("id"), item.optString("sender_id"), item.optString("body"), item.optString("read_at"), item.optString("created_at"), item.optString("delivered_at"), files,item.optString("reply_to_id").takeUnless{it=="null"}.orEmpty(),item.optBoolean("is_forwarded"),!item.isNull("unsent_at"),item.optString("client_message_id"),(item.optJSONArray("professional_message_reactions")?:JSONArray()).let{rs->(0 until rs.length()).map{rs.getJSONObject(it)}.filter{!it.isNull("emoji")}.map{it.optString("member_id") to it.optString("emoji")}},privateReadAt=item.optJSONObject("sera_private_message_reads")?.optString("read_at")?:item.optJSONArray("sera_private_message_reads")?.optJSONObject(0)?.optString("read_at").orEmpty())
        }.asReversed() to ""
    } catch (_: Exception) { null to "Unable to display messages." }
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
fun SeraNativeInbox(token: String, userId: String, modifier: Modifier = Modifier, openCompany: Boolean=false, onCompanyConsumed:()->Unit={}, initialThread:String?=null, initialPeer:String="", initialName:String="", onThreadExit:(()->Unit)?=null, onThreadVisibility:(Boolean)->Unit={}, onInboxExit:(()->Unit)?=null) {
    val dark=androidx.compose.foundation.isSystemInDarkTheme()
    val scheme=if(dark) darkColorScheme(primary=Color(0xFF6DE0D8),onPrimary=Color(0xFF10383B),surface=Color(0xFF14282D),onSurface=Color(0xFFE6F2F1),surfaceVariant=Color(0xFF233E43),onSurfaceVariant=Color(0xFFA8C3C5),background=Color(0xFF14282D),onBackground=Color(0xFFE6F2F1)) else MaterialTheme.colorScheme
    MaterialTheme(colorScheme=scheme,typography=MaterialTheme.typography,shapes=MaterialTheme.shapes){SeraNativeInboxContent(token,userId,modifier,openCompany,onCompanyConsumed,initialThread,initialPeer,initialName,onThreadExit,onThreadVisibility,onInboxExit)}
}
@Composable
private fun SeraNativeInboxContent(token:String,userId:String,modifier:Modifier,openCompany:Boolean,onCompanyConsumed:()->Unit,initialThread:String?,initialPeer:String,initialName:String,onThreadExit:(()->Unit)?,onThreadVisibility:(Boolean)->Unit,onInboxExit:(()->Unit)?){
    var companyInbox by remember { mutableStateOf(false) }
    LaunchedEffect(openCompany){if(openCompany){companyInbox=true;onCompanyConsumed()}}
    if(companyInbox){Box(modifier.fillMaxSize().navigationBarsPadding()){SeraCompanyInbox(token,userId,onBack={companyInbox=false})};return}
    val context = LocalContext.current
    // Fail safely if encrypted storage is temporarily unavailable; never
    // downgrade private message drafts to unencrypted preferences.
    val savedDrafts = remember(context) { runCatching { SeraInboxDrafts(context) }.getOrNull() }
    val global = remember { InboxGlobalState(context) }
    val selection = remember(initialThread) { InboxSelectionState(initialThread, initialPeer, initialName, savedDrafts, userId) }
    val thread = remember(selection.selected?.id) { InboxThreadState() }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val messageListState = rememberLazyListState()
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
    var sound by mutableStateOf(context.getSharedPreferences("sera_chat_settings",0).getBoolean("sound",false))
    var connectionAvailable by mutableStateOf(true)
}

private class InboxSelectionState(initialThread: String?, initialPeer: String, initialName: String, savedDrafts: SeraInboxDrafts?, userId: String) {
    var selected by mutableStateOf(initialThread?.let{NativeConversation(it,initialPeer,initialName)})
    var draft by mutableStateOf(initialThread?.let{savedDrafts?.read(userId,it)}.orEmpty())
}

private class InboxThreadState {
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
    var historyLimit by mutableIntStateOf(100)
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
    var initialMessagesLoaded by mutableStateOf(false)
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
    fun reload(){val c=selected?:return;scope.launch{val r=withContext(Dispatchers.IO){loadNativeMessages(token,c.id,historyLimit)};if(selected?.id==c.id){r.first?.let{latest->messages=latest;thread.textAcknowledged=thread.textAcknowledged.filterNot{ack->latest.any{it.id==ack.id}}};error=r.second}}}
    fun react(m:NativeMessage,emoji:String){reaction=null;scope.launch{val r=withContext(Dispatchers.IO){chatRpc(token,"sera_set_message_reaction",JSONObject().put("p_message",m.id).put("p_emoji",if(m.reactions.any{it.first==userId&&it.second==emoji})JSONObject.NULL else emoji))};if(r.first==null)error=r.second else reload()}}

    @Composable
    fun PresenceAndScroll() {
        LaunchedEffect(token,selected?.id,lifecycle) { val peer=selected?.peerId?:return@LaunchedEffect; lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED){while(true){peerActivity=withContext(Dispatchers.IO){peerPresence(token,peer)};delay(30000)}} }
        val tail = thread.textQueue.lastOrNull()?.id ?: thread.textAcknowledged.lastOrNull()?.id ?: messages.lastOrNull()?.id
        LaunchedEffect(selected?.id, tail,thread.initialMessagesLoaded) {
            val id = selected?.id ?: return@LaunchedEffect
            val last = tail ?: return@LaunchedEffect
            if(!thread.initialMessagesLoaded)return@LaunchedEffect
            val changedConversation = lastVisibleConversation != id
            val layout=messageListState.layoutInfo
            val nearBottom=layout.visibleItemsInfo.lastOrNull()?.index?.let{it>=layout.totalItemsCount-3}?:true
            if(changedConversation||last!=lastMessageId&&(nearBottom||thread.textQueue.isNotEmpty())){
                withFrameNanos{}
                val count=messageListState.layoutInfo.totalItemsCount
                if(count>0){val target=if(changedConversation)unreadTimelineIndex(messages,callEvents,thread.unreadBoundary,historyLimit) else null;messageListState.scrollToItem((target?:count-1).coerceIn(0,count-1))}
            }
            lastVisibleConversation = id
            lastMessageId = last
        }
    }

    @Composable
    fun ConnectionAndPlayback() {
        LaunchedEffect(selected?.id,userId){val c=selected?:return@LaunchedEffect;queued=withContext(Dispatchers.IO){runCatching{outbox?.load(userId,c.id)}.getOrNull()};if(queued!=null){draft="";attachment=null;album=emptyList();reply=null}}
        LaunchedEffect(lifecycle){lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED){while(true){val cm=context.getSystemService(ConnectivityManager::class.java);val caps=cm.getNetworkCapabilities(cm.activeNetwork);connectionAvailable=caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)==true;delay(2000)}}}
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
            onChecking={checkingFile=it},onSelected={attachment=it;album=emptyList();error=""},onAlbum={album=it;attachment=null;error=""},onShare={url->val next=draft.trimEnd()+(if(draft.isBlank())"" else "\n")+url;if(next.length<=4000){draft=next;selected?.id?.let{runCatching{savedDrafts?.write(userId,it,next)}}}else error="Message is too long to add this link."},onError={error=it})
        LaunchedEffect(messages,mediaViewer){val file=mediaViewer;if(file!=null&&messages.none{m->!m.unsent&&m.files.any{it.path==file.path}})mediaViewer=null}
        mediaViewer?.let{file->SeraPrivateImageViewer(token,file,onDismiss={mediaViewer=null})}
        BackHandler(selected != null) { if(onThreadExit!=null)onThreadExit() else {selected = null; messages = emptyList(); error = ""} }
    }

    @Composable
    fun ConversationPolling() {
        val unreadRevision=SeraUnreadRefresh.revision
        LaunchedEffect(token,userId,refresh,selected?.id,lifecycle,unreadRevision) {
            if(userId.isBlank())return@LaunchedEffect
            lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED){
                while(true){
                    val result=withContext(Dispatchers.IO){loadNativeConversations(token,userId)}
                    result.first?.let{rows->conversations=rows;val current=selected;if(current!=null&&current.peerName=="SERA member")rows.firstOrNull{it.id==current.id}?.let{selected=it}}
                    if(selected==null)error=result.second;loading=false
                    if(selected!=null)break
                    delay(20000)
                }
            }
        }
    }

    @Composable
    fun MessagePolling() {
        val recoveryRevision=SeraPushRecovery.revision
        LaunchedEffect(token, selected?.id, lifecycle, historyLimit,recoveryRevision) {
            val conversation = selected ?: return@LaunchedEffect
            lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                SeraRealtime(token, conversation.id).run {
                    val result = withContext(Dispatchers.IO) { loadNativeMessages(token, conversation.id, historyLimit) }
                    result.first?.let { latest ->
                        if(!thread.initialMessagesLoaded){thread.unreadBoundary=latest.firstOrNull{isUnreadIncoming(it,userId)}?.id;thread.initialMessagesLoaded=true}
                        if(messages.isNotEmpty() && latest.lastOrNull()?.id!=messages.lastOrNull()?.id && latest.lastOrNull()?.senderId!=userId){if(SeraMessageAlerts.enabled(context,userId)&&SeraMessageAlerts.mutedUntil(context,userId,conversation.id)<=System.currentTimeMillis())chime();SeraUnreadRefresh.changed()}
                        messages = latest
                    thread.textAcknowledged=thread.textAcknowledged.filterNot{ack->latest.any{it.id==ack.id}}
                        val unread = latest.filter { isUnreadIncoming(it,userId) }
                        val undelivered=unread.filter{it.deliveredAt.isBlank()||it.deliveredAt=="null"}
                        if(undelivered.isNotEmpty())withContext(Dispatchers.IO){undelivered.chunked(100).forEach{batch->chatRpc(token,"sera_message_receipt",JSONObject().put("p_conversation",conversation.id).put("p_ids",JSONArray(batch.map{it.id})).put("p_read",false))}}
                    }
                    error = result.second
                }
            }
        }
    }

    @Composable
    fun ReadReceipts() {
        LaunchedEffect(token,selected?.id,lifecycle){
            val c=selected?:return@LaunchedEffect
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
        LaunchedEffect(token, selected?.id, lifecycle) {
            val conversation = selected ?: return@LaunchedEffect
            lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                while (true) {
                    val result = withContext(Dispatchers.IO) { inboxRequest(token, "professional_typing_status?select=member_id&conversation_id=eq.${conversation.id}&member_id=neq.$userId&limit=1") }
                    peerTyping = runCatching { JSONArray(result.first ?: "[]").length() > 0 }.getOrDefault(false)
                    delay(2500)
                }
            }
        }
        LaunchedEffect(token,selected?.id,lifecycle) {
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
        val clipboard = LocalClipboardManager.current
        val dark = androidx.compose.foundation.isSystemInDarkTheme()
        val inboxInk = if(dark) Color(0xFFE6F2F1) else Color(0xFF183B40)
        val inboxSoft = if(dark) Color(0xFF233E43) else Color(0xFFE9F2F1)
        val chatSurface = if(dark) Color(0xFF111D21) else Color(0xFFF9FCFC)
        val outgoing = if(dark) Color(0xFF087478) else Color(0xFF087478)
        val incoming = if(dark) Color(0xFF26383D) else Color(0xFFE7F0EF)
        val muted = if(dark) Color(0xFFA8C3C5) else Color(0xFF62777B)
        val accent=if(dark) Color(0xFF6DE0D8) else inboxTeal
        Column(modifier.fillMaxSize().background(chatSurface).imePadding().navigationBarsPadding().padding(horizontal = 12.dp)) {
            Spacer(Modifier.height(4.dp))
            if (selected == null) {
                SeraNativeConversationList(conversations,search,loading,error,inboxInk,inboxSoft,chatSurface,muted,accent,inboxTeal,onInboxExit,onCompany=onCompany,onSearch={search=it},onRetry={refresh++},token=token,userId=userId,onControlChanged={refresh++},onSelect={conversation->
                    draft=savedDrafts?.read(userId,conversation.id).orEmpty();selected=conversation;messages=emptyList();error="";typingPulse=0;peerTyping=false
                })
            } else {
                val conversation = selected!!
                SeraThreadHeader(conversation,conversations.firstOrNull{it.id==conversation.id}?.photo,token,userId,peerTyping,peerActivity,sound,inboxInk,inboxSoft,muted,accent,inboxTeal,
                    onBack={if(onThreadExit!=null)onThreadExit() else {selected=null;messages=emptyList();error=""}},onSearch={showSearch=!showSearch},
                    onSound={sound=!sound;context.getSharedPreferences("sera_chat_settings",0).edit().putBoolean("sound",sound).apply()},
                    onAudioStart={playback.pause()},onHistory={callEvents=it})
                if(showSearch)OutlinedTextField(messageSearch,{messageSearch=it},modifier=Modifier.fillMaxWidth(),placeholder={Text("Search loaded messages")},singleLine=true)
                HorizontalDivider(color=inboxSoft,thickness=1.dp)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                val shown = mergeAcknowledgedText(messages,thread.textAcknowledged)
                val legacy = queued?.takeIf{it.file==null&&it.album.isEmpty()}?.let{NativeQueuedText(it.clientId,it.body,it.replyId,java.time.Instant.now().toString(),if(sendFailed)TextSendStage.Failed else TextSendStage.Ready)}
                val pending = (thread.textQueue + listOfNotNull(legacy)).filterNot{p->shown.any{it.senderId==userId&&it.clientId==p.id}}
                SeraThreadTimeline(Modifier.weight(1f),messageListState,shown,callEvents,messageSearch,historyLimit,menu,conversation.peerName,token,userId,playback,inboxInk,inboxSoft,chatSurface,muted,accent,outgoing,incoming,
                    onLoadEarlier={historyLimit=(historyLimit+100).coerceAtMost(1000)},onMenu={menu=it},onReply={reply=it},onReact={reaction=it},onForward={forwarding=it;forwardClient=UUID.randomUUID().toString()},onCopy={clipboard.setText(AnnotatedString(it.body))},onUnsend={remove=it},onReaction={m,e->react(m,e)},onImage={mediaViewer=it},onError={error=it},pendingTexts=pending,connectionAvailable=connectionAvailable,onRetryText={retryText(it)},unreadBoundary=thread.unreadBoundary)
                key(conversation.id) { RenderComposer(conversation, inboxInk, inboxSoft, muted, accent) }
            }
        }
    }

    @Composable
    fun RenderComposer(conversation: NativeConversation, inboxInk: Color, inboxSoft: Color, muted: Color, accent: Color) {
        val haptics = LocalHapticFeedback.current
        SeraNativeComposer(draft=draft,attachment=attachment,album=album,reply=reply,queued=queued,sending=sending,checkingFile=checkingFile||!thread.textLoaded,connectionAvailable=connectionAvailable,sendFailed=sendFailed,transferProgress=transferProgress,recordingVoice=recordingVoice,ink=inboxInk,soft=inboxSoft,muted=muted,accent=accent,teal=inboxTeal,playback=playback,
            onDraftChange={draft=it;typingPulse++;runCatching{savedDrafts?.write(userId,conversation.id,it)}},
            onAttach={attachmentStudio=true;haptics.performHapticFeedback(HapticFeedbackType.LongPress)},
            onRemoveAttachment={attachment=null},onRemovePhoto={index->val next=album.filterIndexed{i,_->i!=index};if(next.size==1){attachment=next.first();album=emptyList()}else album=next},onCancelReply={reply=null},onCancelUpload={val q=queued;val transfer=activeTransfer;if(q!=null&&transfer?.requestCancel()==true){queued=q.copy(paused=true);scope.launch(Dispatchers.IO){runCatching{transfer.disconnect();check(outbox!=null);outbox.setPaused(userId,conversation.id,q.clientId,true)}.onFailure{withContext(Dispatchers.Main){error="Could not persist upload pause. Keep this conversation open."}}}}},onRetry={val q=queued;if(q!=null&&q.paused){scope.launch{val saved=withContext(Dispatchers.IO){runCatching{check(outbox!=null);outbox.setPaused(userId,conversation.id,q.clientId,false)}};if(saved.isSuccess&&selected?.id==conversation.id){queued=q.copy(paused=false);retrySignal++}else error="Could not resume pending upload."}}else retrySignal++},
            onEditQueued={val q=queued;if(q!=null)scope.launch{withContext(Dispatchers.IO){runCatching{outbox?.remove(userId,conversation.id,q.clientId)}};queued=null;draft=q.body;attachment=q.file;album=q.album;sendFailed=false}},
            onVoice={attachment=it;album=emptyList();error=""},onRecording={recordingVoice=it;if(it)playback.pause()},onError={error=it},
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
