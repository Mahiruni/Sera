package et.sera.nativeapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val entryTeal = Color(0xFF087E83)
private val entryInk = Color(0xFF15383D)
private val entryMuted = Color(0xFF577175)
private val entryMint = Color(0xFFDFF6F1)
private val entryDark = Color(0xFF123A40)
private const val SERA_PRODUCTION_ORIGIN = "https://sera.hisabtech.com"

/**
 * The SERA website hosts legal information and social OAuth.
 * Account registration is handled by the native Supabase Auth flow.
 */
private fun openSeraSite(context: Context, path: String) {
    val normalized = path.trimStart('/')
    val uri = Uri.parse("$SERA_PRODUCTION_ORIGIN/$normalized")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
    } catch (_: Exception) {
        Toast.makeText(context, "No browser found. Visit sera.hisabtech.com", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun GuestBrand(onLanguage: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 42.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("SERA", fontWeight = FontWeight.ExtraBold, fontSize = 25.sp, letterSpacing = (-0.7).sp, color = entryTeal)
            Text(".et", fontWeight = FontWeight.Bold, fontSize = 25.sp, letterSpacing = (-0.7).sp, color = entryInk)
        }
        OutlinedButton(
            onClick = onLanguage,
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, Color(0xFFCFE5E2)),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 1.dp)
        ) {
            Icon(Icons.Outlined.Language, contentDescription = null, tint = entryTeal, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text("Language", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = entryInk)
        }
    }
}

@Composable
private fun GuestFeature(symbol: String, heading: String, detail: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = 0.84f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0xFFE0EDEC))
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(13.dp), color = entryMint) {
                Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                    Text(symbol, color = entryTeal, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(heading, fontWeight = FontWeight.Bold, color = entryInk, fontSize = 14.sp)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = entryMuted)
            }
        }
    }
}


/** Concept 04: social-first native sign-in. All existing auth callbacks remain unchanged. */
@Composable
private fun SeraSocialFirstSignIn(
    email: String,
    password: String,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    busy: Boolean,
    message: String,
    oauthError: String,
    onOAuthError: (String) -> Unit,
    onSignIn: () -> Unit,
    onRecover: () -> Unit,
    onRegister: () -> Unit,
    showPassword: Boolean,
    onTogglePassword: () -> Unit
) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf(nativeOAuthPending(context)) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { pending = nativeOAuthPending(context) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, Color(0xFFDEEAE8)),
        shadowElevation = 2.dp
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                "Welcome back",
                fontSize = 21.sp, lineHeight = 25.sp, letterSpacing = (-0.6).sp,
                fontWeight = FontWeight.ExtraBold, color = entryInk
            )
            Text(
                "Sign in to SERA.",
                fontSize = 12.sp, color = entryMuted
            )
            OutlinedButton(
                onClick = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove); val error=beginNativeOAuth(context, "google"); onOAuthError(error);pending=error.isBlank() },
                enabled = !busy && !pending,
                modifier = Modifier.fillMaxWidth().height(49.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFFD4E1E1)),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Text("G", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4285F4))
                Spacer(Modifier.width(11.dp))
                Text("Continue with Google", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = entryInk)
            }
            Button(
                onClick = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove); val error=beginNativeOAuth(context, "linkedin_oidc"); onOAuthError(error);pending=error.isBlank() },
                enabled = !busy && !pending,
                modifier = Modifier.fillMaxWidth().height(49.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A66C2)),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Surface(shape = RoundedCornerShape(3.dp), color = Color.White) {
                    Text(
                        "in", modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0A66C2)
                    )
                }
                Spacer(Modifier.width(11.dp))
                Text("Continue with LinkedIn", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
            if (pending) { Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp);Text("Complete secure sign-in",fontSize=12.sp,color=entryMuted);TextButton(onClick={cancelNativeOAuth(context);pending=false;onOAuthError("")}){Text("Cancel")}} }
            if (oauthError.isNotBlank()) Text(oauthError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)

            Row(
                Modifier.padding(vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                HorizontalDivider(Modifier.weight(1f), color = Color(0xFFDDE9E8))
                Text("or sign in with email", fontSize = 11.sp, color = entryMuted)
                HorizontalDivider(Modifier.weight(1f), color = Color(0xFFDDE9E8))
            }
            OutlinedTextField(
                value = email, onValueChange = onEmailChange, singleLine = true,
                placeholder = { Text("Email address", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null, tint = entryMuted, modifier = Modifier.size(19.dp)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = entryTeal, unfocusedBorderColor = Color(0xFFD1DEDD),
                    focusedContainerColor = Color.White, unfocusedContainerColor = Color.White
                )
            )
            OutlinedTextField(
                value = password, onValueChange = onPasswordChange, singleLine = true,
                placeholder = { Text("Password", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null, tint = entryMuted, modifier = Modifier.size(19.dp)) },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = onTogglePassword) {
                        Icon(if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (showPassword) "Hide password" else "Show password",
                            tint = entryTeal, modifier = Modifier.size(19.dp))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = entryTeal, unfocusedBorderColor = Color(0xFFD1DEDD),
                    focusedContainerColor = Color.White, unfocusedContainerColor = Color.White
                )
            )
            TextButton(
                onClick = onRecover, modifier = Modifier.align(Alignment.End).height(30.dp),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
            ) {
                Text("Forgot password?", fontSize = 12.sp, color = entryTeal, fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = onSignIn,
                enabled = !busy && !pending && email.contains("@") && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF006B6A),
                    disabledContainerColor = Color(0xFFD9E8E6),
                    disabledContentColor = Color(0xFF698481)
                ),
                shape = RoundedCornerShape(15.dp)
            ) {
                if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                else {
                    Text("Sign in", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("New to SERA? ", fontSize = 12.sp, color = entryMuted)
                TextButton(onClick = onRegister, contentPadding = PaddingValues(horizontal = 2.dp)) {
                    Text("Create an account", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = entryTeal)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = { openSeraSite(context, "privacy") }) { Text("Privacy", fontSize = 12.sp, color = entryTeal) }
                TextButton(onClick = { openSeraSite(context, "terms") }) { Text("Terms", fontSize = 12.sp, color = entryTeal) }
            }
        }
    }
}

@Composable
fun SeraGuestEntry(
    email: String,
    password: String,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    busy: Boolean,
    message: String,
    onSignIn: () -> Unit,
    onLanguage: () -> Unit,
    onAuthenticated: (String, String) -> Unit
) {
    val context = LocalContext.current
    var oauthError by remember { mutableStateOf("") }
    var entryPage by rememberSaveable { mutableStateOf("signin") }
    var showPassword by remember { mutableStateOf(false) }
    val guestScroll = rememberScrollState()
    LaunchedEffect(entryPage) { guestScroll.scrollTo(0) }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFFF6FCFB), Color(0xFFEAF7F4), Color.White))
        ).windowInsetsPadding(WindowInsets.safeDrawing).imePadding()
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val widthLimit = minOf(maxWidth, 500.dp)
            Column(
                modifier = Modifier.align(if (entryPage == "signin" || entryPage == "register") Alignment.Center else Alignment.TopCenter).widthIn(max = widthLimit).fillMaxWidth()
                    .verticalScroll(guestScroll).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GuestBrand(onLanguage)
                if (entryPage == "welcome") {
                    Spacer(Modifier.height(9.dp))
                    Surface(color = entryMint, shape = RoundedCornerShape(50)) {
                        Text(
                            "A PLACE FOR YOUR NEXT CHAPTER",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = entryTeal, letterSpacing = 1.sp
                        )
                    }
                    Text(
                        "Your work.\nYour people.\nYour next move.",
                        fontSize = 37.sp, lineHeight = 43.sp, fontWeight = FontWeight.Bold, color = entryInk
                    )
                    Text(
                        "A professional space to share what you create, discover opportunities and make meaningful connections.",
                        style = MaterialTheme.typography.bodyLarge, color = entryMuted
                    )
                    Surface(color = entryDark, shape = RoundedCornerShape(25.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(shape = CircleShape, color = Color(0xFF75E2CE)) { Spacer(Modifier.size(8.dp)) }
                                Text("YOUR PROFESSIONAL WORLD", fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Color(0xFFACE7DB), letterSpacing = 1.sp)
                            }
                            Text("Ideas worth sharing.\nWork worth seeing.", fontSize = 23.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Build your identity • Grow your network • Find your path", fontSize = 12.sp, color = Color(0xFFDBEFEC))
                        }
                    }
                    GuestFeature("◎", "Connect with professionals", "Build meaningful relationships around your skills.")
                    GuestFeature("▣", "Explore jobs and projects", "Discover career paths, opportunities and talent.")
                    GuestFeature("✦", "Showcase your work", "Share expertise, ideas and portfolios.")
                    Button(
                        onClick = { entryPage = "register" },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = entryTeal),
                        shape = RoundedCornerShape(17.dp)
                    ) { Text("Create your account  →", fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = { entryPage = "signin" },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        border = BorderStroke(1.dp, entryTeal),
                        shape = RoundedCornerShape(17.dp)
                    ) { Text("Sign in to SERA", fontWeight = FontWeight.SemiBold, color = entryTeal) }
                    Text("Create your SERA profile without leaving the Android app.", style = MaterialTheme.typography.bodySmall, color = entryMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { entryPage = "signin" }, modifier = Modifier.fillMaxWidth()) {
                        Text("Sign in with email to continue in the Android app", color = entryTeal, textAlign = TextAlign.Center)
                    }
                    TextButton(onClick = { openSeraSite(context, "") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Explore SERA before joining", color = entryInk)
                    }
                } else if (entryPage == "recover") {
                    SeraNativePasswordRecovery(onBack = { entryPage = "signin" })
                } else if (entryPage == "register") {
                    SeraNativeRegistrationFlow(
                        onBack = { entryPage = "welcome" },
                        onSignIn = { registeredEmail ->
                            onEmailChange(registeredEmail)
                            onPasswordChange("")
                            entryPage = "signin"
                        },
                        onAuthenticated = onAuthenticated
                    )
                } else {
                    SeraSocialFirstSignIn(
                        email = email, password = password, onEmailChange = onEmailChange,
                        onPasswordChange = onPasswordChange, busy = busy, message = message,
                        oauthError = oauthError, onOAuthError = { oauthError = it },
                        onSignIn = onSignIn,
                        onRecover = { entryPage = "recover" },
                        onRegister = { entryPage = "register" },
                        showPassword = showPassword,
                        onTogglePassword = { showPassword = !showPassword }
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text("SERA.et  •  Your profile. Your work. Your future.",
                    style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                    color = entryMuted, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
