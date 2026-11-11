package et.sera.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
    LaunchedEffect(token, postId) {
        val result = withContext(Dispatchers.IO) { fetchNativeComments(token, postId) }
        if (result.first != null) comments = result.first!! else error = result.second
        loading = false
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Conversation", style = MaterialTheme.typography.titleLarge)
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            if (!loading && comments.isEmpty() && error.isBlank()) Text("No comments yet.")
            LazyColumn(modifier = Modifier.heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(comments, key = { it.id }) { comment ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(if (comment.parentId == null) "Community comment" else "Reply", style = MaterialTheme.typography.labelMedium)
                            Text(comment.body)
                        }
                    }
                }
            }
            Text("Comment publishing is temporarily read-only until the native moderation gateway is connected.",
                style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    }
}
