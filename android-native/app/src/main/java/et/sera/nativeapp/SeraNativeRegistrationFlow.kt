package et.sera.nativeapp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val authTeal = Color(0xFF087E83)
private val authInk = Color(0xFF15383D)
private val authGray = Color(0xFF64787B)
private val nativeLocales = listOf(
    "en" to "English",
    "am" to "አማርኛ",
    "om" to "Afaan Oromoo",
    "ti" to "ትግርኛ",
    "so" to "Soomaali",
    "aa" to "Qafar af",
    "ar" to "العربية"
)

/**
 * All registration fields and requests remain in Android.
 * External confirmation is controlled by Supabase Auth's configured email template;
 * never suggest a message was delivered just because the request was accepted.
 */
@Composable
fun SeraNativeRegistrationFlow(
    onBack: () -> Unit,
    onSignIn: (String) -> Unit,
    onAuthenticated: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var journey by remember { mutableStateOf("candidate") }
    var locale by remember { mutableStateOf("en") }
    var termsAccepted by remember { mutableStateOf(false) }
    var emailPending by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var resendMessage by remember { mutableStateOf("") }
    var resendBusy by remember { mutableStateOf(false) }
    var lastResendAt by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()

    BackHandler { if (emailPending) emailPending = false else onBack() }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TextButton(onClick = { if (emailPending) emailPending = false else onBack() },
            contentPadding = PaddingValues(0.dp)) { Text("←  Back", color = authTeal) }
        if (emailPending) {
            Text("Check your inbox", fontSize = 31.sp, fontWeight = FontWeight.Bold, color = authInk)
            Text("We received your account request for:", color = authGray)
            Text(email.trim(), fontWeight = FontWeight.SemiBold, color = authInk)
            Surface(
                color = Color(0xFFE2F6F0),
                shape = RoundedCornerShape(19.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("✓   Registration request received", color = authTeal, fontWeight = FontWeight.Bold)
                    Text(
                        "Open your confirmation email, follow the verification link, then return to the SERA app to sign in. You can check your Spam or Promotions folder too.",
                        color = authInk,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Email delivery is handled by the configured mail provider; a successful request does not guarantee immediate delivery.",
                        color = authGray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (resendMessage.isNotEmpty()) {
                Text(resendMessage, color = if (resendMessage.startsWith("Too many") || resendMessage.startsWith("Connection")) MaterialTheme.colorScheme.error else authTeal)
            }
            OutlinedButton(
                onClick = {
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - lastResendAt < 60_000L && lastResendAt != 0L) {
                        resendMessage = "A recent request was accepted. Check your inbox before requesting again."
                    } else {
                        resendBusy = true
                        resendMessage = ""
                        scope.launch {
                            val response = withContext(Dispatchers.IO) { resendSeraConfirmation(email) }
                            resendMessage = response.message
                            if (response.success) lastResendAt = android.os.SystemClock.elapsedRealtime()
                            resendBusy = false
                        }
                    }
                },
                enabled = !resendBusy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                border = BorderStroke(1.dp, authTeal)
            ) {
                if (resendBusy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Resend confirmation email", color = authTeal)
            }
            Button(
                onClick = { onSignIn(email.trim()) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = authTeal),
                shape = RoundedCornerShape(16.dp)
            ) { Text("I've verified my email — Sign in") }
        } else {
            Text("Create your account", fontSize = 31.sp, lineHeight = 37.sp, color = authInk, fontWeight = FontWeight.Bold)
            Text("Join the professional SERA community — right here in the app.", color = authGray)
            OutlinedTextField(
                value = name, onValueChange = { if (it.length <= 100) name = it },
                label = { Text("Full name") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = email, onValueChange = { if (it.length <= 254) email = it },
                label = { Text("Email address") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password, onValueChange = { if (it.length <= 128) password = it },
                label = { Text("Password") },
                supportingText = { Text("Minimum 12 characters, use a unique password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = confirm, onValueChange = { if (it.length <= 128) confirm = it },
                label = { Text("Confirm password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Text("I am joining as", color = authInk, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = journey == "candidate",
                    onClick = { journey = "candidate" },
                    label = { Text("Professional") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = journey == "employer",
                    onClick = { journey = "employer" },
                    label = { Text("Employer") },
                    modifier = Modifier.weight(1f)
                )
            }
            Text("Preferred language", color = authInk, fontWeight = FontWeight.SemiBold)
            var languageMenu by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { languageMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(nativeLocales.first { it.first == locale }.second + "  ▾", color = authInk)
                }
                DropdownMenu(expanded = languageMenu, onDismissRequest = { languageMenu = false }) {
                    nativeLocales.forEach { (code, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { locale = code; languageMenu = false }
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = termsAccepted, onCheckedChange = { termsAccepted = it })
                Text("I agree to SERA's Terms and have read the Privacy Notice.",
                    style = MaterialTheme.typography.bodySmall, color = authInk)
            }
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            Button(
                onClick = {
                    error = when {
                        name.trim().length !in 2..100 -> "Enter your full name."
                        email.trim().length < 5 || !email.contains("@") -> "Enter a valid email address."
                        password.length !in 12..128 -> "Password must contain 12–128 characters."
                        password != confirm -> "The passwords do not match."
                        !termsAccepted -> "Please accept the account terms before continuing."
                        else -> ""
                    }
                    if (error.isEmpty() && !busy) {
                        busy = true
                        scope.launch {
                            val response = withContext(Dispatchers.IO) {
                                registerSeraAccount(name, email, password, locale, journey)
                            }
                            busy = false
                            if (!response.success) error = response.message
                            else {
                                password = ""
                                confirm = ""
                                val token = response.accessToken
                                val refresh = response.refreshToken
                                if (token != null && refresh != null) onAuthenticated(token, refresh)
                                else emailPending = true
                            }
                        }
                    }
                },
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = authTeal),
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(21.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Text("Creating account…")
                } else Text("Create account  →", fontWeight = FontWeight.Bold)
            }
            Text(
                "Your account is created securely through SERA's authentication service. Verification may be required before sign-in.",
                style = MaterialTheme.typography.bodySmall,
                color = authGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(onClick = { onSignIn(email.trim()) }, modifier = Modifier.fillMaxWidth()) {
                Text("Already have an account? Sign in", color = authTeal)
            }
        }
    }
}
