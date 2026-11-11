package et.sera.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal data class NativeProfileFields(
    val fullName: String = "",
    val headline: String = "",
    val location: String = "",
    val summary: String = "",
    val skills: String = ""
)

private fun profileApi(token: String, path: String, method: String = "GET", payload: JSONObject? = null): Pair<String?, String> {
    var connection: HttpURLConnection? = null
    return try {
        connection = (URL(BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/" + path)
            .openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12000
            readTimeout = 16000
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            if (payload != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Prefer", "return=minimal")
            }
        }
        if (payload != null) connection.outputStream.use { it.write(payload.toString().toByteArray()) }
        val status = connection.responseCode
        if (status !in 200..299) return null to when(status) {
            401, 403 -> "You do not have permission to update this profile."
            else -> "Profile request failed (HTTP $status)."
        }
        (connection.inputStream?.bufferedReader()?.use { it.readText() } ?: "") to ""
    } catch (_: Exception) { null to "Could not connect to your profile. Please retry." }
    finally { connection?.disconnect() }
}

private fun loadNativeProfile(token: String, userId: String): Pair<NativeProfileFields?, String> {
    val (raw, error) = profileApi(token,
        "profiles?select=full_name,headline,location,summary,skills&id=eq.$userId&limit=1")
    if (raw == null) return null to error
    return try {
        val data = JSONArray(raw)
        if (data.length() == 0) return null to "Profile not found. Please complete account setup first."
        val obj = data.getJSONObject(0)
        fun field(key: String) = obj.optString(key).takeUnless { it == "null" }.orEmpty()
        NativeProfileFields(field("full_name"), field("headline"), field("location"),
            field("summary"), field("skills")) to ""
    } catch (_: Exception) { null to "Unable to read profile details." }
}

private fun saveNativeProfile(token: String, userId: String, data: NativeProfileFields): String {
    val body = JSONObject().put("full_name", data.fullName.trim())
        .put("headline", data.headline.trim()).put("location", data.location.trim())
        .put("summary", data.summary.trim()).put("skills", data.skills.trim())
    val (result, error) = profileApi(token, "profiles?id=eq.$userId", "PATCH", body)
    return if (result != null) "" else error
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SeraNativeProfileEditor(
    token: String,
    userId: String,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    var form by remember { mutableStateOf(NativeProfileFields()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(token, userId) {
        val result = withContext(Dispatchers.IO) { loadNativeProfile(token, userId) }
        result.first?.let { form = it }
        error = result.second
        loading = false
    }
    SeraSignatureSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 690.dp)
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Edit professional profile", style = MaterialTheme.typography.headlineSmall)
            Text("Your identity, skills and experience on SERA.",
                style = MaterialTheme.typography.bodySmall)
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            if (!loading && error.isBlank() || !loading && form.fullName.isNotBlank()) {
                OutlinedTextField(form.fullName, { if (it.length <= 100) form = form.copy(fullName = it) },
                    label = { Text("Full name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(form.headline, { if (it.length <= 160) form = form.copy(headline = it) },
                    label = { Text("Professional headline") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(form.location, { if (it.length <= 120) form = form.copy(location = it) },
                    label = { Text("Location") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(form.skills, { if (it.length <= 1000) form = form.copy(skills = it) },
                    label = { Text("Skills") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(form.summary, { if (it.length <= 2000) form = form.copy(summary = it) },
                    label = { Text("About me") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                Button(onClick = {
                    saving = true
                    error = ""
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { saveNativeProfile(token, userId, form) }
                        saving = false
                        if (result.isBlank()) onSaved() else error = result
                    }
                }, enabled = !saving && form.fullName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(if (saving) "Saving…" else "Save changes")
                }
            }
        }
    }
}
