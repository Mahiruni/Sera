package et.sera.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private fun requestNativePasswordReset(email: String): String {
    var connection: HttpURLConnection? = null
    return try {
        connection = (URL(BuildConfig.SUPABASE_URL.trimEnd('/') + "/auth/v1/recover")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 12000
            readTimeout = 16000
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Content-Type", "application/json")
        }
        connection.outputStream.use {
            it.write(JSONObject().put("email", email.trim()).toString().toByteArray())
        }
        if (connection.responseCode in 200..299)
            "If this address belongs to a SERA account, a recovery email has been requested. Check your inbox."
        else if (connection.responseCode == 429)
            "Too many requests. Please wait before trying again."
        else "Could not request a recovery email (HTTP ${connection.responseCode})."
    } catch (_: Exception) { "Connection failed. Please retry." }
    finally { connection?.disconnect() }
}

@Composable
internal fun SeraNativePasswordRecovery(onBack: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack) { Text("← Back to sign in") }
        Text("Recover your account", style = MaterialTheme.typography.headlineMedium)
        Text("Enter the email address associated with SERA. We'll request a secure recovery message.",
            style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = email, onValueChange = { email = it.take(254) },
            label = { Text("Email address") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                busy = true
                message = ""
                scope.launch {
                    message = withContext(Dispatchers.IO) { requestNativePasswordReset(email) }
                    busy = false
                }
            },
            enabled = !busy && email.contains("@") && email.substringAfter("@").contains("."),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) { Text(if (busy) "Requesting…" else "Send recovery email") }
        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodyMedium)
        Text("The recovery link may open a browser until secure Android deep-link handling is configured.",
            style = MaterialTheme.typography.bodySmall)
    }
}
