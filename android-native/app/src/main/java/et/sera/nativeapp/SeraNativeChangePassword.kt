package et.sera.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Change a signed-in user's password directly through Supabase Auth, never a web redirect. */
private fun updateNativePassword(token: String, password: String): String {
    var connection: HttpURLConnection? = null
    return try {
        connection = (URL(BuildConfig.SUPABASE_URL.trimEnd('/') + "/auth/v1/user")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            doOutput = true
            connectTimeout = 12000
            readTimeout = 16000
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
        }
        connection.outputStream.use {
            it.write(JSONObject().put("password", password).toString().toByteArray())
        }
        when (connection.responseCode) {
            in 200..299 -> ""
            401, 403 -> "Your session has expired. Please sign in again."
            422 -> "Password was rejected. Choose a stronger password or try again."
            429 -> "Too many attempts. Please wait and retry."
            else -> "Password change failed (HTTP ${connection.responseCode})."
        }
    } catch (_: Exception) { "Could not connect. Please retry." }
    finally { connection?.disconnect() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SeraNativeChangePassword(token: String, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf("") }
    var completed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Account security", style = MaterialTheme.typography.headlineSmall)
            Text("Change your SERA password securely in the Android app.",
                style = MaterialTheme.typography.bodyMedium)
            if (!completed) {
                OutlinedTextField(password, { password = it.take(128) },
                    label = { Text("New password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(confirm, { confirm = it.take(128) },
                    label = { Text("Confirm new password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth())
                if (confirm.isNotEmpty() && password != confirm)
                    Text("Passwords do not match.", color = MaterialTheme.colorScheme.error)
                Button(onClick = {
                    busy = true
                    feedback = ""
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { updateNativePassword(token, password) }
                        busy = false
                        if (result.isEmpty()) {
                            completed = true
                            password = ""
                            confirm = ""
                            feedback = "Your password was updated."
                        } else feedback = result
                    }
                }, enabled = !busy && password.length >= 8 && password == confirm,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(if (busy) "Updating…" else "Update password")
                }
            }
            if (feedback.isNotEmpty()) Text(feedback,
                color = if (completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            if (completed) Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Done")
            }
        }
    }
}
