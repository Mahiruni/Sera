package et.sera.nativeapp

import android.os.Bundle
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.ui.res.stringResource
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

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SeraApp(this) }
    }
}

@Composable
fun SeraApp(context: Context) {
    val session = remember { SessionStore(context) }
    val haptics = LocalHapticFeedback.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var accessToken by remember { mutableStateOf<String?>(null) }
    var restoring by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        val refresh = session.readRefreshToken()
        if (refresh != null) {
            val result = withContext(Dispatchers.IO) { authenticate("refresh_token", refresh) }
            if (result.first != null) { accessToken = result.first; result.third?.let { session.saveRefreshToken(it) } }
            else session.clear()
        }
        restoring = false
    }
    // Request the one-time welcome email promptly after any verified native sign-in.
    // This is a separate IO coroutine so the dashboard never waits for SMTP.
    LaunchedEffect(accessToken) {
        val token = accessToken ?: return@LaunchedEffect
        withContext(Dispatchers.IO) { requestSeraWelcome(token) }
    }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf("Home") }
    var showComposer by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var authenticatedUserId by remember { mutableStateOf("") }
    val tabHistory = remember { mutableStateListOf<String>() }
    fun navigateTo(destination: String) {
        if (destination == tab) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        tabHistory.add(tab)
        if (tabHistory.size > 12) tabHistory.removeAt(0)
        tab = destination
    }
    val scope = rememberCoroutineScope()
    var feed by remember { mutableStateOf<List<SeraPost>>(emptyList()) }
    var identity by remember { mutableStateOf("") }
    var dataError by remember { mutableStateOf("") }
    var dataLoading by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(accessToken, tab, refresh) {
        val token = accessToken ?: return@LaunchedEffect
        if (authenticatedUserId.isBlank()) {
            val userIdResult = withContext(Dispatchers.IO) { loadSeraUserId(token) }
            if (userIdResult.first != null) authenticatedUserId = userIdResult.first!!
        }
        if (tab != "Home" && tab != "Profile") return@LaunchedEffect
        dataLoading = true
        dataError = ""
        if (tab == "Home") {
            val result = withContext(Dispatchers.IO) { loadSeraFeed(token) }
            if (result.first != null) feed = result.first!! else dataError = result.second
        } else {
            val result = withContext(Dispatchers.IO) { loadSeraIdentity(token) }
            if (result.first != null) identity = result.first!! else dataError = result.second
        }
        dataLoading = false
    }
    if (showComposer && accessToken != null && authenticatedUserId.isNotBlank()) SeraComposer(userId = authenticatedUserId, token = accessToken!!, onPublished = { showComposer = false; refresh++ }, onDismiss = { showComposer = false })
    if (showLanguagePicker) {
        val locales = listOf("" to stringResource(R.string.system_language), "en" to "English", "am" to "አማርኛ", "om" to "Afaan Oromoo", "ti" to "ትግርኛ", "ar" to "العربية", "aa" to "Qafar af", "so" to "Soomaali")
        AlertDialog(onDismissRequest = { showLanguagePicker = false },
            title = { Text(stringResource(R.string.language)) },
            text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                locales.forEach { (code, name) ->
                    TextButton(onClick = {
                        showLanguagePicker = false
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
                    }, modifier = Modifier.fillMaxWidth()) { Text(name) }
                }
            } },
            confirmButton = { TextButton(onClick = { showLanguagePicker = false }) { Text(stringResource(R.string.close)) } })
    }
    BackHandler(enabled = accessToken != null && (tab != "Home" || tabHistory.isNotEmpty())) { tab = if (tabHistory.isNotEmpty()) tabHistory.removeAt(tabHistory.lastIndex) else "Home" }
    MaterialTheme(colorScheme = lightColorScheme(primary = teal, background = bg, surface = bg)) {
        Surface(modifier = Modifier.fillMaxSize(), color = bg) {
            if (restoring) {
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (accessToken == null) {
                SeraGuestEntry(
                    email = email,
                    password = password,
                    onEmailChange = { email = it; message = "" },
                    onPasswordChange = { password = it; message = "" },
                    busy = busy,
                    message = message,
                    onSignIn = {
                        busy = true
                        message = ""
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { signIn(email.trim(), password) }
                            if (result.first != null) result.third?.let { session.saveRefreshToken(it) }
                            authenticatedUserId = ""
                            accessToken = result.first
                            message = result.second
                            busy = false
                        }
                    },
                    onLanguage = { showLanguagePicker = true },
                    onAuthenticated = { token, refreshToken ->
                        session.saveRefreshToken(refreshToken)
                        authenticatedUserId = ""
                        accessToken = token
                    }
                )
            } else {
                BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    val expanded = maxWidth >= 700.dp
                    val sections = listOf("Home", "Network", "Jobs", "Inbox", "Profile")
                    Row(Modifier.fillMaxSize()) {
                        if (expanded) NavigationRail {
                            sections.forEach { item ->
                                NavigationRailItem(
                                    selected = tab == item, onClick = { navigateTo(item) },
                                    icon = { Text(sectionSymbol(item), fontSize = 20.sp) },
                                    label = { Text(sectionLabel(item)) }
                                )
                            }
                        }
                        Scaffold(
                            modifier = Modifier.weight(1f),
                            contentWindowInsets = WindowInsets(0, 0, 0, 0),
                            topBar = {
                                Surface(color = bg, tonalElevation = 2.dp) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("SERA.et", color = teal, fontWeight = FontWeight.Bold, fontSize = 25.sp)
                                        TextButton(onClick = { showLanguagePicker = true }) { Text("🌐") }
                                        if (tab == "Home") TextButton(onClick = { showComposer = true }, enabled = authenticatedUserId.isNotBlank()) { Text(stringResource(R.string.create_post)) } else Text(sectionLabel(tab), color = ink)
                                    }
                                }
                            },
                            bottomBar = {
                                if (!expanded) SeraFloatingDock(
                                    selected = tab,
                                    sections = sections,
                                    onNavigate = ::navigateTo
                                )
                            }
                        ) { padding ->
                            Crossfade(targetState = tab, animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy), label = "section transition") { currentTab ->
                                if (currentTab == "Home") {
                                    SeraPagedFeed(accessToken ?: "", refresh, { post ->
                                        val token = accessToken
                                        if (token != null) scope.launch {
                                            val result = withContext(Dispatchers.IO) { setSeraLike(token, post.id, post.liked) }
                                            if (result.first) refresh++ else dataError = result.second
                                        }
                                    }, Modifier.padding(padding))
                                } else if (currentTab == "Jobs") {
                                    SeraNativeJobs(accessToken ?: "", Modifier.padding(padding))
                                } else if (currentTab == "Network") {
                                    SeraNativeNetwork(accessToken ?: "", authenticatedUserId, Modifier.padding(padding))
                                } else if (currentTab == "Inbox") {
                                    SeraNativeInbox(accessToken ?: "", authenticatedUserId, Modifier.padding(padding))
                                } else {
                                    Column(
                                        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        if (currentTab == "Profile") {
                                            SeraProfile(identity, dataLoading, dataError, { refresh++ }, {
                                                session.clear()
                                                accessToken = null
                                                authenticatedUserId = ""
                                                showComposer = false
                                                password = ""
                                                message = ""
                                                tab = "Home"
                                            })
                                        } else {
                                            Text(sectionLabel(currentTab), color = ink, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                                            Text(sectionDescription(currentTab), color = ink)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun signIn(email: String, password: String): Triple<String?, String, String?> = authenticate("password", password, email)

private fun authenticate(grantType: String, credential: String, email: String = ""): Triple<String?, String, String?> {
    if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank())
        return Triple(null, "Supabase public configuration is missing from the build.", null)
    return try {
        val url = URL(BuildConfig.SUPABASE_URL.trimEnd('/') + "/auth/v1/token?grant_type=" + grantType)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000; readTimeout = 15000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
        }
        connection.outputStream.use { it.write((if (grantType == "password") JSONObject().put("email", email).put("password", credential) else JSONObject().put("refresh_token", credential)).toString().toByteArray()) }
        val response = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        if (connection.responseCode in 200..299) {
            val data = JSONObject(response)
            Triple(data.optString("access_token").takeIf { it.isNotEmpty() }, "Signed in successfully", data.optString("refresh_token").takeIf { it.isNotEmpty() })
        } else Triple(null, JSONObject(response).optString("msg").ifBlank { "Sign-in failed" }, null)
    } catch (e: Exception) { Triple(null, "Connection failed. Please retry.", null) }
}


private class SessionStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "sera_native_session",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    fun readRefreshToken(): String? = prefs.getString("refresh_token", null)
    fun saveRefreshToken(value: String) { prefs.edit().putString("refresh_token", value).apply() }
    fun clear() { prefs.edit().clear().apply() }
}

private fun sectionSymbol(section: String): String = when (section) {
    "Home" -> "⌂"
    "Network" -> "◎"
    "Jobs" -> "▣"
    "Inbox" -> "✉"
    else -> "◉"
}

private fun sectionDescription(section: String): String = when (section) {
    "Home" -> "Your professional feed will appear here when the native feed integration is ready."
    "Network" -> "Discover and connect with professionals. Network data integration is next."
    "Jobs" -> "Find relevant opportunities. Native job discovery is coming."
    "Inbox" -> "Your conversations will appear here after messaging integration."
    else -> "Your professional identity. Profile data integration is coming."
}

@Composable
private fun sectionLabel(section: String): String = when (section) {
    "Home" -> stringResource(R.string.home)
    "Network" -> stringResource(R.string.network)
    "Jobs" -> stringResource(R.string.jobs)
    "Inbox" -> stringResource(R.string.inbox)
    "Profile" -> stringResource(R.string.profile)
    else -> section
}
