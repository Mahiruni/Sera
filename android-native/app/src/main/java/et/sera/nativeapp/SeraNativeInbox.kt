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
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.outlined.Search
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

private data class NativeConversation(val id:String,val peerId:String,val peerName:String,val avatar:String?=null,val preview:String="",val lastAt:String="",val mine:Boolean=false,val verified:Boolean=false,val unread:Int=0)
private data class NativeMessage(val id: String, val senderId: String, val body: String, val readAt: String, val createdAt: String, val deliveredAt: String, val files: List<NativeChatFile>)

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
        "professional_conversations?select=id,member_a,member_b,last_message_at,professional_messages(body,sender_id,created_at)&professional_messages.order=created_at.desc,id.desc&professional_messages.limit=1&or=(member_a.eq.$userId,member_b.eq.$userId)&order=last_message_at.desc&limit=60")
    if (raw == null) return null to error
    return try {
        val rows = JSONArray(raw)
        val peers = (0 until rows.length()).map { rows.getJSONObject(it) }
        val ids = peers.map { if (it.optString("member_a") == userId) it.optString("member_b") else it.optString("member_a") }
        val names = mutableMapOf<String, String>()
        val avatars=mutableMapOf<String,String>()
        val badges=identityBadges(token,ids)
        if (ids.isNotEmpty()) {
            val (cards, _) = inboxRequest(token, "rpc/member_cards", "POST", JSONObject().put("ids", JSONArray(ids)))
            if (cards != null) {
                val entries = JSONArray(cards)
                for (i in 0 until entries.length()) {
                    val entry = entries.getJSONObject(i)
                    names[entry.optString("id")] = entry.optString("full_name")
                    val path=entry.optString("avatar_path");if(path.isNotBlank()&&path!="null")signedPostImage(token,path,"sera-account-private")?.let{avatars[entry.optString("id")]=it}
                }
            }
        }
        val unread=mutableMapOf<String,Int>()
        val conversationIds=peers.map{it.optString("id")}.filter{it.isNotBlank()}
        if(conversationIds.isNotEmpty()){val rawUnread=inboxRequest(token,"professional_messages?select=conversation_id&conversation_id=in.("+conversationIds.joinToString(",")+")&sender_id=neq.$userId&read_at=is.null&limit=1000").first; if(rawUnread!=null){val rows=JSONArray(rawUnread);for(i in 0 until rows.length()){val id=rows.getJSONObject(i).optString("conversation_id");unread[id]=(unread[id]?:0)+1}}}
        peers.map { row ->
            val id = if (row.optString("member_a") == userId) row.optString("member_b") else row.optString("member_a")
            val latest=row.optJSONArray("professional_messages")?.optJSONObject(0)
            NativeConversation(row.optString("id"),id,names[id]?.takeIf{it.isNotBlank()&&it!="null"}?:"SERA professional",avatars[id],latest?.optString("body").orEmpty(),latest?.optString("created_at").orEmpty(),latest?.optString("sender_id")==userId,id in badges,unread[row.optString("id")]?:0)
        } to ""
    } catch (_: Exception) { null to "Unable to display conversations." }
}

private fun loadNativeMessages(token: String, conversationId: String): Pair<List<NativeMessage>?, String> {
    val (raw, error) = inboxRequest(token,
        "professional_messages?select=id,sender_id,body,read_at,created_at,delivered_at,professional_message_attachments(file_name,mime_type,storage_path,byte_size)&conversation_id=eq.$conversationId&order=created_at.desc,id.desc&limit=100")
    if (raw == null) return null to error
    return try {
        val rows = JSONArray(raw)
        (0 until rows.length()).map { i ->
            val item = rows.getJSONObject(i)
            val attachments = item.optJSONArray("professional_message_attachments") ?: JSONArray()
            val files = (0 until attachments.length()).map { k -> val a=attachments.getJSONObject(k); NativeChatFile(a.optString("file_name"),a.optString("mime_type"),a.optString("storage_path"),a.optInt("byte_size")) }
            NativeMessage(item.optString("id"), item.optString("sender_id"), item.optString("body"), item.optString("read_at"), item.optString("created_at"), item.optString("delivered_at"), files)
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
fun SeraNativeInbox(token: String, userId: String, modifier: Modifier = Modifier, openCompany: Boolean=false, onCompanyConsumed:()->Unit={}, initialThread:String?=null, initialPeer:String="", initialName:String="", onThreadExit:(()->Unit)?=null,onConversationVisible:(Boolean)->Unit={}) {
    var companyInbox by remember { mutableStateOf(false) }
    LaunchedEffect(openCompany){if(openCompany){companyInbox=true;onCompanyConsumed()}}
    if(companyInbox){LaunchedEffect(Unit){onConversationVisible(true)};DisposableEffect(Unit){onDispose{onConversationVisible(false)}};Box(modifier.fillMaxSize()){SeraCompanyInbox(token,userId,onBack={companyInbox=false})};return}
    val context = LocalContext.current
    // Fail safely if encrypted storage is temporarily unavailable; never
    // downgrade private message drafts to unencrypted preferences.
    val savedDrafts = remember(context) { runCatching { SeraInboxDrafts(context) }.getOrNull() }
    var conversations by remember { mutableStateOf<List<NativeConversation>>(emptyList()) }
    var selected by remember(initialThread) { mutableStateOf(initialThread?.let{NativeConversation(it,initialPeer,initialName)}) }
    val visibleCallback by rememberUpdatedState(onConversationVisible)
    LaunchedEffect(selected?.id){visibleCallback(selected!=null)}
    DisposableEffect(Unit){onDispose{visibleCallback(false)}}
    var messages by remember { mutableStateOf<List<NativeMessage>>(emptyList()) }
    var draft by remember(initialThread) { mutableStateOf(initialThread?.let{savedDrafts?.read(userId,it)}.orEmpty()) }
    var error by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    val inboxListState=rememberLazyListState()
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
        if (changedConversation || (last != lastMessageId && nearBottom)) messageListState.scrollToItem(messages.lastIndex)
        lastVisibleConversation = id
        lastMessageId = last
    }
    val scope = rememberCoroutineScope()
    var attachment by remember(selected?.id) { mutableStateOf<NativeChatSelection?>(null) }
    var checkingFile by remember { mutableStateOf(false) }
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
    LaunchedEffect(token, selected?.id, lifecycle) {
        val conversation = selected ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            SeraRealtime(token, conversation.id).run {
                val result = withContext(Dispatchers.IO) { loadNativeMessages(token, conversation.id) }
                result.first?.let { latest ->
                    messages = latest
                    val unread = latest.filter { it.senderId != userId && (it.readAt.isBlank() || it.readAt == "null") }
                    if (unread.isNotEmpty()) {
                        val receipt=withContext(Dispatchers.IO) { inboxRequest(token, "rpc/sera_message_receipt", "POST", JSONObject().put("p_conversation",conversation.id).put("p_ids",JSONArray(unread.map { it.id })).put("p_read",true)) }
                        if(receipt.first!=null){SeraUnreadRefresh.changed();conversations=conversations.map{if(it.id==conversation.id)it.copy(unread=0) else it}}
                    }
                }
                error = result.second
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
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp)) {
        Spacer(Modifier.height(12.dp))
        if (selected == null) {
            Text("Messages",fontSize=26.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface)
            TextButton(onClick={companyInbox=true}){Text("Company messages") }
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
            LazyColumn(state=inboxListState,verticalArrangement = Arrangement.spacedBy(0.dp)) {
                items(filtered, key = { it.id }) { conversation ->
                    Surface(onClick = {
                        draft = savedDrafts?.read(userId, conversation.id).orEmpty()
                        selected = conversation; messages = emptyList(); error = ""; typingPulse = 0; peerTyping = false
                    },
                        shape=RoundedCornerShape(0.dp),modifier=Modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.surface) {
                        Row(Modifier.padding(vertical=12.dp,horizontal=4.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SeraAccountAvatar(conversation.peerName,conversation.avatar,50)
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){Text(conversation.peerName,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onSurface,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f,fill=false));if(conversation.verified)SeraIdentityBadge()}
                                Text((if(conversation.mine)"You: " else "")+conversation.preview.ifBlank{"Start a conversation"},fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                            }
                            Column(horizontalAlignment=Alignment.End){Text(inboxTime(conversation.lastAt),fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);if(conversation.unread>0)Badge(containerColor=inboxTeal,contentColor=Color.White){Text(if(conversation.unread>99)"99+" else conversation.unread.toString())}}
                        }
                    }
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                }
            }
        } else {
            val conversation = selected!!
            val peer=conversations.firstOrNull{it.id==conversation.id}?:conversation
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(onClick = { if(onThreadExit!=null)onThreadExit() else {selected = null; messages = emptyList(); error = ""} },
                    modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Back to conversations",
                        tint = MaterialTheme.colorScheme.onSurface)
                }
                SeraAccountAvatar(peer.peerName,peer.avatar,42)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){Text(peer.peerName,fontWeight=FontWeight.Bold,fontSize=17.sp,color=MaterialTheme.colorScheme.onSurface,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f,fill=false));if(peer.verified)SeraIdentityBadge()}
                    Text(if (peerTyping) "Typing…" else peerActivity ?: "Professional conversation", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            LazyColumn(Modifier.weight(1f), state = messageListState, verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 14.dp)) {
                items(messages, key = { it.id }) { message ->
                    val index=messages.indexOf(message)
                    val date=inboxDay(message.createdAt)
                    if(index==0||inboxDay(messages[index-1].createdAt)!=date){Box(Modifier.fillMaxWidth().padding(vertical=8.dp),contentAlignment=Alignment.Center){Text(date,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
                    val own = message.senderId == userId
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (own) Arrangement.End else Arrangement.Start) {
                        Surface(shape = RoundedCornerShape(18.dp),
                            color = if (own) inboxTeal else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.widthIn(max = 300.dp)) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                if (message.body != "Attachment" || message.files.isEmpty()) Text(message.body, color = if (own) Color.White else MaterialTheme.colorScheme.onSurface)
                                message.files.forEach { file ->
                                    TextButton(onClick = { scope.launch {
                                        val result = withContext(Dispatchers.IO) { runCatching { downloadChatFile(context,token,file) } }
                                        result.onSuccess { uri -> runCatching { openChatFile(context,uri,file.mime) }.onFailure { error="No app can open this file." } }.onFailure { error=it.message ?: "Could not open attachment." }
                                    } }, colors = ButtonDefaults.textButtonColors(contentColor = if (own) Color.White else inboxTeal)) {
                                        Icon(Icons.Outlined.Description,null,Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text(file.name, maxLines=2)
                                    }
                                    Text("${file.bytes / 1024} KB · ${if (file.mime == "application/pdf") "PDF" else "Image"}",fontSize=11.sp,color=if(own) Color.White.copy(alpha=.8f) else inboxInk)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    val time = runCatching { java.time.Instant.parse(message.createdAt).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) }.getOrDefault("")
                                    Text(time, color = if (own) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                    if (own) {
                                        val read = message.readAt.isNotBlank() && message.readAt != "null"
                                        val delivered = read || (message.deliveredAt.isNotBlank() && message.deliveredAt != "null")
                                        val receipt = if (read) "Read" else if (delivered) "Delivered" else "Sent"
                                        Icon(if (delivered) Icons.Outlined.DoneAll else Icons.Outlined.Check, contentDescription = receipt, tint = if(read)Color(0xFFAEF6E7) else Color.White.copy(alpha=.85f), modifier = Modifier.size(15.dp))
                                        Text(receipt, color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            attachment?.let { file ->
                Surface(color=inboxSoft, shape=RoundedCornerShape(14.dp), modifier=Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Description,null,tint=inboxTeal); Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) { Text(file.name,maxLines=1,fontWeight=FontWeight.SemiBold); Text("${file.bytes.size / 1024} KB · Ready to send",fontSize=11.sp) }
                        IconButton(onClick={attachment=null},enabled=!sending) { Icon(Icons.Outlined.Close,"Remove attachment") }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().imePadding().navigationBarsPadding()
                    .padding(top = 11.dp, bottom = 10.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick={attachmentPicker.launch(arrayOf("image/jpeg","image/png","image/webp","application/pdf"))},enabled=!sending && !checkingFile,modifier=Modifier.size(44.dp)) { Icon(Icons.Outlined.AttachFile,"Attach image or PDF",tint=inboxTeal) }
                OutlinedTextField(
                    value = draft, onValueChange = {
                        if (it.length <= 4000) {
                            draft = it
                            typingPulse++
                            runCatching { savedDrafts?.write(userId, conversation.id, it) }
                        }
                    },
                    modifier = Modifier.weight(1f), placeholder = { Text("Write a message…") },
                    shape = RoundedCornerShape(24.dp), maxLines = 5, keyboardOptions=seraMultilineKeyboard, singleLine=false, enabled=!sending
                )
                Button(onClick = {
                    val body = draft.trim()
                    val file=attachment
                    if ((body.isBlank() && file == null) || sending || checkingFile) return@Button
                    sending = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            if(file != null) { val failure=sendChatAttachment(token,userId,conversation.id,body,file); if(failure.isBlank()) "ok" to "" else null to failure }
                            else { val result=inboxRequest(token, "professional_messages", "POST", JSONObject().put("conversation_id",conversation.id).put("sender_id",userId).put("body",body)); result.first?.let { raw -> runCatching { SeraPush.dispatch(token,"message",JSONArray(raw).getJSONObject(0).getString("id")) } }; result }
                        }
                        if (result.first != null) {
                            if (selected?.id == conversation.id) { draft = ""; attachment=null }
                            runCatching { savedDrafts?.clear(userId, conversation.id) }
                            val refreshed = withContext(Dispatchers.IO) { loadNativeMessages(token, conversation.id) }
                            if (selected?.id == conversation.id) refreshed.first?.let { messages = it }
                            refresh++
                        } else error = result.second
                        sending = false
                    }
                }, enabled = (draft.isNotBlank() || attachment != null) && !sending && !checkingFile,
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    modifier = Modifier.heightIn(min = 54.dp),
                    shape = RoundedCornerShape(28.dp),
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

private fun inboxTime(raw:String):String=runCatching{val date=java.time.Instant.parse(raw).atZone(java.time.ZoneId.systemDefault());if(date.toLocalDate()==java.time.LocalDate.now())date.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) else date.format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))}.getOrDefault("")

private fun inboxDay(raw:String):String=runCatching{val day=java.time.Instant.parse(raw).atZone(java.time.ZoneId.systemDefault()).toLocalDate();when(day){java.time.LocalDate.now()->"Today";java.time.LocalDate.now().minusDays(1)->"Yesterday";else->day.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy"))}}.getOrDefault("")
