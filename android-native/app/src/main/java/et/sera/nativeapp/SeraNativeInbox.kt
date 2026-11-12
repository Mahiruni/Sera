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
import androidx.compose.foundation.gestures.detectTapGestures
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

private data class NativeConversation(val id: String, val peerId: String, val peerName: String, val preview:String="", val unread:Int=0, val photo:String?=null, val lastAt:String="")
private data class NativeMessage(val id: String, val senderId: String, val body: String, val readAt: String, val createdAt: String, val deliveredAt: String, val files: List<NativeChatFile>, val replyId:String="", val forwarded:Boolean=false, val unsent:Boolean=false, val clientId:String="", val reactions:List<Pair<String,String>> = emptyList())

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
fun SeraNativeInbox(token: String, userId: String, modifier: Modifier = Modifier, openCompany: Boolean=false, onCompanyConsumed:()->Unit={}, initialThread:String?=null, initialPeer:String="", initialName:String="", onThreadExit:(()->Unit)?=null, onThreadVisibility:(Boolean)->Unit={}) {
    var companyInbox by remember { mutableStateOf(false) }
    LaunchedEffect(openCompany){if(openCompany){companyInbox=true;onCompanyConsumed()}}
    if(companyInbox){Box(modifier.fillMaxSize()){SeraCompanyInbox(token,userId,onBack={companyInbox=false})};return}
    val context = LocalContext.current
    // Fail safely if encrypted storage is temporarily unavailable; never
    // downgrade private message drafts to unencrypted preferences.
    val savedDrafts = remember(context) { runCatching { SeraInboxDrafts(context) }.getOrNull() }
    var conversations by remember { mutableStateOf<List<NativeConversation>>(emptyList()) }
    var selected by remember(initialThread) { mutableStateOf(initialThread?.let{NativeConversation(it,initialPeer,initialName)}) }
    LaunchedEffect(selected?.id){onThreadVisibility(selected!=null)}
    DisposableEffect(Unit){onDispose{onThreadVisibility(false)}}
    var messages by remember { mutableStateOf<List<NativeMessage>>(emptyList()) }
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
    DisposableEffect(playback,lifecycle){val observer=androidx.lifecycle.LifecycleEventObserver{_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_PAUSE)playback.pause()};lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer);playback.stop()}}
    LaunchedEffect(attachment){if(playback.activeId?.startsWith("preview:")==true&&playback.activeId!="preview:"+System.identityHashCode(attachment))playback.stop()}
    fun reload(){val c=selected?:return;scope.launch{val r=withContext(Dispatchers.IO){loadNativeMessages(token,c.id,historyLimit)};if(selected?.id==c.id){r.first?.let{messages=it};error=r.second}}}
    LaunchedEffect(token,selected?.id,queued?.clientId,retrySignal,connectionAvailable){
        val c=selected?:return@LaunchedEffect;val p=queued?:return@LaunchedEffect
        if(!connectionAvailable)return@LaunchedEffect
        sending=true
        try{
            val result=withContext(Dispatchers.IO){sendNativeMessageV2(token,userId,c.id,p.clientId,p.body,p.file,p.replyId)}
            if(result.first!=null){withContext(Dispatchers.IO){runCatching{outbox?.remove(userId,c.id,p.clientId)}};if(selected?.id==c.id){queued=null;sendFailed=false;error="";chime();reload()};runCatching{savedDrafts?.clear(userId,c.id)}}
            else{sendFailed=true;error=result.second}
        }finally{sending=false}
    }
    fun react(m:NativeMessage,emoji:String){reaction=null;scope.launch{val r=withContext(Dispatchers.IO){chatRpc(token,"sera_set_message_reaction",JSONObject().put("p_message",m.id).put("p_emoji",if(m.reactions.any{it.first==userId&&it.second==emoji})JSONObject.NULL else emoji))};if(r.first==null)error=r.second else reload()}}
    if(remove!=null)AlertDialog(onDismissRequest={remove=null},title={Text("Unsend message?")},text={Text("This message will be removed for everyone.")},confirmButton={TextButton(onClick={val id=remove!!.id;remove=null;scope.launch{val r=withContext(Dispatchers.IO){unsendNativeMessage(token,id)};if(r.first==null)error=r.second else reload()}}){Text("Unsend")}},dismissButton={TextButton(onClick={remove=null}){Text("Cancel")}})
    if(reaction!=null)AlertDialog(onDismissRequest={reaction=null},title={Text("React to message")},text={Row{listOf("👍","❤️","😂","😮","😢","👏").forEach{emoji->TextButton(onClick={reaction?.let{react(it,emoji)}},contentPadding=PaddingValues(2.dp)){Text(emoji,fontSize=20.sp)}}}},confirmButton={TextButton(onClick={reaction=null}){Text("Close")}})
    if(forwarding!=null)AlertDialog(onDismissRequest={forwarding=null},title={Text("Forward message")},text={Column{conversations.forEach{target->TextButton(onClick={val source=forwarding?:return@TextButton;scope.launch{val r=withContext(Dispatchers.IO){runCatching{val f=source.files.firstOrNull()?.let{NativeChatSelection(it.name,it.mime,readChatFile(token,it),it.durationMs)};sendNativeMessageV2(token,userId,target.id,forwardClient,source.body,f,forward=source.id)}.getOrElse{null to "Forward failed. Retry."}};if(r.first!=null){forwarding=null;refresh++;reload()}else error=r.second}}){Text(target.peerName)}}}},confirmButton={TextButton(onClick={forwarding=null}){Text("Cancel")}})

    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val conversationId = selected?.id
            checkingFile = true
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { selectChatFile(context,uri) } }
                if (selected?.id == conversationId) result.onSuccess { attachment=it; error="" }.onFailure { error=it.message ?: "Could not read file." }
                checkingFile=false
            }
        }
    }
    BackHandler(selected != null) { if(onThreadExit!=null)onThreadExit() else {selected = null; messages = emptyList(); error = ""} }
    LaunchedEffect(token, userId, refresh) {
        if (userId.isBlank()) return@LaunchedEffect
        loading = true
        val result = withContext(Dispatchers.IO) { loadNativeConversations(token, userId) }
        result.first?.let { conversations = it }
        error = result.second
        loading = false
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
    val chatSurface = if(dark) Color(0xFF14282D) else Color(0xFFFAFCFC)
    val muted = if(dark) Color(0xFFA8C3C5) else Color(0xFF62777B)
    Column(modifier.fillMaxSize().background(chatSurface).then(if(selected!=null) Modifier.imePadding().navigationBarsPadding() else Modifier).padding(horizontal = 12.dp)) {
        Spacer(Modifier.height(12.dp))
        if (selected == null) {
            Text("Messages", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = inboxInk, modifier=Modifier.padding(horizontal=6.dp,vertical=4.dp))
            FilledTonalButton(onClick={companyInbox=true},modifier=Modifier.fillMaxWidth().padding(top=8.dp),shape=RoundedCornerShape(16.dp)){Text("Company inbox · Job conversations")}
            OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), singleLine = true, placeholder = { Text("Search conversations") }, leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = inboxTeal) }, shape = RoundedCornerShape(18.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = inboxTeal, unfocusedBorderColor = Color(0xFFD6E6E3)))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { refresh++ }, enabled = !loading) { Text("Refresh") }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            if (!loading && conversations.isEmpty() && error.isBlank())
                Text("No conversations yet. Start a conversation from SERA Network.", modifier = Modifier.padding(top = 20.dp))
            val filtered = conversations.filter { it.peerName.contains(search.trim(), ignoreCase = true) }
            if (search.isNotBlank() && filtered.isEmpty() && !loading) Text("No matching conversations.", modifier = Modifier.padding(vertical = 16.dp), color = Color.Gray)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp),contentPadding=PaddingValues(bottom=12.dp)) {
                items(filtered, key = { it.id }) { conversation ->
                    Card(onClick = {
                        draft = savedDrafts?.read(userId, conversation.id).orEmpty()
                        selected = conversation; messages = emptyList(); error = ""; typingPulse = 0; peerTyping = false
                    },
                        shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = if(conversation.unread>0) inboxSoft else chatSurface)) {
                        Row(Modifier.padding(horizontal=12.dp,vertical=12.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(52.dp).clip(CircleShape).background(inboxSoft),
                                contentAlignment = Alignment.Center) {
                                Text(conversation.peerName.take(1).uppercase(), color = inboxTeal, fontWeight = FontWeight.Bold)
                                conversation.photo?.let{SeraMediaImage(it,"Profile photo",128,128,Modifier.fillMaxSize())}
                            }
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                Text(conversation.peerName, fontWeight = if(conversation.unread>0) FontWeight.Bold else FontWeight.SemiBold, color = inboxInk,fontSize=16.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                                Text(conversation.preview.ifBlank{"Start a conversation"}, fontSize = 13.sp, color = muted,maxLines=1,overflow=TextOverflow.Ellipsis)
                            }
                            Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(5.dp)){val stamp=runCatching{java.time.Instant.parse(conversation.lastAt).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))}.getOrDefault("");if(stamp.isNotBlank())Text(stamp,fontSize=11.sp,color=muted);if(conversation.unread>0) Box(Modifier.size(24.dp).clip(CircleShape).background(inboxTeal),contentAlignment=Alignment.Center){Text(if(conversation.unread>99)"99+" else conversation.unread.toString(),fontSize=10.sp,color=Color.White,fontWeight=FontWeight.Bold)}}
                        }
                    }
                }
            }
        } else {
            val conversation = selected!!
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp),
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
                IconButton(onClick={showSearch=!showSearch},modifier=Modifier.size(48.dp)){Icon(Icons.Outlined.Search,"Search messages",tint=inboxInk)}
                IconButton(onClick={sound=!sound;context.getSharedPreferences("sera_chat_settings",0).edit().putBoolean("sound",sound).apply()},modifier=Modifier.size(48.dp)){Icon(if(sound)Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff,"Message sounds",tint=inboxInk)}
            }
            if(showSearch)OutlinedTextField(messageSearch,{messageSearch=it},modifier=Modifier.fillMaxWidth(),placeholder={Text("Search loaded messages")},singleLine=true)
            HorizontalDivider()
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            val maxBubbleWidth=(LocalConfiguration.current.screenWidthDp.dp-84.dp).coerceIn(120.dp,360.dp)
            LazyColumn(Modifier.weight(1f), state = messageListState, verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 14.dp)) {
                if(messages.size>=historyLimit && historyLimit<1000)item{TextButton(onClick={historyLimit=(historyLimit+100).coerceAtMost(1000)}){Text("Load earlier messages")}}
                items(messages.filter{messageSearch.isBlank()||it.body.contains(messageSearch,ignoreCase=true)}, key = { it.id }) { message ->
                    val own = message.senderId == userId
                    val actions: @Composable () -> Unit = {
                        if(!message.unsent) Box {
                            IconButton(onClick={menu=message.id},modifier=Modifier.size(48.dp)){Icon(Icons.Outlined.MoreVert,"Message actions",tint=muted)}
                            DropdownMenu(expanded=menu==message.id,onDismissRequest={menu=null}) {
                                DropdownMenuItem(text={Text("Reply")},onClick={reply=message;menu=null})
                                DropdownMenuItem(text={Text("React")},onClick={reaction=message;menu=null})
                                DropdownMenuItem(text={Text("Forward")},onClick={forwarding=message;forwardClient=UUID.randomUUID().toString();menu=null})
                                if(own)DropdownMenuItem(text={Text("Unsend")},onClick={remove=message;menu=null})
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (own) Arrangement.End else Arrangement.Start,verticalAlignment=Alignment.Bottom) {
                        if(own) actions()
                        Surface(shape = if(own) RoundedCornerShape(topStart=20.dp,topEnd=20.dp,bottomStart=20.dp,bottomEnd=6.dp) else RoundedCornerShape(topStart=20.dp,topEnd=20.dp,bottomStart=6.dp,bottomEnd=20.dp),
                            color = if (own) inboxTeal else inboxSoft,
                            modifier = Modifier.widthIn(max = maxBubbleWidth).then(if(!message.unsent)Modifier.pointerInput(message.id){detectTapGestures(onLongPress={menu=message.id})}else Modifier)) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                if(message.forwarded&&!message.unsent)Text("Forwarded",fontSize=10.sp,color=if(own)Color.White else inboxTeal)
                                if(message.replyId.isNotBlank()&&!message.unsent)Surface(color=if(own)Color.White.copy(alpha=.13f)else chatSurface,shape=RoundedCornerShape(10.dp),modifier=Modifier.padding(bottom=6.dp)){Column(Modifier.padding(horizontal=10.dp,vertical=7.dp)){Text("Reply",fontSize=11.sp,fontWeight=FontWeight.SemiBold,color=if(own)Color.White else inboxTeal);Text(messages.firstOrNull{it.id==message.replyId}?.body?.take(100)?:"Earlier message",fontSize=12.sp,color=if(own)Color.White else inboxInk,maxLines=2,overflow=TextOverflow.Ellipsis)}}
                                if (message.body != "Attachment" || message.files.isEmpty()) Text(if(message.unsent) "Message unsent" else message.body, color = if (own) Color.White else inboxInk,fontSize=15.sp,lineHeight=21.sp)
                                if(!message.unsent&&message.reactions.isNotEmpty()) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(3.dp)){message.reactions.groupBy{it.second}.forEach{(emoji,people)->Surface(color=if(own)Color.White.copy(alpha=.16f)else chatSurface,shape=CircleShape){TextButton(onClick={react(message,emoji)},contentPadding=PaddingValues(horizontal=6.dp,vertical=2.dp)){Text(emoji+" "+people.size,color=if(own)Color.White else inboxInk,fontSize=12.sp)}}}}

                                message.files.filter{!message.unsent}.forEach { file ->
                                    if(file.mime.startsWith("audio/")){SeraChatVoicePlayer(playback,file.path,file.durationMs,if(own)Color.White else inboxTeal,onPlay={scope.launch{playback.toggle(file.path,file.mime,{readChatFile(token,file)},{error=it})}})}else{
                                    TextButton(onClick = { scope.launch {
                                        val result = withContext(Dispatchers.IO) { runCatching { downloadChatFile(context,token,file) } }
                                        result.onSuccess { uri -> runCatching { openChatFile(context,uri,file.mime) }.onFailure { error="No app can open this file." } }.onFailure { error=it.message ?: "Could not open attachment." }
                                    } }, colors = ButtonDefaults.textButtonColors(contentColor = if (own) Color.White else inboxTeal)) {
                                        Icon(Icons.Outlined.Description,null,Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text(file.name, maxLines=2)
                                    }
                                    Text("${file.bytes / 1024} KB · ${if (file.mime == "application/pdf") "PDF" else "Image"}",fontSize=11.sp,color=if(own) Color.White.copy(alpha=.8f) else inboxInk)}
                                }
                                Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp,Alignment.End)) {
                                    val time = runCatching { java.time.Instant.parse(message.createdAt).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) }.getOrDefault("")
                                    Text(time, color = if (own) Color.White.copy(alpha = 0.8f) else Color(0xFF607478), fontSize = 10.sp)
                                    if (own) {
                                        val read = message.readAt.isNotBlank() && message.readAt != "null"
                                        val delivered = read || (message.deliveredAt.isNotBlank() && message.deliveredAt != "null")
                                        val receipt = if (read) "Read" else if (delivered) "Delivered" else "Sent"
                                        Icon(if (delivered) Icons.Outlined.DoneAll else Icons.Outlined.Check, contentDescription = receipt, tint = if(read) Color(0xFFB5FFF4) else Color.White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
                                        Text(receipt, color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                        if(!own) actions()
                    }
                }
            }
            reply?.let{m->Surface(color=inboxSoft,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(8.dp),verticalAlignment=Alignment.CenterVertically){Text("Replying to: "+m.body.take(100),modifier=Modifier.weight(1f),fontSize=12.sp);IconButton(onClick={reply=null}){Icon(Icons.Outlined.Close,"Cancel reply")}}}}
            queued?.let{q->Surface(color=inboxSoft,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(10.dp)){Text(q.body.ifBlank{q.file?.name?:"Attachment"},maxLines=2);Text(if(sending)"Sending…" else if(!connectionAvailable)"Queued · waiting for connection" else if(sendFailed)"Not sent · retry" else "Queued",fontSize=12.sp,color=inboxTeal);Row{TextButton(onClick={retrySignal++},enabled=!sending&&connectionAvailable){Text("Retry")};TextButton(onClick={scope.launch{withContext(Dispatchers.IO){runCatching{outbox?.remove(userId,conversation.id,q.clientId)}};queued=null;draft=q.body;attachment=q.file;sendFailed=false}},enabled=!sending){Text("Edit message")}}}}}
            attachment?.let { file ->
                Surface(color=inboxSoft, shape=RoundedCornerShape(14.dp), modifier=Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
                        if(file.mime.startsWith("audio/")){Box(Modifier.weight(1f)){SeraChatVoicePlayer(playback,"preview:"+System.identityHashCode(file),file.durationMs,inboxTeal,label="Voice preview",onPlay={scope.launch{playback.toggle("preview:"+System.identityHashCode(file),file.mime,{file.bytes},{error=it})}})}}else{Icon(Icons.Outlined.Description,null,tint=inboxTeal); Spacer(Modifier.width(8.dp));Column(Modifier.weight(1f)) { Text(file.name,maxLines=1,fontWeight=FontWeight.SemiBold); Text("${file.bytes.size / 1024} KB · Ready to send",fontSize=11.sp) }}
                        IconButton(onClick={attachment=null},enabled=!sending) { Icon(Icons.Outlined.Close,"Remove attachment") }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if(!recordingVoice) IconButton(onClick={attachmentPicker.launch(arrayOf("image/jpeg","image/png","image/webp","application/pdf","audio/mp4","audio/webm","audio/ogg","audio/mpeg"))},enabled=!sending && !checkingFile,modifier=Modifier.size(48.dp)) { Icon(Icons.Outlined.AttachFile,"Attach image or PDF",tint=inboxTeal) }
                SeraChatVoice(enabled=!sending&&attachment==null,onReady={attachment=it;error=""},onError={error=it},onRecordingChange={recordingVoice=it;if(it)playback.pause()})
                if(!recordingVoice) OutlinedTextField(
                    value = draft, onValueChange = {
                        if (it.length <= 4000) {
                            draft = it
                            typingPulse++
                            runCatching { savedDrafts?.write(userId, conversation.id, it) }
                        }
                    },
                    modifier = Modifier.weight(1f), placeholder = { Text("Write a message…") },
                    shape = RoundedCornerShape(24.dp), maxLines = 5, colors=OutlinedTextFieldDefaults.colors(focusedContainerColor=inboxSoft,unfocusedContainerColor=inboxSoft,focusedBorderColor=inboxTeal,unfocusedBorderColor=Color.Transparent)
                )
                if(!recordingVoice) Button(onClick = {
                    val body = draft.trim()
                    val file=attachment
                    if ((body.isBlank() && file == null) || sending || checkingFile || queued!=null) return@Button
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
                }, enabled = (draft.isNotBlank() || attachment != null) && !sending && !checkingFile && queued==null,
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    modifier = Modifier.size(50.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = inboxTeal)) {
                    if (sending) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                            color = Color.White)
                    } else {
                        Icon(Icons.Outlined.Send, contentDescription = "Send message",
                            modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}
