package et.sera.nativeapp

import android.os.Bundle
import android.content.Intent
import android.net.Uri
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
import kotlinx.coroutines.delay
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
    private val callback = mutableStateOf<Uri?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        callback.value = intent?.data?.takeIf { it.scheme == "sera" }
        enableEdgeToEdge()
        setContent { SeraApp(this, callback.value, onCallbackConsumed = { callback.value = null }) }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        callback.value = intent.data?.takeIf { it.scheme == "sera" }
    }
}

@Composable
fun SeraApp(context: Context, oauthCallback: Uri? = null, onCallbackConsumed: () -> Unit = {}) {
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

    var availableUpdate by remember { mutableStateOf<SeraRelease?>(null) }
    var updateNotice by remember { mutableStateOf("") }
    var activeDownloadId by remember { mutableStateOf<Long?>(null) }
    var activeRelease by remember { mutableStateOf<SeraRelease?>(null) }
    var updateProgress by remember { mutableIntStateOf(0) }
    var updateBytes by remember { mutableLongStateOf(0L) }
    var updateTotalBytes by remember { mutableLongStateOf(-1L) }
    var updateDownloaded by remember { mutableStateOf(false) }
    var checkingApkIntegrity by remember { mutableStateOf(false) }
    var installerPermissionRequired by remember { mutableStateOf(false) }
    var installerRequested by remember { mutableStateOf(false) }

    // A user-initiated update survives activity recreation, keyboard changes,
    // backgrounding and process restarts. Never re-enqueue the same download.
    LaunchedEffect(Unit) {
        val pending = readSeraPendingUpdate(context)
        if (pending != null) {
            activeRelease = pending.release
            activeDownloadId = pending.id
        } else {
            availableUpdate = checkSeraUpdate()
        }
    }

    fun launchVerifiedInstaller() {
        val id = activeDownloadId ?: return
        if (!updateDownloaded) return
        when (openSeraInstaller(context, id)) {
            SeraInstallResult.LAUNCHED -> {
                installerRequested = true
                installerPermissionRequired = false
            }
            SeraInstallResult.PERMISSION_REQUIRED -> {
                installerPermissionRequired = true
                installerRequested = false
            }
            SeraInstallResult.FAILED -> {
                installerRequested = false
                updateNotice = "Android could not open the installer. The verified APK is still saved; tap Install update to retry."
            }
        }
    }

    // The user may have returned from Android's per-app install permission page.
    // Resume only the already downloaded and verified update.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (installerPermissionRequired && updateDownloaded && activeDownloadId != null &&
            context.packageManager.canRequestPackageInstalls()) {
            launchVerifiedInstaller()
        }
    }

    LaunchedEffect(activeDownloadId) {
        val id = activeDownloadId ?: return@LaunchedEffect
        while (true) {
            val status = withContext(Dispatchers.IO) { seraDownloadStatus(context, id) }
            updateBytes = status.downloadedBytes
            updateTotalBytes = status.totalBytes
            updateProgress = if (status.complete) 100 else status.progress.coerceAtMost(99)
            if (status.complete) {
                checkingApkIntegrity = true
                val expected = activeRelease?.sha256.orEmpty()
                val verified = withContext(Dispatchers.IO) {
                    verifySeraDownload(context, id, expected)
                }
                checkingApkIntegrity = false
                if (verified) {
                    updateDownloaded = true
                    // Download began only after the user approved this update.
                    // Launch the Android approval screen automatically after verification.
                    launchVerifiedInstaller()
                } else {
                    updateNotice = "APK security verification failed. Installation was blocked. Please download the update again."
                    discardSeraDownload(context, id)
                    activeDownloadId = null
                    activeRelease = null
                }
                break
            }
            if (status.failed) {
                updateNotice = "Update download failed. Check your connection, reopen SERA and retry."
                discardSeraDownload(context, id)
                activeDownloadId = null
                activeRelease = null
                break
            }
            delay(900)
        }
    }

    availableUpdate?.let { release ->
        SeraUpdatePrompt(
            release,
            onDismiss = { availableUpdate = null },
            onDownload = {
                val id = downloadSeraUpdate(context, release)
                if (id != null) {
                    saveSeraPendingUpdate(context, id, release)
                    activeRelease = release
                    updateDownloaded = false
                    checkingApkIntegrity = false
                    installerPermissionRequired = false
                    installerRequested = false
                    updateProgress = 0
                    updateBytes = 0L
                    updateTotalBytes = -1L
                    activeDownloadId = id
                } else updateNotice = "Unable to start the download. Please try again."
                availableUpdate = null
            }
        )
    }

    if (activeDownloadId != null) AlertDialog(
        onDismissRequest = { if (updateDownloaded) activeDownloadId = null },
        title = {
            Text(when {
                checkingApkIntegrity -> "Verifying SERA update"
                installerPermissionRequired -> "Allow SERA updates"
                updateDownloaded -> "SERA update is ready"
                else -> "Updating SERA"
            })
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    checkingApkIntegrity -> {
                        Text("Download complete. Checking APK integrity before installation…")
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    installerPermissionRequired -> Text(
                        "Android requires one-time permission to install updates from SERA. " +
                        "Choose Allow from this source in Android settings, then return to SERA. " +
                        "Your verified update is saved."
                    )
                    updateDownloaded -> Text(
                        if (installerRequested)
                            "Android's installer has been opened. Approve the installation to finish updating SERA. " +
                            "If you cancelled, tap Install update to try again."
                        else "The downloaded APK passed verification. Tap Install update to continue with Android's approval."
                    )
                    else -> {
                        Text(
                            if (updateTotalBytes > 0L)
                                "Downloading inside SERA… ${updateProgress}%"
                            else "Downloading securely inside SERA…"
                        )
                        if (updateTotalBytes > 0L) LinearProgressIndicator(
                            progress = { updateProgress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        ) else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        if (updateBytes > 0L) Text(
                            if (updateTotalBytes > 0L)
                                "${"%.1f".format(updateBytes / 1048576.0)} MB / " +
                                "${"%.1f".format(updateTotalBytes / 1048576.0)} MB"
                            else "${"%.1f".format(updateBytes / 1048576.0)} MB downloaded",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            when {
                installerPermissionRequired -> Button(onClick = {
                    if (!requestSeraInstallPermission(context))
                        updateNotice = "Open Android Settings → Apps → Special app access → Install unknown apps → SERA."
                }) { Text("Allow installation") }
                updateDownloaded -> Button(onClick = { launchVerifiedInstaller() }) {
                    Text("Install update")
                }
            }
        },
        dismissButton = {
            if (updateDownloaded) TextButton(onClick = { activeDownloadId = null }) {
                Text("Later")
            }
        }
    )
    if (updateNotice.isNotBlank()) AlertDialog(
        onDismissRequest = { updateNotice = "" },
        title = { Text("SERA update") },
        text = { Text(updateNotice) },
        confirmButton = {
            TextButton(onClick = { updateNotice = "" }) { Text("OK") }
        }
    )
    var oauthFailure by remember { mutableStateOf("") }
    LaunchedEffect(oauthCallback) {
        val callbackUri = oauthCallback ?: return@LaunchedEffect
        onCallbackConsumed()
        val result = withContext(Dispatchers.IO) { finishNativeOAuth(context, callbackUri) }
        if (result.first != null) {
            val (token, refreshToken) = result.first!!
            session.saveRefreshToken(refreshToken)
            accessToken = token
        }
        // Failures are surfaced on the sign-in screen below.
        if (result.second.isNotBlank()) oauthFailure = result.second
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
                    message = if (oauthFailure.isNotBlank()) oauthFailure else message,
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
                                SeraNativeHeader(
                                    section = tab,
                                    canCompose = authenticatedUserId.isNotBlank(),
                                    onCompose = { showComposer = true },
                                    onLanguage = { showLanguagePicker = true },
                                    onInbox = { navigateTo("Inbox") }
                                )
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
                                            }, accessToken ?: "", authenticatedUserId)
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
