package et.sera.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.imePadding
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class NativeComment(
    val id: String,
    val body: String,
    val authorId: String,
    val parentId: String?,
    val createdAt: String,
    val author: String = "SERA member",
    val avatarUrl: String? = null
)

fun fetchNativeComments(token: String, postId: String): Pair<List<NativeComment>?, String> {
    return try {
        val url = URL(BuildConfig.SUPABASE_URL.trimEnd('/') +
            "/rest/v1/post_comments?select=id,author_id,parent_id,body,created_at&post_id=eq.$postId&order=created_at.desc&limit=100")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000; readTimeout = 15000
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $token")
        }
        val status = connection.responseCode
        val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        connection.disconnect()
        if (status !in 200..299) return null to "Unable to load comments (HTTP $status)."
        val rows = JSONArray(response)
        val comments = (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            NativeComment(
                row.optString("id"), row.optString("body"), row.optString("author_id"),
                row.optString("parent_id").takeIf { it.isNotBlank() && it != "null" },
                row.optString("created_at")
            )
        }
        val names = mutableMapOf<String, String>()
        val photos = mutableMapOf<String, String>()
        val authors = comments.map { it.authorId }.filter { it.isNotBlank() }.distinct()
        if (authors.isNotEmpty()) {
            try {
                val cards = (URL(BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/rpc/member_cards")
                    .openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 12000; readTimeout = 12000; doOutput = true
                    setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Content-Type", "application/json")
                }
                cards.outputStream.use {
                    it.write(JSONObject().put("ids", JSONArray(authors)).toString().toByteArray(Charsets.UTF_8))
                }
                if (cards.responseCode in 200..299) {
                    val members = JSONArray(cards.inputStream.bufferedReader().use { it.readText() })
                    for (i in 0 until members.length()) {
                        val card = members.getJSONObject(i)
                        val id = card.optString("id")
                        if (id.isBlank()) continue
                        val name = card.optString("full_name")
                        if (name.isNotBlank() && name != "null") names[id] = name
                        val avatar = card.optString("avatar_path")
                        if (avatar.isNotBlank() && avatar != "null") {
                            signedPostImage(token, avatar, "sera-account-private")?.let { photos[id] = it }
                        }
                    }
                }
                cards.disconnect()
            } catch (_: Exception) {
                // A temporarily unavailable member profile never blocks comments.
            }
        }
        comments.map { it.copy(
            author = names[it.authorId] ?: "SERA member",
            avatarUrl = photos[it.authorId]
        ) } to ""
    } catch (e: Exception) { null to "Unable to load comments. Please retry." }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeraCommentsSheet(token: String, postId: String, onDismiss: () -> Unit) {
    var comments by remember(postId) { mutableStateOf<List<NativeComment>>(emptyList()) }
    var error by remember(postId) { mutableStateOf("") }
    var loading by remember(postId) { mutableStateOf(true) }
    var retry by remember(postId) { mutableIntStateOf(0) }
    var draft by remember(postId) { mutableStateOf("") }
    var sending by remember(postId) { mutableStateOf(false) }
    var publishingError by remember(postId) { mutableStateOf("") }
    var replyTarget by remember(postId) { mutableStateOf<NativeComment?>(null) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    LaunchedEffect(token, postId, retry) {
        loading = true
        error = ""
        val result = withContext(Dispatchers.IO) { fetchNativeComments(token, postId) }
        if (result.first != null) comments = result.first!! else error = result.second
        loading = false
    }
    SeraSignatureSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.conversation), style = MaterialTheme.typography.titleLarge, color = Color(0xFF18383D), fontWeight = FontWeight.Bold)
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error.isNotBlank()) { Text(error, color = MaterialTheme.colorScheme.error); OutlinedButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) } }
            if (!loading && comments.isEmpty() && error.isBlank()) Text(stringResource(R.string.no_comments))
            LazyColumn(modifier = Modifier.heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(comments, key = { it.id }) { comment ->
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFF7FBFA)),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (!comment.avatarUrl.isNullOrBlank()) {
                                    SeraMediaImage(
                                        url = comment.avatarUrl,
                                        description = "Profile photo of " + comment.author,
                                        widthPx = 96, heightPx = 96,
                                        modifier = Modifier.size(38.dp).clip(CircleShape)
                                    )
                                } else {
                                    Box(Modifier.size(38.dp).background(Color(0xFFE0F2EF), CircleShape),
                                        contentAlignment = Alignment.Center) {
                                        Text(comment.author.take(1).uppercase(), color = Color(0xFF087E83),
                                            fontWeight = FontWeight.Bold)
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(comment.author, style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold, color = Color(0xFF18383D))
                                    val date = remember(comment.createdAt) {
                                        runCatching {
                                            DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
                                                .withZone(ZoneId.systemDefault())
                                                .format(Instant.parse(comment.createdAt))
                                        }.getOrDefault("")
                                    }
                                    if (date.isNotBlank()) Text(date, style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF688184))
                                }
                            }
                            Text(comment.body, style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF18383D))
                            TextButton(onClick = { replyTarget = comment },
                                contentPadding = PaddingValues(0.dp)) {
                                Text("Reply", color = Color(0xFF087E83),
                                    style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
            replyTarget?.let { replying ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Replying to " + replying.author,
                        style = MaterialTheme.typography.labelMedium, color = Color(0xFF087E83))
                    TextButton(onClick = { replyTarget = null }) { Text("Cancel") }
                }
            }
            OutlinedTextField(
                value = draft, onValueChange = { if (it.length <= 2000) draft = it },
                label = { Text(stringResource(R.string.write_comment)) }, modifier = Modifier.fillMaxWidth(),
                maxLines = 5, enabled = !sending, shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() })
            )
            if (publishingError.isNotBlank()) Text(publishingError, color = MaterialTheme.colorScheme.error)
            if (sending) LinearProgressIndicator(Modifier.fillMaxWidth())
            Button(onClick = {
                sending = true
                publishingError = ""
                focus.clearFocus()
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        publishNativeComment(token, postId, draft.trim(), replyTarget?.let { it.parentId ?: it.id })
                    }
                    if (result.first) {
                        draft = ""
                        replyTarget = null
                        retry++
                    } else publishingError = result.second
                    sending = false
                }
            }, enabled = !sending && draft.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF087E83))) {
                Text(if (sending) "Publishing…" else if (replyTarget != null) "Publish reply" else "Publish comment")
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    }
}

private fun publishNativeComment(token: String, postId: String, body: String, parentId: String? = null): Pair<Boolean, String> {
    return try {
        val connection = (URL("https://sera.hisabtech.com/api/native/comments").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 15000; readTimeout = 20000; doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
        }
        connection.outputStream.use { it.write(JSONObject().put("post_id", postId).put("body", body).put("parent_id", parentId).toString().toByteArray()) }
        val status = connection.responseCode
        val response = (if (status in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        connection.disconnect()
        if (status in 200..299) true to "" else false to (try { JSONObject(response).optString("error").ifBlank { "Unable to publish (HTTP $status)." } } catch (_: Exception) { "Unable to publish (HTTP $status)." })
    } catch (_: Exception) { false to "Network unavailable. Your comment is still here." }
}
