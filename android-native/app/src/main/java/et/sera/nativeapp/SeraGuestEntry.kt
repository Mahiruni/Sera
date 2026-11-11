package et.sera.nativeapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text("SERA.et", fontWeight = FontWeight.ExtraBold, fontSize = 27.sp, color = entryTeal)
        OutlinedButton(
            onClick = onLanguage,
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, Color(0xFFD6E8E6)),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
        ) { Text("Language  ·  🌐", style = MaterialTheme.typography.labelMedium, color = entryInk) }
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

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFFF6FCFB), Color(0xFFF0F8F7), Color.White))
        )
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val widthLimit = if (maxWidth > 600.dp) 500.dp else maxWidth
            Column(
                modifier = Modifier.align(Alignment.TopCenter).widthIn(max = widthLimit).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
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
                    Spacer(Modifier.height(18.dp))
                    TextButton(onClick = { entryPage = "welcome" }, contentPadding = PaddingValues(0.dp)) {
                        Text("Explore SERA", color = entryTeal)
                    }
                    Text("Welcome to SERA.", fontSize = 33.sp, fontWeight = FontWeight.Bold, color = entryInk)
                    Text("Sign in to your professional network, or create an account below.", color = entryMuted)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { oauthError = beginNativeOAuth(context, "google") },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFD9E2E2))
                        ) {
                            Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold, fontSize = 21.sp)
                            Spacer(Modifier.width(12.dp))
                            Text("Continue with Google", color = entryInk, fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = { oauthError = beginNativeOAuth(context, "linkedin_oidc") },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFD9E2E2))
                        ) {
                            Surface(color = Color(0xFF0A66C2), shape = RoundedCornerShape(3.dp)) {
                                Text("in", color = Color.White, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text("Continue with LinkedIn", color = entryInk, fontWeight = FontWeight.SemiBold)
                        }
                        if (oauthError.isNotBlank()) Text(oauthError, color = MaterialTheme.colorScheme.error)
                    }

                    OutlinedTextField(
                        value = email, onValueChange = onEmailChange,
                        label = { Text("Email address") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password, onValueChange = onPasswordChange,
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            TextButton(onClick = { showPassword = !showPassword }) {
                                Text(if (showPassword) "Hide" else "Show")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = { entryPage = "recover" }, modifier = Modifier.align(Alignment.End)) {
                        Text("Forgot password?", color = entryTeal)
                    }
                    Button(
                        onClick = onSignIn,
                        enabled = !busy && email.contains("@") && password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = entryTeal),
                        shape = RoundedCornerShape(17.dp)
                    ) {
                        if (busy) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
                        else Text("Sign in  →", fontWeight = FontWeight.Bold)
                    }
                    if (message.isNotEmpty()) {
                        Text(
                            message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text("Check your password and email verification, or use password recovery.", color = entryMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider(color = Color(0xFFDCEAE9))
                    Text("New here?", color = entryMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = { entryPage = "register" }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text("Create a SERA account", color = entryTeal, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { openSeraSite(context, "sign-in") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Google and LinkedIn native sign-in is being prepared", color = entryMuted)
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text("SERA.et  •  Your profile. Your work. Your future.",
                    style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                    color = entryMuted, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
