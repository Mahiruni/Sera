package et.sera.nativeapp

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

private fun uploadAccountFile(context: Context, token: String, userId: String, uri: Uri, kind: String): String {
    val mime = context.contentResolver.getType(uri) ?: return "Unsupported file format."
    val avatar = kind == "avatar"
    if (avatar && mime !in setOf("image/jpeg", "image/png", "image/webp"))
        return "Choose a JPG, PNG or WebP image."
    if (!avatar && mime != "application/pdf") return "Choose a PDF document."
    val limit = if (avatar) 2 * 1024 * 1024 else 3 * 1024 * 1024
    val bytes = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val data = input.readNBytes(limit + 1)
            if (data.size > limit) return "File too large. Maximum ${limit / 1024 / 1024} MB."
            data
        } ?: return "Could not read selected file."
    } catch (_: Exception) { return "Could not read selected file." }
    if (bytes.isEmpty()) return "Selected file is empty."
    if (!avatar && !bytes.take(5).toByteArray().contentEquals("%PDF-".toByteArray()))
        return "Invalid PDF file."
    val extension = if (!avatar) "pdf" else when (mime) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "jpg"
    }
    val path = "$userId/${if (avatar) "avatar" else "cv"}/${UUID.randomUUID()}.$extension"
    var connection: HttpURLConnection? = null
    try {
        connection = (URL(BuildConfig.SUPABASE_URL.trimEnd('/') +
            "/storage/v1/object/sera-account-private/" + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", mime)
            setRequestProperty("x-upsert", "false")
        }
        connection.outputStream.use { it.write(bytes) }
        if (connection.responseCode !in 200..299) return "Upload failed (HTTP ${connection.responseCode})."
    } catch (_: Exception) { return "Upload interrupted. Please retry." }
    finally { connection?.disconnect() }

    val fields = if (avatar) JSONObject().put("avatar_path", path) else JSONObject()
        .put("cv_path", path).put("cv_name", "SERA-CV.pdf")
        .put("cv_uploaded_at", java.time.Instant.now().toString())
        .put("cv_status", "pending").put("branded_path", JSONObject.NULL)
    fields.put("version", UUID.randomUUID().toString())
    val update = saveAccountAsset(token, userId, fields, "PATCH")
    if (update.isEmpty()) return ""
    // If there is no asset row yet, create one. This remains owner-only under RLS.
    val create = saveAccountAsset(token, userId, fields.put("user_id", userId), "POST")
    return if (create.isEmpty()) "" else create
}

private fun saveAccountAsset(token: String, userId: String, fields: JSONObject, method: String): String {
    var connection: HttpURLConnection? = null
    return try {
        val suffix = if (method == "PATCH") "?user_id=eq.$userId" else ""
        connection = (URL(BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/account_assets" + suffix)
            .openConnection() as HttpURLConnection).apply {
            requestMethod = method
            doOutput = true
            connectTimeout = 12000
            readTimeout = 15000
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Prefer", "return=representation")
        }
        connection.outputStream.use { it.write(fields.toString().toByteArray()) }
        if (connection.responseCode !in 200..299) return "Could not save file metadata (HTTP ${connection.responseCode})."
        val response = connection.inputStream.bufferedReader().use { it.readText() }
        if (response == "[]") "Account file record not found." else ""
    } catch (_: Exception) { "Could not save account file metadata." }
    finally { connection?.disconnect() }
}

@Composable
internal fun SeraNativeAccountFiles(token: String, userId: String, onUploaded: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf("") }
    var selectedKind by remember { mutableStateOf("avatar") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            busy = true
            feedback = ""
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    uploadAccountFile(context, token, userId, uri, selectedKind)
                }
                busy = false
                feedback = if (result.isEmpty()) "File uploaded successfully." else result
                if (result.isEmpty()) onUploaded()
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Profile photo & CV", style = MaterialTheme.typography.titleMedium)
        Text("Upload files securely to your SERA account.",
            style = MaterialTheme.typography.bodySmall)
        OutlinedButton(
            onClick = { selectedKind = "avatar"; picker.launch("image/*") },
            enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
        ) { Text("Choose profile photo") }
        OutlinedButton(
            onClick = { selectedKind = "cv"; picker.launch("application/pdf") },
            enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
        ) { Text("Upload CV (PDF, up to 3 MB)") }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (feedback.isNotBlank()) Text(feedback, style = MaterialTheme.typography.bodySmall)
    }
}
