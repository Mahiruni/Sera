package et.sera.nativeapp

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val teal = Color(0xFF087E83)
private val ink = Color(0xFF183B40)
private val bg = Color(0xFFF5FAFB)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(245,250,251)
        window.navigationBarColor = android.graphics.Color.rgb(245,250,251)
        setContent { SeraApp() }
    }
}

@Composable
fun SeraApp() {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var accessToken by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf("Home") }
    val scope = rememberCoroutineScope()
    MaterialTheme(colorScheme = lightColorScheme(primary = teal, background = bg)) {
        Surface(modifier = Modifier.fillMaxSize(), color = bg) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("SERA.et", color = teal, fontWeight = FontWeight.Bold, fontSize = 30.sp)
                Text("Your professional world, connected.", color = ink, fontSize = 16.sp)
                if (accessToken == null) {
                    Spacer(Modifier.height(20.dp))
                    Text("Welcome back", fontWeight = FontWeight.SemiBold, fontSize = 24.sp)
                    OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(password, { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        busy = true; message = ""
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { signIn(email.trim(), password) }
                            accessToken = result.first
                            message = result.second
                            busy = false
                        }
                    }, enabled = !busy && email.isNotBlank() && password.isNotBlank(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Text(if (busy) "Signing in…" else "Sign in")
                    }
                    Text("Sign in with your existing SERA account.", color = ink)
                } else {
                    Text("Signed in", color = teal, fontWeight = FontWeight.Bold)
                    Text("Native Android foundation is ready. Feed, messaging and profile data are coming in Phase 2.", color = ink)
                    Spacer(Modifier.weight(1f))
                    NavigationBar {
                        listOf("Home", "Network", "Jobs", "Inbox", "Profile").forEach { item ->
                            NavigationBarItem(selected = tab == item, onClick = { tab = item }, icon = { Text("•") }, label = { Text(item) })
                        }
                    }
                    Text("Selected: $tab", color = ink)
                    TextButton(onClick = { accessToken = null; password = ""; message = "" }) { Text("Sign out") }
                }
                if (message.isNotEmpty()) Text(message, color = ink)
            }
        }
    }
}

private fun signIn(email: String, password: String): Pair<String?, String> {
    if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank())
        return null to "Supabase public configuration is missing from the build."
    return try {
        val url = URL(BuildConfig.SUPABASE_URL.trimEnd('/') + "/auth/v1/token?grant_type=password")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000; readTimeout = 15000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
        }
        connection.outputStream.use { it.write(JSONObject().put("email", email).put("password", password).toString().toByteArray()) }
        val response = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        if (connection.responseCode in 200..299) JSONObject(response).optString("access_token").takeIf { it.isNotEmpty() } to "Signed in successfully"
        else null to (JSONObject(response).optString("msg").ifBlank { "Sign-in failed" })
    } catch (e: Exception) { null to "Connection failed. Please retry." }
}
