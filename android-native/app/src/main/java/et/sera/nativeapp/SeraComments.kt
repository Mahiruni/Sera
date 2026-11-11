package et.sera.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
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

data class NativeComment(val id: String, val body: String, val authorId: String, val parentId: String?)

fun fetchNativeComments(token: String, postId: String): Pair<List<NativeComment>?, String> {
    return try {
        val url = URL(BuildConfig.SUPABASE_URL.trimEnd('/') +
            "/rest/v1/post_comments?select=id,author_id,parent_id,body&post_id=eq.$postId&order=created_at.desc&limit=100")
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
        (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            NativeComment(row.optString("id"), row.optString("body"), row.optString("author_id"),
                row.optString("parent_id").takeIf { it.isNotBlank() && it != "null" })
        } to ""
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
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    LaunchedEffect(token, postId, retry) {
        loading = true
        error = ""
        val result = withContext(Dispatchers.IO) { fetchNativeComments(token, postId) }
        if (result.first != null) comments = result.first!! else error = result.second
        loading = false
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.conversation), style = MaterialTheme.typography.titleLarge)
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error.isNotBlank()) { Text(error, color = MaterialTheme.colorScheme.error); OutlinedButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) } }
            if (!loading && comments.isEmpty() && error.isBlank()) Text(stringResource(R.string.no_comments))
            LazyColumn(modifier = Modifier.heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(comments, key = { it.id }) { comment ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(if (comment.parentId == null) stringResource(R.string.community_comment) else stringResource(R.string.reply), style = MaterialTheme.typography.labelMedium)
                            Text(comment.body)
                        }
                    }
                }
            }
            OutlinedTextField(
                value = draft, onValueChange = { if (it.length <= 2000) draft = it },
                label = { Text(stringResource(R.string.write_comment)) }, modifier = Modifier.fillMaxWidth(),
                maxLines = 5, enabled = !sending,
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
                    val result = withContext(Dispatchers.IO) { publishNativeComment(token, postId, draft.trim()) }
                    if (result.first) {
                        draft = ""
                        retry++
                    } else publishingError = result.second
                    sending = false
                }
            }, enabled = !sending && draft.isNotBlank()) { Text(if (sending) "Publishing…" else "Publish comment") }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    }
}

private fun publishNativeComment(token: String, postId: String, body: String): Pair<Boolean, String> {
    return try {
        val connection = (URL("https://sera.hisabtech.com/api/native/comments").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 15000; readTimeout = 20000; doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
        }
        connection.outputStream.use { it.write(JSONObject().put("post_id", postId).put("body", body).toString().toByteArray()) }
        val status = connection.responseCode
        val response = (if (status in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        connection.disconnect()
        if (status in 200..299) true to "" else false to (try { JSONObject(response).optString("error").ifBlank { "Unable to publish (HTTP $status)." } } catch (_: Exception) { "Unable to publish (HTTP $status)." })
    } catch (_: Exception) { false to "Network unavailable. Your comment is still here." }
}
