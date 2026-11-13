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

internal data class NativeConversation(val id: String, val peerId: String, val peerName: String, val preview:String="", val unread:Int=0, val photo:String?=null, val lastAt:String="")
internal data class NativeMessage(val id: String, val senderId: String, val body: String, val readAt: String, val createdAt: String, val deliveredAt: String, val files: List<NativeChatFile>, val replyId:String="", val forwarded:Boolean=false, val unsent:Boolean=false, val clientId:String="", val reactions:List<Pair<String,String>> = emptyList())

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
        peers.map { row ->
            val id = if (row.optString("member_a") == userId) row.optString("member_b") else row.optString("member_a")
            NativeConversation(row.optString("id"), id, names[id]?.takeIf { it.isNotBlank() && it != "null" } ?: "SERA professional",row.optString("last_body").takeUnless{it=="null"}.orEmpty(),row.optInt("unread_count"),photos[id],row.optString("last_created_at").takeUnless{it=="null"}.orEmpty())
        } to ""
    } catch (_: Exception) { null to "Unable to display conversations." }
}

private fun loadNativeMessages(token: String, conversationId: String, limit:Int=100): Pair<List<NativeMessage>?, String> {
    val (raw, error) = inboxRequest(token,
        "professional_messages?select=id,sender_id,body,read_at,created_at,delivered_at,reply_to_id,is_forwarded,unsent_at,client_message_id,professional_message_reactions(member_id,emoji),professional_message_attachments(file_name,mime_type,storage_path,byte_size,duration_ms)&conversation_id=eq.$conversationId&order=created_at.desc,id.desc&limit=$limit")
    if (raw == null) return null to error
    return try {
        val rows = JSONArray(raw)
        (0 until rows.length()).map { i ->
            val item = rows.getJSONObject(i)
            val attachments = item.optJSONArray("professional_message_attachments") ?: JSONArray()
            val files = (0 until attachments.length()).map { k -> val a=attachments.getJSONObject(k); NativeChatFile(a.optString("file_name"),a.optString("mime_type"),a.optString("storage_path"),a.optInt("byte_size"),a.optInt("duration_ms").takeIf{it>0}) }
            NativeMessage(item.optString("id"), item.optString("sender_id"), item.optString("body"), item.optString("read_at"), item.optString("created_at"), item.optString("delivered_at"), files,item.optString("reply_to_id").takeUnless{it=="null"}.orEmpty(),item.optBoolean("is_forwarded"),!item.isNull("unsent_at"),item.optString("client_message_id"),(item.optJSONArray("professional_message_reactions")?:JSONArray()).let{rs->(0 until rs.length()).map{rs.getJSONObject(it)}.filter{!it.isNull("emoji")}.map{it.optString("member_id") to it.optString("emoji")}})
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
    val clipboard=LocalClipboardManager.current
    val haptics=LocalHapticFeedback.current
    val swipeThreshold=with(LocalDensity.current){64.dp.toPx()}
    // Fail safely if encrypted storage is temporarily unavailable; never
    // downgrade private message drafts to unencrypted preferences.
    val savedDrafts = remember(context) { runCatching { SeraInboxDrafts(context) }.getOrNull() }
    var conversations by remember { mutableStateOf<List<NativeConversation>>(emptyList()) }
    var selected by remember(initialThread) { mutableStateOf(initialThread?.let{NativeConversation(it,initialPeer,initialName)}) }
    LaunchedEffect(selected?.id){onThreadVisibility(selected!=null)}
    DisposableEffect(Unit){onDispose{onThreadVisibility(false)}}
    var messages by remember { mutableStateOf<List<NativeMessage>>(emptyList()) }
    var callEvents by remember(selected?.id){mutableStateOf<List<SeraCallEvent>>(emptyList())}
    var draft by remember(initialThread) { mutableStateOf(initialThread?.let{savedDrafts?.read(userId,it)}.orEmpty()) }
    var error by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var peerTyping by remember { mutableStateOf(false) }
    var peerActivity by remember(selected?.id) { mutableStateOf<String?>(null) }
    var typingPulse by remember { mutableIntStateOf(0) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(token,selected?.id,lifecycle) { val peer=selected?.peerId?:return@LaunchedEffect; lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED){while(true){peerActivity=withContext(Dispatchers.IO){peerPresence(token,peer)};delay(30000)}} }
    val messageListState = rememberLazyListState()
    var lastVisibleConversation by remember { mutableStateOf<String?>(null) }
    var lastMessageId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(selected?.id, messages.lastOrNull()?.id) {
        val id = selected?.id ?: return@LaunchedEffect
        val last = messages.lastOrNull()?.id ?: return@LaunchedEffect
        val changedConversation = lastVisibleConversation != id
        val nearBottom = messageListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= messages.lastIndex - 2 } ?: true
        if (changedConversation || (last != lastMessageId && nearBottom)) { val count=messageListState.layoutInfo.totalItemsCount;if(count>0)messageListState.scrollToItem(count-1) }
        lastVisibleConversation = id
        lastMessageId = last
    }
    val scope = rememberCoroutineScope()
    var attachment by remember(selected?.id) { mutableStateOf<NativeChatSelection?>(null) }
    var checkingFile by remember { mutableStateOf(false) }
    var transferProgress by remember(selected?.id){mutableIntStateOf(-1)}
    var mediaViewer by remember(selected?.id){mutableStateOf<NativeChatFile?>(null)}
    var recordingVoice by remember(selected?.id){mutableStateOf(false)}
    var reply by remember(selected?.id){mutableStateOf<NativeMessage?>(null)}
    var menu by remember(selected?.id){mutableStateOf<String?>(null)}
    var reaction by remember(selected?.id){mutableStateOf<NativeMessage?>(null)}
    var remove by remember(selected?.id){mutableStateOf<NativeMessage?>(null)}
    var forwarding by remember(selected?.id){mutableStateOf<NativeMessage?>(null)}
    var forwardClient by remember{mutableStateOf("")}
    var historyLimit by remember(selected?.id){mutableIntStateOf(100)}
    var messageSearch by remember(selected?.id){mutableStateOf("")}
    var showSearch by remember(selected?.id){mutableStateOf(false)}
    var sound by remember{mutableStateOf(context.getSharedPreferences("sera_chat_settings",0).getBoolean("sound",false))}
    val playback=remember(context,selected?.id){SeraChatPlayback(context.applicationContext)}
    var sendFailed by remember(selected?.id){mutableStateOf(false)}
    val outbox=remember(context){runCatching{NativeChatOutbox(context)}.getOrNull()}
    var queued by remember(selected?.id){mutableStateOf<NativePending?>(null)}
    var retrySignal by remember(selected?.id){mutableIntStateOf(0)}
    var connectionAvailable by remember{mutableStateOf(true)}
    LaunchedEffect(selected?.id,userId){val c=selected?:return@LaunchedEffect;queued=withContext(Dispatchers.IO){runCatching{outbox?.load(userId,c.id)}.getOrNull()};if(queued!=null){draft="";attachment=null;reply=null}}
    LaunchedEffect(lifecycle){lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED){while(true){val cm=context.getSystemService(ConnectivityManager::class.java);val caps=cm.getNetworkCapabilities(cm.activeNetwork);connectionAvailable=caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)==true;delay(2000)}}}
    fun chime(){if(sound)runCatching{val tone=ToneGenerator(AudioManager.STREAM_NOTIFICATION,35);tone.startTone(ToneGenerator.TONE_PROP_BEEP,120);android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({tone.release()},180)}}
    LaunchedEffect(playback,playback.playing){while(playback.playing){playback.tick();delay(250)}}
    DisposableEffect(playback,lifecycle){val observer=androidx.lifecycle.LifecycleEventObserver{_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_PAUSE)playback.stop()};lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer);playback.stop()}}
    LaunchedEffect(attachment){if(playback.activeId?.startsWith("preview:")==true&&playback.activeId!="preview:"+System.identityHashCode(attachment))playback.stop()}
    fun reload(){val c=selected?:return;scope.launch{val r=withContext(Dispatchers.IO){loadNativeMessages(token,c.id,historyLimit)};if(selected?.id==c.id){r.first?.let{messages=it};error=r.second}}}
    LaunchedEffect(token,selected?.id,queued?.clientId,retrySignal,connectionAvailable){
        val c=selected?:return@LaunchedEffect;val p=queued?:return@LaunchedEffect
        if(!connectionAvailable)return@LaunchedEffect
        sending=true;transferProgress=if(p.file!=null)0 else -1
        try{
            val result=withContext(Dispatchers.IO){sendNativeMessageV2(token,userId,c.id,p.clientId,p.body,p.file,p.replyId,onProgress={percent->scope.launch{if(selected?.id==c.id)transferProgress=percent}})}
            if(result.first!=null){withContext(Dispatchers.IO){runCatching{outbox?.remove(userId,c.id,p.clientId)}};if(selected?.id==c.id){queued=null;sendFailed=false;error="";chime();reload()};runCatching{savedDrafts?.clear(userId,c.id)}}
            else{sendFailed=true;error=result.second}
        }finally{sending=false;transferProgress=-1}
    }
    fun react(m:NativeMessage,emoji:String){reaction=null;scope.launch{val r=withContext(Dispatchers.IO){chatRpc(token,"sera_set_message_reaction",JSONObject().put("p_message",m.id).put("p_emoji",if(m.reactions.any{it.first==userId&&it.second==emoji})JSONObject.NULL else emoji))};if(r.first==null)error=r.second else reload()}}
    SeraNativeMessageDialogs(remove=remove,reaction=reaction,forwarding=forwarding,conversations=conversations,token=token,userId=userId,forwardClient=forwardClient,
        onRemoveDismiss={remove=null},onReactionDismiss={reaction=null},onForwardDismiss={forwarding=null},
        onReact={message,emoji->react(message,emoji)},onChanged={refresh++;reload()},onError={error=it})

    var attachmentStudio by remember(selected?.id){mutableStateOf(false)}
    SeraNativeAttachmentPicker(conversationId=selected?.id,open=attachmentStudio,onDismiss={attachmentStudio=false},
        onChecking={checkingFile=it},onSelected={attachment=it;error=""},onError={error=it})
    LaunchedEffect(messages,mediaViewer){val file=mediaViewer;if(file!=null&&messages.none{m->!m.unsent&&m.files.any{it.path==file.path}})mediaViewer=null}
    mediaViewer?.let{file->SeraPrivateImageViewer(token,file,onDismiss={mediaViewer=null})}
    BackHandler(selected != null) { if(onThreadExit!=null)onThreadExit() else {selected = null; messages = emptyList(); error = ""} }
    LaunchedEffect(token,userId,refresh,selected?.id,lifecycle) {
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
    LaunchedEffect(token, selected?.id, lifecycle, historyLimit) {
        val conversation = selected ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            SeraRealtime(token, conversation.id).run {
                val result = withContext(Dispatchers.IO) { loadNativeMessages(token, conversation.id, historyLimit) }
                result.first?.let { latest ->
                    if(messages.isNotEmpty() && latest.lastOrNull()?.id!=messages.lastOrNull()?.id && latest.lastOrNull()?.senderId!=userId)chime()
                    messages = latest
                    val unread = latest.filter { it.senderId != userId && (it.readAt.isBlank() || it.readAt == "null") }
                    val undelivered=unread.filter{it.deliveredAt.isBlank()||it.deliveredAt=="null"}
                    if(undelivered.isNotEmpty())withContext(Dispatchers.IO){undelivered.chunked(100).forEach{batch->chatRpc(token,"sera_message_receipt",JSONObject().put("p_conversation",conversation.id).put("p_ids",JSONArray(batch.map{it.id})).put("p_read",false))}}
                }
                error = result.second
            }
        }
    }
    LaunchedEffect(token,selected?.id,lifecycle){
        val c=selected?:return@LaunchedEffect
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED){
            val seen=mutableSetOf<String>()
            snapshotFlow{messageListState.layoutInfo.visibleItemsInfo.map{it.key.toString()} to messages}
                .collectLatest{(visible,latest)->
                    delay(600)
                    val ids=latest.filter{it.id in visible&&it.senderId!=userId&&!it.unsent&&(it.readAt.isBlank()||it.readAt=="null")&&it.id !in seen}.map{it.id}.take(100)
                    if(ids.isNotEmpty()){val r=withContext(Dispatchers.IO){chatRpc(token,"sera_message_receipt",JSONObject().put("p_conversation",c.id).put("p_ids",JSONArray(ids)).put("p_read",true))};if(r.first!=null){seen.addAll(ids);SeraUnreadRefresh.changed()}}
                }
        }
    }
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
    LaunchedEffect(token, selected?.id, typingPulse) {
        val conversation = selected ?: return@LaunchedEffect
        if (typingPulse == 0) return@LaunchedEffect
        delay(350)
        withContext(Dispatchers.IO) { inboxRequest(token, "rpc/sera_set_typing", "POST", JSONObject().put("p_conversation",conversation.id).put("p_typing",draft.isNotBlank())) }
        delay(4000)
        withContext(Dispatchers.IO) { inboxRequest(token, "rpc/sera_set_typing", "POST", JSONObject().put("p_conversation",conversation.id).put("p_typing",false)) }
    }
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
            SeraNativeConversationList(conversations,search,loading,error,inboxInk,inboxSoft,chatSurface,muted,accent,inboxTeal,onInboxExit,onCompany={companyInbox=true},onSearch={search=it},onRetry={refresh++},onSelect={conversation->
                draft=savedDrafts?.read(userId,conversation.id).orEmpty();selected=conversation;messages=emptyList();error="";typingPulse=0;peerTyping=false
            })
        } else {
            val conversation = selected!!
            Row(
                modifier = Modifier.fillMaxWidth().height(60.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(onClick = { if(onThreadExit!=null)onThreadExit() else {selected = null; messages = emptyList(); error = ""} },
                    modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Back to conversations",
                        tint = inboxInk)
                }
                Box(Modifier.size(42.dp).clip(CircleShape).background(inboxSoft),contentAlignment=Alignment.Center){Text(conversation.peerName.take(1).uppercase(),color=inboxTeal,fontWeight=FontWeight.Bold,fontSize=18.sp);(conversation.photo?:conversations.firstOrNull{it.id==conversation.id}?.photo)?.let{SeraMediaImage(it,"Profile photo",112,112,Modifier.fillMaxSize())}}
                Column(Modifier.weight(1f)) {
                    Text(conversation.peerName, fontWeight = FontWeight.SemiBold,fontSize = 17.sp, color = inboxInk,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text(if (peerTyping) "Typing…" else peerActivity ?: "Private conversation", fontSize = 12.sp, color = muted,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                SeraNativeVoiceCallButton(token,userId,conversation.id,conversation.peerName,conversation.photo,ink=inboxInk,accent=accent,onAudioStart={playback.pause()},onHistory={callEvents=it})
                IconButton(onClick={showSearch=!showSearch},modifier=Modifier.size(44.dp)){Icon(Icons.Outlined.Search,"Search messages",tint=inboxInk)}
                var threadMenu by remember{mutableStateOf(false)}
                Box{IconButton(onClick={threadMenu=true},modifier=Modifier.size(44.dp)){Icon(Icons.Outlined.MoreVert,"Conversation settings",tint=inboxInk)};DropdownMenu(expanded=threadMenu,onDismissRequest={threadMenu=false}){DropdownMenuItem(text={Text(if(sound)"Mute message sounds" else "Enable message sounds")},onClick={sound=!sound;context.getSharedPreferences("sera_chat_settings",0).edit().putBoolean("sound",sound).apply();threadMenu=false})}}
            }
            if(showSearch)OutlinedTextField(messageSearch,{messageSearch=it},modifier=Modifier.fillMaxWidth(),placeholder={Text("Search loaded messages")},singleLine=true)
            HorizontalDivider(color=inboxSoft,thickness=1.dp)
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            val maxBubbleWidth=(LocalConfiguration.current.screenWidthDp.dp*.76f).coerceIn(120.dp,340.dp)
            LazyColumn(Modifier.weight(1f), state = messageListState, verticalArrangement = Arrangement.spacedBy(5.dp,Alignment.Bottom),
                contentPadding = PaddingValues(vertical = 12.dp)) {
                if(messages.size>=historyLimit && historyLimit<1000)item{TextButton(onClick={historyLimit=(historyLimit+100).coerceAtMost(1000)}){Text("Load earlier messages")}}
                val filteredMessages=messages.filter{messageSearch.isBlank()||it.body.contains(messageSearch,ignoreCase=true)}
                val timeline=(filteredMessages.map{SeraTimelineItem(it.createdAt,it,null)}+callEvents.filter{messageSearch.isBlank()||"voice call".contains(messageSearch,true)}.map{SeraTimelineItem(it.createdAt,null,it)}).sortedBy{runCatching{java.time.Instant.parse(it.at).toEpochMilli()}.getOrDefault(0)}
                itemsIndexed(timeline,key={_,item->item.message?.id?:"call-"+item.event?.id}) { index,item ->
                    val date=runCatching{java.time.Instant.parse(item.at).atZone(java.time.ZoneId.systemDefault()).toLocalDate()}.getOrNull()
                    val previousDate=timeline.getOrNull(index-1)?.let{runCatching{java.time.Instant.parse(it.at).atZone(java.time.ZoneId.systemDefault()).toLocalDate()}.getOrNull()}
                    if(date!=null&&date!=previousDate)Box(Modifier.fillMaxWidth().padding(vertical=8.dp),contentAlignment=Alignment.Center){Surface(color=inboxSoft,shape=CircleShape){Text(date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy")),fontSize=11.sp,color=muted,modifier=Modifier.padding(horizontal=12.dp,vertical=5.dp))}}
                    val message=item.message
                    if(message==null){item.event?.let{SeraCallTimelineRow(it,userId,inboxSoft,inboxInk,muted,accent)}}else SeraNativeMessageRow(message=message,originalMessage=messages.firstOrNull{it.id==message.replyId},userId=userId,peerName=conversation.peerName,token=token,playback=playback,maxBubbleWidth=maxBubbleWidth,inboxInk=inboxInk,inboxSoft=inboxSoft,chatSurface=chatSurface,muted=muted,accent=accent,outgoing=outgoing,incoming=incoming,menuOpen=menu==message.id,onMenu={menu=if(it)message.id else null},onReply={reply=message},onReact={reaction=message},onForward={forwarding=message;forwardClient=UUID.randomUUID().toString()},onCopy={clipboard.setText(AnnotatedString(message.body))},onUnsend={remove=message},onReaction={react(message,it)},onImage={mediaViewer=it},onError={error=it})
                }
            }
            SeraNativeComposer(draft=draft,attachment=attachment,reply=reply,queued=queued,sending=sending,checkingFile=checkingFile,connectionAvailable=connectionAvailable,sendFailed=sendFailed,transferProgress=transferProgress,recordingVoice=recordingVoice,ink=inboxInk,soft=inboxSoft,muted=muted,accent=accent,teal=inboxTeal,playback=playback,
                onDraftChange={draft=it;typingPulse++;runCatching{savedDrafts?.write(userId,conversation.id,it)}},
                onAttach={attachmentStudio=true;haptics.performHapticFeedback(HapticFeedbackType.LongPress)},
                onRemoveAttachment={attachment=null},onCancelReply={reply=null},onRetry={retrySignal++},
                onEditQueued={val q=queued;if(q!=null)scope.launch{withContext(Dispatchers.IO){runCatching{outbox?.remove(userId,conversation.id,q.clientId)}};queued=null;draft=q.body;attachment=q.file;sendFailed=false}},
                onVoice={attachment=it;error=""},onRecording={recordingVoice=it;if(it)playback.pause()},onError={error=it},
                onSend={

                    val body = draft.trim()
                    val file=attachment
                    if ((body.isBlank() && file == null) || sending || checkingFile || queued!=null) return@SeraNativeComposer
                    sending = true
                    scope.launch {
                        val p=NativePending(UUID.randomUUID().toString(),body,reply?.id,file)
                        val saved=withContext(Dispatchers.IO){runCatching{check(outbox!=null);outbox.save(userId,conversation.id,p)}}
                        if(saved.isSuccess){
                            runCatching{savedDrafts?.clear(userId,conversation.id)}
                            if(selected?.id==conversation.id){draft="";attachment=null;reply=null;queued=p;sendFailed=false;error=""}
                        }else error="Secure message storage is unavailable. Your draft was retained."
                        sending = false
                    }
                })
        }
    }
}
