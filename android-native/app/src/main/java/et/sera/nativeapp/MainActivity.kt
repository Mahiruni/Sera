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
import androidx.compose.foundation.background
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

private val teal = Color(0xFF0D7C86)
private val ink = Color(0xFF16363A)
private val bg = Color(0xFFF6FAF9)

class MainActivity : AppCompatActivity() {
    private val callback = mutableStateOf<Uri?>(null)
    private val pushKind = mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 12+ and earlier versions show the same static S mark first.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        callback.value = intent?.data?.takeIf { isSeraOAuthCallback(it) }
        pushKind.value = seraLaunchDestination(intent)
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
                if(!introVisible) SeraForegroundMessageBanner(Modifier.align(Alignment.TopCenter), onOpen={ pushKind.value=it })
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
    override fun onResume(){super.onResume();SeraMessageAlerts.setForeground(true)}
    override fun onPause(){SeraMessageAlerts.setForeground(false);super.onPause()}
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        callback.value = intent.data?.takeIf { isSeraOAuthCallback(it) }
        pushKind.value = seraLaunchDestination(intent)
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
    var restoreError by remember { mutableStateOf("") }
    var restoreAttempt by remember { mutableIntStateOf(0) }
    val permissionPrefs=remember(context){context.getSharedPreferences("sera_first_launch_permissions",Context.MODE_PRIVATE)}
    var permissionSetupDone by remember { mutableStateOf(permissionPrefs.getBoolean("completed",false)) }
    val freshInstallation=remember(context){
        runCatching{context.packageManager.getPackageInfo(context.packageName,0).let{it.firstInstallTime==it.lastUpdateTime}}.getOrDefault(false)
    }
    fun finishPermissionSetup(){
        permissionPrefs.edit().putBoolean("completed",true).apply()
        // Do not re-prompt on sign-in after the user has made a setup choice.
        context.getSharedPreferences("sera_push_permission",Context.MODE_PRIVATE).edit().putBoolean("asked",true).apply()
        permissionSetupDone=true
    }
    LaunchedEffect(restoreAttempt) {
        if(oauthCallback!=null){restoring=false;return@LaunchedEffect}
        restoring=true;restoreError=""
        val refresh=withContext(Dispatchers.IO){session.readRefreshToken()}
        if(refresh!=null){
            val result=withContext(Dispatchers.IO){authenticate("refresh_token",refresh)}
            if(result.first!=null){accessToken=result.first;result.third?.let{session.saveRefreshToken(it)}}
            else if(result.second==SERA_EXPIRED_SESSION){session.clear()}
            else restoreError=result.second
        }
        restoring=false
    }
    LaunchedEffect(restoring) {
        if (!restoring) onInitialDestinationReady()
    }
    // Request the one-time welcome email promptly after any verified native sign-in.
    // This is a separate IO coroutine so the dashboard never waits for SMTP.
    LaunchedEffect(accessToken) {
        val token = accessToken ?: return@LaunchedEffect
        delay(2000)
        withContext(Dispatchers.IO) { requestSeraWelcome(token) }
    }

    var lastUpdateCheckElapsed by remember { mutableLongStateOf(0L) }
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
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateChecked by remember { mutableStateOf(false) }
    var updateCheckSuccessful by remember { mutableStateOf(false) }
    var updateInitialized by remember { mutableStateOf(false) }
    var automaticAttempted by remember { mutableStateOf(false) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var updateForeground by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val installScope = rememberCoroutineScope()

    fun startUpdateCheck() {
        if (checkingUpdate || activeDownloadId != null) return
        checkingUpdate = true
        updateNotice = ""
        lastUpdateCheckElapsed = SystemClock.elapsedRealtime()
        installScope.launch {
            try {
                val result = checkSeraUpdateResult()
                updateChecked = true
                updateCheckSuccessful = result.successful
                val release = result.release
                if (release != null && activeDownloadId == null) {
                    val id = downloadSeraUpdate(context, release)
                    if (id != null) {
                        saveSeraPendingUpdate(context,id,release)
                        activeRelease = release
                        updateDownloaded = false
                        checkingApkIntegrity = false
                        installerPermissionRequired = false
                        installerRequested = false
                        preparingInstaller = false
                        automaticAttempted = false
                        updateProgress = 0
                        updateBytes = 0L
                        updateTotalBytes = -1L
                        activeDownloadId = id
                    } else updateNotice = "Unable to start the update. Check your connection and retry."
                } else if (!result.successful) updateNotice = "Update service unavailable. Try again later."
            } finally { checkingUpdate = false }
        }
    }

    // Restore one DownloadManager job across restarts. No duplicate download or
    // download confirmation is needed; Android still owns installation approval.
    LaunchedEffect(Unit) {
        val pending = readSeraPendingUpdate(context)
        if (pending != null) {
            activeRelease = pending.release
            automaticAttempted = seraAutomaticInstallAttempted(context,pending.id)
            activeDownloadId = pending.id
        } else startUpdateCheck()
        updateInitialized = true
    }

    fun launchVerifiedInstaller() {
        val id = activeDownloadId ?: return
        if (!updateForeground || !updateDownloaded || preparingInstaller || installerRequested) return
        if (!context.packageManager.canRequestPackageInstalls()) {
            installerPermissionRequired = true
            return
        }
        // Persist the attempt before opening Android's UI. A cancelled install is
        // never automatically re-prompted after restart; the menu offers retry.
        automaticAttempted = true
        markSeraAutomaticInstallAttempted(context,id)
        preparingInstaller = true
        installerPermissionRequired = false
        updateNotice = ""
        installScope.launch {
            val result = withContext(Dispatchers.IO) { openSeraInstaller(context, id, activeRelease?.sha256.orEmpty()) }
            preparingInstaller = false
            when (result) {
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
                    updateNotice = "Could not prepare Android installation. Tap Install update to retry."
                }
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        updateForeground = true
        consumeSeraInstallError(context)?.let { reason ->
            updateNotice = reason
            installerRequested = false
        }
        if (installerPermissionRequired && context.packageManager.canRequestPackageInstalls()) {
            installerPermissionRequired = false
        }
        val now = SystemClock.elapsedRealtime()
        if (updateInitialized && activeDownloadId == null && now - lastUpdateCheckElapsed >= 30L * 60L * 1000L) startUpdateCheck()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { updateForeground = false }
    LaunchedEffect(updateForeground,updateInitialized) {
        if (!updateForeground || !updateInitialized) return@LaunchedEffect
        while (true) {
            delay(30L * 60L * 1000L)
            if (activeDownloadId == null) startUpdateCheck()
        }
    }
    LaunchedEffect(updateForeground,updateDownloaded,activeDownloadId,automaticAttempted,restoring,installerPermissionRequired) {
        if (shouldAutomaticallyOfferSeraInstall(updateForeground,restoring,updateDownloaded,automaticAttempted)) launchVerifiedInstaller()
    }
    // A receiver result can arrive while SERA is still resumed (for example an
    // installer launch failure). Poll only while an approval is outstanding.
    LaunchedEffect(installerRequested,updateForeground) {
        if (!installerRequested || !updateForeground) return@LaunchedEffect
        while (installerRequested) {
            consumeSeraInstallError(context)?.let { reason ->
                updateNotice = reason
                installerRequested = false
            }
            delay(750)
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
                val verified = withContext(Dispatchers.IO) { verifySeraDownload(context, id, expected) }
                checkingApkIntegrity = false
                if (verified) {
                    updateDownloaded = true
                } else {
                    updateNotice = "APK security verification failed. Installation was blocked. Check for updates to retry."
                    discardSeraDownload(context, id)
                    activeDownloadId = null
                    activeRelease = null
                }
                break
            }
            if (status.failed) {
                updateNotice = "Update download failed. Check your connection and retry."
                discardSeraDownload(context, id)
                activeDownloadId = null
                activeRelease = null
                break
            }
            delay(900)
        }
    }
    val updateStatus = when {
        checkingApkIntegrity -> "Verifying update"
        preparingInstaller -> "Preparing installation"
        installerPermissionRequired -> "Installation permission required"
        installerRequested -> "Waiting for Android approval"
        updateDownloaded -> "Ready to install"
        activeDownloadId != null -> "Downloading · ${updateProgress}%"
        checkingUpdate -> "Checking for updates"
        updateNotice.isNotBlank() -> "Update needs attention"
        updateChecked && updateCheckSuccessful -> "Up to date"
        else -> "Not checked yet"
    }
    fun openUpdatePermission() {
        if(!requestSeraInstallPermission(context)) updateNotice="Allow installation in Android Settings."
    }
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
    var composerInitialKind by remember { mutableStateOf<String?>(null) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var authenticatedUserId by remember { mutableStateOf("") }
    var inboxThreadOpen by remember(authenticatedUserId) { mutableStateOf(false) }
    val globalVoice=remember(authenticatedUserId){SeraGlobalCallState()}
    val pageStateHolder=key(authenticatedUserId){androidx.compose.runtime.saveable.rememberSaveableStateHolder()}
    val feedSession=remember(accessToken,authenticatedUserId){accessToken?.takeIf{authenticatedUserId.isNotBlank()}?.let{SeraFeedSession(it,context.applicationContext,authenticatedUserId)}}
    DisposableEffect(feedSession){onDispose{feedSession?.close()}}
    val homeFeedState=key(authenticatedUserId){androidx.compose.foundation.lazy.rememberLazyListState()}
    val savedFeedState=key(authenticatedUserId){androidx.compose.foundation.lazy.rememberLazyListState()}
    var homeReselect by remember(authenticatedUserId){mutableIntStateOf(0)}
    var homePersonalized by remember(authenticatedUserId){mutableStateOf(false)}
    val tabHistory = remember { mutableStateListOf<String>() }
    fun navigateTo(destination: String) {
        if (destination == tab) {if(destination=="Home")homeReselect++;return}
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if(tab!="Inbox") tabHistory.add(tab)
        if (tabHistory.size > 12) tabHistory.removeAt(0)
        inboxThreadOpen = false
        tab = destination
    }
    val scope = rememberCoroutineScope()
    val pushPermission=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()){ granted -> if(granted) accessToken?.let{token->scope.launch{SeraPush.register(context,token,authenticatedUserId)}} }
    LaunchedEffect(accessToken,authenticatedUserId){
        val token=accessToken?:return@LaunchedEffect
        if(authenticatedUserId.isBlank())return@LaunchedEffect
        session.saveActionAccess(authenticatedUserId,token)
        if(!SeraPush.initialize(context))return@LaunchedEffect
        if(android.os.Build.VERSION.SDK_INT>=33 && androidx.core.content.ContextCompat.checkSelfPermission(context,android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
            val prefs=context.getSharedPreferences("sera_push_permission",Context.MODE_PRIVATE)
            if(!prefs.getBoolean("asked",false)){prefs.edit().putBoolean("asked",true).apply();pushPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)}
        }
        SeraPush.register(context,token,authenticatedUserId)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME){accessToken?.let{token->scope.launch{SeraPush.register(context,token,authenticatedUserId)}}}
    var openCompanyInbox by remember { mutableStateOf(false) }
    var callThread by remember(authenticatedUserId){mutableStateOf<String?>(null)}
    var callPeer by remember(authenticatedUserId){mutableStateOf("")}
    fun closeInbox(){callThread=null;callPeer="";openCompanyInbox=false;inboxThreadOpen=false;tab=if(tabHistory.isNotEmpty())tabHistory.removeAt(tabHistory.lastIndex)else "Home"}
    LaunchedEffect(pushKind,accessToken,authenticatedUserId){
        val kind=pushKind?:return@LaunchedEffect
        val token=accessToken?:return@LaunchedEffect
        if(authenticatedUserId.isBlank())return@LaunchedEffect
        if(kind=="voice_call_active"){globalVoice.restore();onPushConsumed();return@LaunchedEffect}
        if(kind.startsWith("message:")){
            val parts=kind.split(":")
            val thread=parts.getOrNull(1)
            val owner=parts.getOrNull(2)
            if(owner==authenticatedUserId && thread!=null){
                val peer=withContext(Dispatchers.IO){resolveNativeMessagePeer(token,authenticatedUserId,thread)}
                if(peer!=null){openCompanyInbox=false;callThread=thread;callPeer=peer;navigateTo("Inbox")}
            }
            onPushConsumed();return@LaunchedEffect
        }
        if(kind.startsWith("voice_call:") && !BuildConfig.LIVE_CALLING_INCLUDED){
            SeraPush.clearCallNotification(context,kind.substringAfter(':'))
            onPushConsumed()
            return@LaunchedEffect
        }
        if(kind.startsWith("voice_call:")){
            val id=kind.substringAfter(':')
            if(id.matches(Regex("[0-9a-fA-F-]{36}"))){
                val incoming=withContext(Dispatchers.IO){runCatching{chatRpc(token,"sera_call_incoming",JSONObject().put("p_id",id)).first?.let{if(it=="null")null else JSONObject(it)}}.getOrNull()}
                SeraPush.clearCallNotification(context,id)
                if(incoming!=null){
                    if(SeraNotificationAnswer.take(id,authenticatedUserId))globalVoice.queueAnswer(id)
                    openCompanyInbox=false;callThread=incoming.optString("conversation_id").takeIf{it.matches(Regex("[0-9a-fA-F-]{36}"))};callPeer=incoming.optString("caller")
                    navigateTo("Inbox")
                }else navigateTo("Inbox")
            }
        }else{openCompanyInbox=kind=="company_message";navigateTo(if(kind=="message"||kind=="company_message")"Inbox" else "Notifications")}
        onPushConsumed()
    }
    var identity by remember { mutableStateOf("") }
    var profileAvatarUrl by remember { mutableStateOf<String?>(null) }
    var profileHeadline by remember(authenticatedUserId){mutableStateOf("")}
    var profileVerified by remember(authenticatedUserId){mutableStateOf(false)}
    var editProfileRequested by remember(authenticatedUserId){mutableStateOf(false)}
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(accessToken, authenticatedUserId, refresh) {
        val token = accessToken ?: return@LaunchedEffect
        val id = authenticatedUserId.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val member = withContext(Dispatchers.IO) { loadSeraIdentity(token) }
        member.first?.let { identity = it }
        profileAvatarUrl = withContext(Dispatchers.IO) { loadSeraCurrentAvatar(token, id) }
        profileHeadline=withContext(Dispatchers.IO){loadNativeProfile(token,id).first?.headline.orEmpty()}
        profileVerified=withContext(Dispatchers.IO){id in identityBadges(token,listOf(id))}
    }
    val networkLink=rememberSeraLink(context.applicationContext)
    val unreadActivity = rememberSeraUnreadActivity(accessToken.orEmpty(),authenticatedUserId,tab,networkLink,globalVoice.pinned)
    SeraPresenceHeartbeat(accessToken.orEmpty(),authenticatedUserId,networkLink.available)
    var dataError by remember { mutableStateOf("") }
    var dataLoading by remember { mutableStateOf(false) }
    fun signOutSera(){SeraNetworkMemory.clear();SeraAvatarMemory.clear();SeraAccountSnapshot.clear();feedSession?.clearOnSignOut();runCatching{SeraMessageHistoryCache(context.applicationContext).clear(authenticatedUserId)};accessToken?.let{token->val uid=authenticatedUserId;scope.launch{SeraPush.unregister(context,token,uid)}};SeraPush.clearLocal(context);session.clear();accessToken=null;authenticatedUserId="";showComposer=false;password="";message="";tab="Home";tabHistory.clear();identity="";profileAvatarUrl=null;profileHeadline="";profileVerified=false;dataError="";dataLoading=false;editProfileRequested=false}
    LaunchedEffect(accessToken,authenticatedUserId){
        val token=accessToken?:return@LaunchedEffect
        if(authenticatedUserId.isNotBlank())return@LaunchedEffect
        var attempt=0
        while(authenticatedUserId.isBlank()){
            val result=withContext(Dispatchers.IO){loadSeraUserId(token)}
            if(result.first!=null){authenticatedUserId=result.first!!;return@LaunchedEffect}
            delay(seraReconnectDelay(attempt));attempt=(attempt+1).coerceAtMost(5)
        }
    }
    LaunchedEffect(accessToken, tab, refresh) {
        val token = accessToken ?: return@LaunchedEffect
        if (tab != "Profile") return@LaunchedEffect
        dataLoading = true
        dataError = ""
        val result = withContext(Dispatchers.IO) { loadSeraIdentity(token) }
        if (result.first != null) identity = result.first!! else dataError = result.second
        dataLoading = false
    }
    if (showComposer && accessToken != null && authenticatedUserId.isNotBlank()) SeraComposer(userId = authenticatedUserId, token = accessToken!!, initialKind=composerInitialKind, onPublished = { showComposer = false; composerInitialKind=null; refresh++ }, onDismiss = { showComposer = false; composerInitialKind=null })
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
    BackHandler(enabled = accessToken != null && !(tab=="Inbox"&&inboxThreadOpen) && (tab != "Home" || tabHistory.isNotEmpty())) { if(tab=="Inbox")closeInbox() else tab = if (tabHistory.isNotEmpty()) tabHistory.removeAt(tabHistory.lastIndex) else "Home" }
    val systemDark=androidx.compose.foundation.isSystemInDarkTheme()
    val appColors=seraColorScheme(systemDark)
    MaterialTheme(
        colorScheme = appColors,
        typography = SeraTypography,
        shapes = SeraShapes
    ) {
        SeraLinkNavigation(accessToken,authenticatedUserId,onSection={section->navigateTo(when(section){"feed"->"Home";"jobs"->"Jobs";"discover"->"Search";"connections"->"Network";"settings"->"Settings";"workspace"->"Workspace";"notifications"->"Notifications";"saved"->"Saved";"messages"->"Inbox";else->"Profile"})}){
        Surface(modifier = Modifier.fillMaxSize(), color = appColors.background) {
            Box(Modifier.fillMaxSize()) {
            if (restoring) {
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if(restoreError.isNotBlank()&&accessToken==null){
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
                    Text("Connection unavailable",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                    Text("Your saved session is safe. Reconnect to continue.",modifier=Modifier.padding(vertical=12.dp))
                    Button(onClick={restoreAttempt++}){Text("Retry")}
                    TextButton(onClick={session.clear();SeraAccountSnapshot.clear();restoreError=""}){Text("Sign in instead")}
                }
            } else if (oauthProcessing) {
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
                    Text("Sera",style=MaterialTheme.typography.headlineLarge,color=teal,fontWeight=FontWeight.Bold)
                    Spacer(Modifier.height(24.dp));CircularProgressIndicator(color=teal);Spacer(Modifier.height(16.dp));Text("Connecting securely…",style=MaterialTheme.typography.titleMedium);Text("Preparing your workspace",style=MaterialTheme.typography.bodySmall)
                }
            } else if (accessToken == null && freshInstallation && !permissionSetupDone && oauthCallback==null && pushKind==null) {
                SeraPermissionSetup(onComplete={finishPermissionSetup()},onLanguage={showLanguagePicker=true})
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
                    val standaloneThread = tab=="Inbox"
                    val baseTab = if(standaloneThread)tabHistory.lastOrNull{it!="Inbox"}?:"Home" else tab
                    val darkSystem = androidx.compose.foundation.isSystemInDarkTheme()
                    val sections = remember { listOf("Home", "Network", "Explore", "Jobs", "Inbox", "Profile") }
                    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
                    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                    val chromeVisible = remember(baseTab) { mutableStateOf(true) }
                    SideEffect {
                        (context as? android.app.Activity)?.let { activity ->
                            WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply{isAppearanceLightStatusBars=!darkSystem;isAppearanceLightNavigationBars=!darkSystem}
                        }
                    }
                    // Listen to the actual nested scroll consumption of the active page.
                    // No scroll distance is consumed here; gestures stay natural.
                    val chromeDensity=LocalDensity.current
                    val hideTravel=with(chromeDensity){28.dp.toPx()}
                    val revealTravel=with(chromeDensity){18.dp.toPx()}
                    val scrollObserver = remember(baseTab,hideTravel,revealTravel) {
                        object : NestedScrollConnection {
                            var travel = 0f
                            override fun onPostScroll(
                                consumed: Offset,
                                available: Offset,
                                source: NestedScrollSource
                            ): Offset {
                                if (baseTab != "Home" || source != NestedScrollSource.UserInput) return Offset.Zero
                                val delta = consumed.y
                                if (delta == 0f) {
                                    if (available.y > 5f) chromeVisible.value = true
                                    return Offset.Zero
                                }
                                if (travel * delta < 0f) travel = 0f
                                travel = (travel + delta).coerceIn(-hideTravel*3, hideTravel*3)
                                if (travel < -hideTravel) {chromeVisible.value=false;travel=0f}
                                if (travel > revealTravel) {chromeVisible.value=true;travel=0f}
                                return Offset.Zero
                            }
                        }
                    }
                    CompositionLocalProvider(LocalSeraGlobalCall provides globalVoice,LocalSeraLink provides networkLink){
                    Column(Modifier.fillMaxSize()){
                    if(BuildConfig.LIVE_CALLING_INCLUDED&&authenticatedUserId.isNotBlank())SeraGlobalVoiceHost(globalVoice,accessToken ?: "",authenticatedUserId)
                    Box(Modifier.weight(1f).fillMaxWidth()){
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        gesturesEnabled = !expanded && !standaloneThread,
                        drawerContent = {
                            SeraNavigationDrawer(
                                selected = tab,
                                identity = identity,
                                avatarUrl=profileAvatarUrl,
                                headline=profileHeadline,identityVerified=profileVerified,
                                onClose={scope.launch{drawerState.close()}},
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
                                updateStatus=updateStatus,
                                updateVersion=activeRelease?.versionName,
                                updateProgress=if(activeDownloadId!=null&&!updateDownloaded)updateProgress else null,
                                updateNotice=updateNotice,
                                updateChecking=checkingUpdate,
                                updateCanInstall=updateDownloaded&&!preparingInstaller&&!installerRequested,
                                updatePermission=installerPermissionRequired,
                                onCheckUpdates={startUpdateCheck()},
                                onInstallUpdate={
                                    scope.launch { drawerState.close() }
                                    if(installerPermissionRequired)openUpdatePermission() else launchVerifiedInstaller()
                                },
                                onSignOut={scope.launch{drawerState.close()};signOutSera()}
                            )
                        }
                    ) {
                        Box(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxSize()) {
                            if (expanded) NavigationRail {
                                sections.forEach { item ->
                                    NavigationRailItem(
                                        selected = baseTab == item, onClick = { navigateTo(item) },
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
                                    .padding(top = 60.dp, bottom = bottomInset + if (expanded) 0.dp else 76.dp)
                                Crossfade(
                                    targetState = baseTab,
                                    animationSpec = spring(
                                        stiffness = Spring.StiffnessMediumLow,
                                        dampingRatio = Spring.DampingRatioNoBouncy
                                    ),
                                    label = "section transition"
                                ) { currentTab ->
                                    pageStateHolder.SaveableStateProvider(currentTab) {
                                    when (currentTab) {
                                        "Home", "Saved" -> if(feedSession==null){
                                            Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
                                        }else SeraPagedFeed(
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
                                            modifier = Modifier.fillMaxSize().padding(bottom = bottomInset),
                                            onAtTop={chromeVisible.value=true},savedOnly=currentTab=="Saved",
                                            composerName=identity.lineSequence().firstOrNull().orEmpty(),composerAvatar=profileAvatarUrl,
                                            canCompose=authenticatedUserId.isNotBlank(),onCompose={kind->composerInitialKind=kind;showComposer=true},session=feedSession,retainedListState=if(currentTab=="Saved")savedFeedState else homeFeedState,homeReselect=homeReselect,activeCall=globalVoice.pinned,retainedPersonalized=if(currentTab=="Saved")false else homePersonalized,onPersonalized={if(currentTab=="Home")homePersonalized=it}
                                        )
                                        "Workspace" -> SeraNativeWorkspace(pageModifier,::navigateTo)
                                        "Settings" -> SeraContentSettings(accessToken ?: "",pageModifier){navigateTo("Profile")}
                                        "Search" -> SeraUniversalSearch(accessToken ?: "", authenticatedUserId, pageModifier)
                                        "Explore" -> SeraExplore(accessToken ?: "", authenticatedUserId, pageModifier)
                                        "Notifications" -> SeraNativeNotifications(accessToken ?: "", authenticatedUserId, pageModifier)
                                        "Jobs" -> SeraNativeJobs(accessToken ?: "", authenticatedUserId, pageModifier)
                                        "Network" -> SeraNativeNetwork(
                                            accessToken ?: "", authenticatedUserId, pageModifier
                                        )
                                        else -> Column(
                                            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                                .padding(start = 20.dp, end = 20.dp,
                                                    top = 68.dp, bottom = bottomInset + if (expanded) 18.dp else 72.dp),
                                            verticalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            SeraProfile(
                                                identity, dataLoading, dataError, { refresh++ }, ::signOutSera,
                                                accessToken ?: "", authenticatedUserId, profileAvatarUrl,editProfileRequested,{editProfileRequested=false}
                                            )
                                        }
                                    }
                                    }
                                }
                                SeraChromeOverlays(
                                    notificationCount=unreadActivity.first,
                                    inboxCount=unreadActivity.second,
                                    visible = (baseTab != "Home" || chromeVisible.value) && (!keyboardOpen || standaloneThread),
                                    expanded = expanded,
                                    selected = baseTab,
                                    sections = sections,
                                    canCompose = authenticatedUserId.isNotBlank(),
                                    profileAvatarUrl = profileAvatarUrl,
                                    profileName=identity.lineSequence().firstOrNull().orEmpty().ifBlank{"SERA member"},profileHeadline=profileHeadline,identityVerified=profileVerified,
                                    onEditProfile={editProfileRequested=true;navigateTo("Profile")},onSignOut=::signOutSera,
                                    onCompose = { showComposer = true },
                                    onLanguage = { showLanguagePicker = true },
                                    onMenu = { scope.launch { drawerState.open() } },
                                    onNavigate = ::navigateTo
                                )
                            }
                        }
                        // Keep the source destination composed in the same slot: its
                        // feed position, filters and content survive the inbox overlay.
                        if(standaloneThread) key(authenticatedUserId,callThread) {
                            SeraNativeInbox(
                                accessToken ?: "", authenticatedUserId, Modifier.fillMaxSize(),
                                openCompanyInbox, {openCompanyInbox=false},
                                initialThread=callThread,initialPeer=callPeer,initialName="SERA member",
                                onThreadExit=if(callThread!=null){{callThread=null;callPeer="";inboxThreadOpen=false}}else null,
                                onThreadVisibility={inboxThreadOpen=it},onInboxExit={closeInbox()},
                                overlayOnPage=true
                            )
                        }
                        }
                    }
                    }
                    }
                    }
                }
            }
            if (activeDownloadId != null && !installerRequested && !preparingInstaller && WindowInsets.ime.getBottom(LocalDensity.current)==0) {
                val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                SeraUpdateProgressPanel(highlights=activeRelease?.highlights.orEmpty(),version=activeRelease?.versionName.orEmpty(),progress=updateProgress,bytes=updateBytes,total=updateTotalBytes,downloaded=updateDownloaded,verifying=checkingApkIntegrity,preparing=preparingInstaller,permission=installerPermissionRequired,requested=installerRequested,onInstall={launchVerifiedInstaller()},onPermission={openUpdatePermission()},modifier=Modifier.align(Alignment.BottomCenter).padding(horizontal=12.dp).padding(bottom=bottom + if(accessToken!=null) 76.dp else 12.dp))
            }
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
    profileName:String,profileHeadline:String,identityVerified:Boolean,onEditProfile:()->Unit,onSignOut:()->Unit,
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
            showMessaging = expanded,
            onMessaging = { onNavigate("Inbox") },
            onSearch = { onNavigate("Search") },
            onNotifications = { onNavigate("Notifications") },
            onMenu = onMenu,
            avatarUrl=profileAvatarUrl,profileName=profileName,profileHeadline=profileHeadline,identityVerified=identityVerified,
            onEditProfile=onEditProfile,onSignOut=onSignOut,
            onProfile={onNavigate("Profile")},
            onSettings={onNavigate("Settings")}
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
            sections = listOf("Home", "Network", "Explore", "Jobs", "Inbox"),
            onNavigate = onNavigate,
            canCompose = canCompose,
            profileAvatarUrl = profileAvatarUrl,
            inboxCount = inboxCount
        )
    }
}

private fun signIn(email: String, password: String): Triple<String?, String, String?> = authenticate("password", password, email)

private const val SERA_EXPIRED_SESSION="Session expired. Please sign in again."
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
            val access=data.optString("access_token").takeIf{it.isNotEmpty()}
            if(access!=null)SeraAccountSnapshot.seed(access,data.optJSONObject("user"))
            Triple(access, "Signed in successfully", data.optString("refresh_token").takeIf { it.isNotEmpty() })
        } else {
            val code=runCatching{JSONObject(response).optString("error_code")}.getOrDefault("")
            val msg=if(grantType=="refresh_token"&&seraRefreshDefinitivelyExpired(code))SERA_EXPIRED_SESSION else if(connection.responseCode>=500||connection.responseCode==429)"Authentication is temporarily unavailable. Please retry." else runCatching{JSONObject(response).optString("msg")}.getOrDefault("").ifBlank{"Sign-in failed"}
            Triple(null,msg,null)
        }
    } catch (e: Exception) { Triple(null, "Connection failed. Please retry.", null) }
}


internal class SessionStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "sera_native_session",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    fun readRefreshToken(): String? = prefs.getString("refresh_token", null)
    // Notification actions use only the current short-lived access token.
    // Never rotate the refresh token from a background receiver.
    fun saveActionAccess(owner:String,token:String){prefs.edit().putString("action_owner",owner).putString("action_access",token).apply()}
    fun actionAccess(owner:String):String?=if(prefs.getString("action_owner",null)==owner)prefs.getString("action_access",null)else null
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
