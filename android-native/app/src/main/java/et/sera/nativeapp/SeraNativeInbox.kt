package et.sera.nativeapp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
private data class NativeConversation(val id: String, val peerId: String, val peerName: String)
private data class NativeMessage(val id: String, val senderId: String, val body: String, val readAt: String)

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
                setRequestProperty("Prefer", "return=minimal")
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
        "professional_conversations?select=id,member_a,member_b&or=(member_a.eq.$userId,member_b.eq.$userId)&order=last_message_at.desc&limit=60")
    if (raw == null) return null to error
    return try {
        val rows = JSONArray(raw)
        val peers = (0 until rows.length()).map { rows.getJSONObject(it) }
        val ids = peers.map { if (it.optString("member_a") == userId) it.optString("member_b") else it.optString("member_a") }
        val names = mutableMapOf<String, String>()
        if (ids.isNotEmpty()) {
            val (cards, _) = inboxRequest(token, "rpc/member_cards", "POST", JSONObject().put("ids", JSONArray(ids)))
            if (cards != null) {
                val entries = JSONArray(cards)
                for (i in 0 until entries.length()) {
                    val entry = entries.getJSONObject(i)
                    names[entry.optString("id")] = entry.optString("full_name")
                }
            }
        }
        peers.map { row ->
            val id = if (row.optString("member_a") == userId) row.optString("member_b") else row.optString("member_a")
            NativeConversation(row.optString("id"), id, names[id]?.takeIf { it.isNotBlank() && it != "null" } ?: "SERA professional")
        } to ""
    } catch (_: Exception) { null to "Unable to display conversations." }
}

private fun loadNativeMessages(token: String, conversationId: String): Pair<List<NativeMessage>?, String> {
    val (raw, error) = inboxRequest(token,
        "professional_messages?select=id,sender_id,body,read_at&conversation_id=eq.$conversationId&order=created_at.asc&limit=100")
    if (raw == null) return null to error
    return try {
        val rows = JSONArray(raw)
        (0 until rows.length()).map { i ->
            val item = rows.getJSONObject(i)
            NativeMessage(item.optString("id"), item.optString("sender_id"), item.optString("body"), item.optString("read_at"))
        } to ""
    } catch (_: Exception) { null to "Unable to display messages." }
}

@Composable
fun SeraNativeInbox(token: String, userId: String, modifier: Modifier = Modifier) {
    var conversations by remember { mutableStateOf<List<NativeConversation>>(emptyList()) }
    var selected by remember { mutableStateOf<NativeConversation?>(null) }
    var messages by remember { mutableStateOf<List<NativeMessage>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    BackHandler(selected != null) { selected = null; messages = emptyList(); draft = ""; error = "" }
    LaunchedEffect(token, userId, refresh) {
        if (userId.isBlank()) return@LaunchedEffect
        loading = true
        val result = withContext(Dispatchers.IO) { loadNativeConversations(token, userId) }
        result.first?.let { conversations = it }
        error = result.second
        loading = false
    }
    LaunchedEffect(token, selected?.id) {
        val conversation = selected ?: return@LaunchedEffect
        while (true) {
            val result = withContext(Dispatchers.IO) { loadNativeMessages(token, conversation.id) }
            result.first?.let { messages = it }
            if (result.second.isNotBlank()) error = result.second else error = ""
            delay(5000)
        }
    }
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        if (selected == null) {
            Text("Conversations", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = inboxInk)
            Text("Private messages with your professional network", fontSize = 13.sp, color = Color.Gray)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { refresh++ }, enabled = !loading) { Text("Refresh") }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            if (!loading && conversations.isEmpty() && error.isBlank())
                Text("No conversations yet. Start a conversation from SERA Network.", modifier = Modifier.padding(top = 20.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(conversations, key = { it.id }) { conversation ->
                    ElevatedCard(onClick = { selected = conversation; messages = emptyList(); error = "" },
                        shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(46.dp).clip(CircleShape).background(Color(0xFFE0F4F0)),
                                contentAlignment = Alignment.Center) {
                                Text(conversation.peerName.take(1).uppercase(), color = inboxTeal, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text(conversation.peerName, fontWeight = FontWeight.Bold, color = inboxInk)
                                Text("Tap to open conversation", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        } else {
            val conversation = selected!!
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { selected = null; messages = emptyList(); error = "" }) { Text("←") }
                Text(conversation.peerName, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = inboxInk)
            }
            HorizontalDivider()
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 14.dp)) {
                items(messages, key = { it.id }) { message ->
                    val own = message.senderId == userId
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (own) Arrangement.End else Arrangement.Start) {
                        Surface(shape = RoundedCornerShape(18.dp),
                            color = if (own) inboxTeal else Color.White,
                            modifier = Modifier.widthIn(max = 300.dp)) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Text(message.body, color = if (own) Color.White else inboxInk)
                                if (own) Text(if (message.readAt.isNotBlank() && message.readAt != "null") "Read" else "Sent",
                                    color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp),
                verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft, onValueChange = { if (it.length <= 4000) draft = it },
                    modifier = Modifier.weight(1f), placeholder = { Text("Write a message…") },
                    shape = RoundedCornerShape(22.dp), maxLines = 5
                )
                Button(onClick = {
                    val body = draft.trim()
                    if (body.isBlank() || sending) return@Button
                    sending = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            inboxRequest(token, "professional_messages", "POST",
                                JSONObject().put("conversation_id", conversation.id)
                                    .put("sender_id", userId).put("body", body))
                        }
                        if (result.first != null) {
                            draft = ""
                            val refreshed = withContext(Dispatchers.IO) { loadNativeMessages(token, conversation.id) }
                            refreshed.first?.let { messages = it }
                        } else error = result.second
                        sending = false
                    }
                }, enabled = draft.isNotBlank() && !sending, contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.heightIn(min = 54.dp)) { Text(if (sending) "…" else "Send") }
            }
        }
    }
}
