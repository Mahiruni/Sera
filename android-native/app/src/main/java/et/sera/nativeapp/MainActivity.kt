package et.sera.nativeapp

import android.os.Bundle
import android.os.SystemClock
import android.content.Intent
import android.net.Uri
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.platform.LocalDensity
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
    private val pushKind = mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 12+ and earlier versions show the same static S mark first.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        callback.value = intent?.data?.takeIf { isSeraOAuthCallback(it) }
        pushKind.value = intent?.getStringExtra("sera_kind") ?: intent?.getStringExtra("kind")
        SeraPush.channel(this)
        SeraPush.initialize(this)
        // A warm start, configuration change, process restoration or OAuth
        // deep link must never replay the cinematic introduction.
        val playIntro = savedInstanceState == null &&
            intent?.action == Intent.ACTION_MAIN && callback.value == null
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !playIntro
            isAppearanceLightNavigationBars = !playIntro
        }
        setContent {
            var introVisible by remember { mutableStateOf(playIntro) }
            var destinationReady by remember { mutableStateOf(false) }
            Box(Modifier.fillMaxSize()) {
                SeraApp(
                    context = this@MainActivity,
                    oauthCallback = callback.value,
                    onCallbackConsumed = { callback.value = null; setIntent(Intent(this@MainActivity, MainActivity::class.java).setAction(Intent.ACTION_MAIN)) },
                    onInitialDestinationReady = { destinationReady = true },
                    pushKind = pushKind.value, onPushConsumed = { pushKind.value = null }
                )
                if (introVisible) SeraSignatureIntro(
                    destinationReady = destinationReady,
                    onWhiteFrame = {
                        WindowCompat.getInsetsController(window, window.decorView).apply {
                            isAppearanceLightStatusBars = true
                            isAppearanceLightNavigationBars = true
                        }
                    },
                    onComplete = { introVisible = false }
                )
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        callback.value = intent.data?.takeIf { isSeraOAuthCallback(it) }
        pushKind.value = intent.getStringExtra("sera_kind") ?: intent.getStringExtra("kind")
    }
}

@Composable
fun SeraApp(
    context: Context,
    oauthCallback: Uri? = null,
    onCallbackConsumed: () -> Unit = {},
    onInitialDestinationReady: () -> Unit = {},
    pushKind:String?=null, onPushConsumed:()->Unit={}
) {
    val session = remember { SessionStore(context) }
    val haptics = LocalHapticFeedback.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var accessToken by remember { mutableStateOf<String?>(null) }
    var restoring by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        if (oauthCallback != null) { restoring = false; return@LaunchedEffect }
        val refresh = session.readRefreshToken()
        if (refresh != null) {
            val result = withContext(Dispatchers.IO) { authenticate("refresh_token", refresh) }
            if (result.first != null) { accessToken = result.first; result.third?.let { session.saveRefreshToken(it) } }
            else session.clear()
        }
        restoring = false
    }
    LaunchedEffect(restoring) {
        if (!restoring) onInitialDestinationReady()
    }
    // Request the one-time welcome email promptly after any verified native sign-in.
    // This is a separate IO coroutine so the dashboard never waits for SMTP.
    LaunchedEffect(accessToken) {
        val token = accessToken ?: return@LaunchedEffect
        withContext(Dispatchers.IO) { requestSeraWelcome(token) }
    }

    var availableUpdate by remember { mutableStateOf<SeraRelease?>(null) }
    var lastUpdateCheckElapsed by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
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
    var preparingInstaller by remember { mutableStateOf(false) }
    val installScope = rememberCoroutineScope()

    // A user-initiated update survives activity recreation, keyboard changes,
    // backgrounding and process restarts. Never re-enqueue the same download.
    LaunchedEffect(Unit) {
        val pending = readSeraPendingUpdate(context)
        if (pending != null) {
            activeRelease = pending.release
            activeDownloadId = pending.id
        } else {
            lastUpdateCheckElapsed = SystemClock.elapsedRealtime()
            availableUpdate = checkSeraUpdate()
        }
    }

    fun launchVerifiedInstaller() {
        val id = activeDownloadId ?: return
        if (!updateDownloaded || preparingInstaller || installerRequested) return
        preparingInstaller = true
        installScope.launch {
            val result = withContext(Dispatchers.IO) {
                openSeraInstaller(context, id)
            }
            preparingInstaller = false
            when (result) {
                SeraInstallResult.LAUNCHED -> {
                    // OS session is committed, but not installed yet. The
                    // private receiver launches Android's user approval.
                    installerRequested = true
                    installerPermissionRequired = false
                }
                SeraInstallResult.PERMISSION_REQUIRED -> {
                    installerPermissionRequired = true
                    installerRequested = false
                }
                SeraInstallResult.FAILED -> {
                    installerRequested = false
                    updateNotice = "Could not prepare the Android installer. Your verified APK is saved; tap Install update to retry."
                }
            }
        }
    }

    // The user may have returned from Android's per-app install permission page.
    // Resume only the already downloaded and verified update.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        consumeSeraInstallError(context)?.let { reason ->
            updateNotice = reason
            installerRequested = false
        }
        if (installerPermissionRequired && updateDownloaded && activeDownloadId != null &&
            context.packageManager.canRequestPackageInstalls()) {
            launchVerifiedInstaller()
        }
        // Detect new signed releases when returning to SERA after a long background
        // session. Avoid repeated requests when the installer or a download is active.
        val now = SystemClock.elapsedRealtime()
        if (activeDownloadId == null && now - lastUpdateCheckElapsed >= 30L * 60L * 1000L) {
            lastUpdateCheckElapsed = now
            installScope.launch {
                checkSeraUpdate()?.let { availableUpdate = it }
            }
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
                    preparingInstaller = false
                    updateProgress = 0
                    updateBytes = 0L
                    updateTotalBytes = -1L
                    activeDownloadId = id
                } else updateNotice = "Unable to start the download. Please try again."
                availableUpdate = null
            }
        )
    }

    if (updateNotice.isNotBlank()) AlertDialog(
        onDismissRequest = { updateNotice = "" },
        title = { Text("SERA update") },
        text = { Text(updateNotice) },
        confirmButton = {
            TextButton(onClick = { updateNotice = "" }) { Text("OK") }
        }
    )
    var oauthFailure by remember { mutableStateOf("") }
    var oauthProcessing by remember { mutableStateOf(false) }
    LaunchedEffect(oauthCallback) {
        val callbackUri = oauthCallback ?: return@LaunchedEffect
        if (oauthProcessing) return@LaunchedEffect
        oauthProcessing = true
        oauthFailure = ""
        val result = withContext(Dispatchers.IO) { finishNativeOAuth(context, callbackUri) }
        if (result.first != null) {
            val (token, refreshToken) = result.first!!
            session.saveRefreshToken(refreshToken)
            accessToken = token
        }
        // Failures are surfaced on the sign-in screen below.
        if (result.second.isNotBlank()) oauthFailure = result.second
        oauthProcessing = false
        onCallbackConsumed()
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
    val pushPermission=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()){ granted -> if(granted) accessToken?.let{token->scope.launch{SeraPush.register(context,token,authenticatedUserId)}} }
    LaunchedEffect(accessToken,authenticatedUserId){
        val token=accessToken?:return@LaunchedEffect
        if(authenticatedUserId.isBlank()||!SeraPush.initialize(context))return@LaunchedEffect
        if(android.os.Build.VERSION.SDK_INT>=33 && androidx.core.content.ContextCompat.checkSelfPermission(context,android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
            val prefs=context.getSharedPreferences("sera_push_permission",Context.MODE_PRIVATE)
            if(!prefs.getBoolean("asked",false)){prefs.edit().putBoolean("asked",true).apply();pushPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)}
        }else SeraPush.register(context,token,authenticatedUserId)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME){accessToken?.let{token->scope.launch{SeraPush.register(context,token,authenticatedUserId)}}}
    var openCompanyInbox by remember { mutableStateOf(false) }
    LaunchedEffect(pushKind,accessToken){if(pushKind!=null&&accessToken!=null){openCompanyInbox=pushKind=="company_message";navigateTo(if(pushKind=="message"||pushKind=="company_message")"Inbox" else "Notifications");onPushConsumed()}}
    var feed by remember { mutableStateOf<List<SeraPost>>(emptyList()) }
    var identity by remember { mutableStateOf("") }
    var profileAvatarUrl by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(accessToken, authenticatedUserId, refresh) {
        val token = accessToken ?: return@LaunchedEffect
        val id = authenticatedUserId.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val member = withContext(Dispatchers.IO) { loadSeraIdentity(token) }
        member.first?.let { identity = it }
        profileAvatarUrl = withContext(Dispatchers.IO) { loadSeraCurrentAvatar(token, id) }
    }
    val unreadActivity = rememberSeraUnreadActivity(accessToken.orEmpty(),authenticatedUserId,tab)
    SeraPresenceHeartbeat(accessToken.orEmpty(),authenticatedUserId)
    var dataError by remember { mutableStateOf("") }
    var dataLoading by remember { mutableStateOf(false) }
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
        val locales = listOf("en" to "English", "am" to "አማርኛ", "om" to "Afaan Oromo")
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
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = teal, onPrimary = Color.White,
            secondary = Color(0xFF126F70),
            background = bg, surface = Color.White,
            onSurface = ink, onSurfaceVariant = Color(0xFF607B7D),
            surfaceVariant = Color(0xFFF0F7F6),
            outline = Color(0xFFC6DCDA)
        ),
        typography = SeraTypography,
        shapes = SeraShapes
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = bg) {
            Box(Modifier.fillMaxSize()) {
            if (restoring) {
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (oauthProcessing) {
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
                    Text("SERA.et",style=MaterialTheme.typography.headlineLarge,color=teal,fontWeight=FontWeight.Bold)
                    Spacer(Modifier.height(24.dp));CircularProgressIndicator(color=teal);Spacer(Modifier.height(16.dp));Text("Connecting securely…",style=MaterialTheme.typography.titleMedium);Text("Preparing your workspace",style=MaterialTheme.typography.bodySmall)
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
                BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
                    val expanded = maxWidth >= 700.dp
                    val sections = remember { listOf("Home", "Network", "Jobs", "Inbox", "Profile") }
                    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
                    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                    val chromeVisible = remember(tab) { mutableStateOf(true) }
                    SideEffect {
                        (context as? android.app.Activity)?.let { activity ->
                            WindowCompat.getInsetsController(activity.window, activity.window.decorView).isAppearanceLightNavigationBars = expanded || !chromeVisible.value || keyboardOpen
                        }
                    }
                    // Listen to the actual nested scroll consumption of the active page.
                    // No scroll distance is consumed here; gestures stay natural.
                    val scrollObserver = remember(tab) {
                        object : NestedScrollConnection {
                            var travel = 0f
                            override fun onPostScroll(
                                consumed: Offset,
                                available: Offset,
                                source: NestedScrollSource
                            ): Offset {
                                if (source != NestedScrollSource.UserInput) return Offset.Zero
                                val delta = consumed.y
                                if (delta == 0f) {
                                    if (available.y > 5f) chromeVisible.value = true
                                    return Offset.Zero
                                }
                                if (travel * delta < 0f) travel = 0f
                                travel = (travel + delta).coerceIn(-90f, 90f)
                                if (travel < -25f) chromeVisible.value = false
                                if (travel > 18f) chromeVisible.value = true
                                return Offset.Zero
                            }
                        }
                    }
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        gesturesEnabled = !expanded,
                        drawerContent = {
                            SeraNavigationDrawer(
                                selected = tab,
                                identity = identity,
                                onNavigate = { destination ->
                                    navigateTo(destination)
                                    scope.launch { drawerState.close() }
                                },
                                onCompose = {
                                    scope.launch { drawerState.close() }
                                    if (authenticatedUserId.isNotBlank()) showComposer = true
                                },
                                onLanguage = {
                                    scope.launch { drawerState.close() }
                                    showLanguagePicker = true
                                },
                                onCheckUpdates = {
                                    scope.launch { drawerState.close() }
                                    if (activeDownloadId != null) {
                                        updateNotice = "Your current SERA update is already downloading or ready to install."
                                    } else {
                                        installScope.launch {
                                            lastUpdateCheckElapsed = SystemClock.elapsedRealtime()
                                            val release = checkSeraUpdate()
                                            if (release != null) availableUpdate = release
                                            else updateNotice = "No newer signed SERA version was found, or the update service is temporarily unavailable."
                                        }
                                    }
                                },
                                onSignOut = {
                                    scope.launch { drawerState.close() }
                                    accessToken?.let { token -> val uid=authenticatedUserId; scope.launch { SeraPush.unregister(context,token,uid) } }
                                    SeraPush.clearLocal(context)
                                    session.clear()
                                    accessToken = null
                                    authenticatedUserId = ""
                                    showComposer = false
                                    password = ""
                                    message = ""
                                    tab = "Home"
                                    tabHistory.clear()
                                }
                            )
                        }
                    ) {
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
                            // Chrome is an overlay, not a Scaffold bottomBar. This
                            // removes the opaque rectangle behind the floating dock.
                            Box(
                                Modifier.weight(1f).fillMaxHeight()
                                    .nestedScroll(scrollObserver)
                            ) {
                                val pageModifier = Modifier.fillMaxSize()
                                    .padding(top = 60.dp, bottom = bottomInset + if (expanded) 0.dp else 60.dp)
                                Crossfade(
                                    targetState = tab,
                                    animationSpec = spring(
                                        stiffness = Spring.StiffnessMediumLow,
                                        dampingRatio = Spring.DampingRatioNoBouncy
                                    ),
                                    label = "section transition"
                                ) { currentTab ->
                                    when (currentTab) {
                                        "Home" -> SeraPagedFeed(
                                            accessToken ?: "", refresh,
                                            onLike = { post ->
                                                val token = accessToken
                                                if (token != null) scope.launch {
                                                    val result = withContext(Dispatchers.IO) {
                                                        setSeraLike(token, post.id, post.liked)
                                                    }
                                                    if (!result.first) dataError = result.second
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize().padding(bottom = bottomInset)
                                        )
                                        "Search" -> SeraNativeNetwork(accessToken ?: "", authenticatedUserId, pageModifier, initialSearch=true)
                                        "Notifications" -> SeraNativeNotifications(accessToken ?: "", authenticatedUserId, pageModifier)
                                        "Jobs" -> SeraNativeJobs(accessToken ?: "", authenticatedUserId, pageModifier)
                                        "Network" -> SeraNativeNetwork(
                                            accessToken ?: "", authenticatedUserId, pageModifier
                                        )
                                        "Inbox" -> SeraNativeInbox(
                                            accessToken ?: "", authenticatedUserId, pageModifier, openCompanyInbox, { openCompanyInbox=false }
                                        )
                                        else -> Column(
                                            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                                .padding(start = 20.dp, end = 20.dp,
                                                    top = 68.dp, bottom = bottomInset + if (expanded) 18.dp else 72.dp),
                                            verticalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            SeraProfile(
                                                identity, dataLoading, dataError, { refresh++ }, {
                                                    accessToken?.let { token -> val uid=authenticatedUserId; scope.launch { SeraPush.unregister(context,token,uid) } }
                                                    SeraPush.clearLocal(context)
                                                    session.clear()
                                                    accessToken = null
                                                    authenticatedUserId = ""
                                                    showComposer = false
                                                    password = ""
                                                    message = ""
                                                    tab = "Home"
                                                    tabHistory.clear()
                                                },
                                                accessToken ?: "", authenticatedUserId, profileAvatarUrl
                                            )
                                        }
                                    }
                                }
                                SeraChromeOverlays(
                                    notificationCount=unreadActivity.first,
                                    inboxCount=unreadActivity.second,
                                    visible = chromeVisible.value && !keyboardOpen,
                                    expanded = expanded,
                                    selected = tab,
                                    sections = sections,
                                    canCompose = authenticatedUserId.isNotBlank(),
                                    profileAvatarUrl = profileAvatarUrl,
                                    onCompose = { showComposer = true },
                                    onLanguage = { showLanguagePicker = true },
                                    onMenu = { scope.launch { drawerState.open() } },
                                    onNavigate = ::navigateTo
                                )
                            }
                        }
                    }
                }
            }
            if (activeDownloadId != null && !installerRequested && !preparingInstaller) {
                val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                SeraUpdateProgressPanel(highlights=activeRelease?.highlights.orEmpty(),version=activeRelease?.versionName.orEmpty(),progress=updateProgress,bytes=updateBytes,total=updateTotalBytes,downloaded=updateDownloaded,verifying=checkingApkIntegrity,preparing=preparingInstaller,permission=installerPermissionRequired,requested=installerRequested,onInstall={launchVerifiedInstaller()},onPermission={if(!requestSeraInstallPermission(context)) updateNotice="Allow installation in Android Settings."},modifier=Modifier.align(Alignment.BottomCenter).padding(horizontal=12.dp).padding(bottom=bottom + if(accessToken!=null) (60.dp + (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * 0.25f)) else 12.dp))
            }
            }
        }
    }
}


/** Isolated BoxScope keeps the top/bottom chrome outside the RowScope overload. */
@Composable
private fun BoxScope.SeraChromeOverlays(
    notificationCount:Int,
    inboxCount:Int,
    visible: Boolean,
    expanded: Boolean,
    selected: String,
    sections: List<String>,
    canCompose: Boolean,
    profileAvatarUrl: String?,
    onCompose: () -> Unit,
    onLanguage: () -> Unit,
    onMenu: () -> Unit,
    onNavigate: (String) -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
        modifier = Modifier.align(Alignment.TopCenter)
    ) {
        SeraNativeHeader(
            notificationCount=notificationCount,
            inboxCount=inboxCount,
            section = selected,
            canCompose = canCompose,
            onCompose = onCompose,
            showCreate = expanded,
            onMessaging = { onNavigate("Inbox") },
            onSearch = { onNavigate("Search") },
            onNotifications = { onNavigate("Notifications") },
            onMenu = onMenu
        )
    }
    if (!expanded) AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        SeraFloatingDock(
            selected = selected,
            sections = listOf("Home", "Network", "Create", "Jobs", "Profile"),
            onNavigate = { if (it == "Create") onCompose() else onNavigate(it) },
            canCompose = canCompose,
            profileAvatarUrl = profileAvatarUrl
        )
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
