package et.sera.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val profileInk = Color(0xFF173A3F)
private val profileTeal = Color(0xFF087E83)
private val profileMuted = Color(0xFF607478)
private val profileLine = Color(0xFFDCEAE8)

@Composable
fun SeraProfile(identity: String, loading: Boolean, error: String, onRetry: () -> Unit, onSignOut: () -> Unit, token: String = "", userId: String = "", avatarUrl: String? = null) {
    var editing by remember { mutableStateOf(false) }
    var changingPassword by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var details by remember(userId) { mutableStateOf<NativeProfileFields?>(null) }
    LaunchedEffect(token, userId, reload) {
        if (token.isNotBlank() && userId.isNotBlank()) {
            details = withContext(Dispatchers.IO) { loadNativeProfile(token, userId).first }
        }
    }
    if (changingPassword && token.isNotBlank()) SeraNativeChangePassword(token, onDismiss = { changingPassword = false })
    if (editing && token.isNotBlank() && userId.isNotBlank()) SeraNativeProfileEditor(token, userId, onDismiss = { editing = false }, onSaved = { editing = false; reload++; onRetry() })
    val name = details?.fullName?.takeIf { it.isNotBlank() } ?: identity.lineSequence().firstOrNull().orEmpty().ifBlank { "SERA member" }
    val email = identity.lineSequence().drop(1).firstOrNull().orEmpty()
    val canEdit = token.isNotBlank() && userId.isNotBlank()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color.White, border = BorderStroke(1.dp, profileLine)) {
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(132.dp)) {
                    Box(Modifier.fillMaxWidth().height(96.dp).background(
                        Brush.linearGradient(listOf(Color(0xFF102F36), Color(0xFF087E83), Color(0xFF43B5A8)))))
                    Box(Modifier.align(Alignment.BottomStart).padding(start = 18.dp).size(76.dp)
                        .clip(CircleShape).background(Color.White).padding(4.dp)
                        .clip(CircleShape).background(Color(0xFFE0F4F1)), contentAlignment = Alignment.Center) {
                        if (!avatarUrl.isNullOrBlank()) SeraMediaImage(url = avatarUrl, description = "Profile photo", widthPx = 256, heightPx = 256, modifier = Modifier.fillMaxSize())
                        else Text(name.take(1).uppercase(), color = profileTeal, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    }
                    if (canEdit) FilledTonalButton(onClick = { editing = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFE1F3F0), contentColor = profileTeal)) {
                        Icon(Icons.Outlined.EditNote, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text("Edit profile")
                    }
                }
                Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(name, fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, color = profileInk)
                    Text(details?.headline?.takeIf { it.isNotBlank() } ?: "Your professional profile", color = profileMuted, fontSize = 14.sp)
                    details?.location?.takeIf { it.isNotBlank() }?.let { Text(it, color = profileMuted, fontSize = 13.sp) }
                    if (email.isNotBlank()) Text(email, color = profileMuted, fontSize = 12.sp)
                }
            }
        }
        if (canEdit) Surface(shape = RoundedCornerShape(22.dp), color = Color.White, border = BorderStroke(1.dp, profileLine)) {
            Box(Modifier.fillMaxWidth().padding(16.dp)) { SeraNativeAccountFiles(token, userId, onUploaded = { reload++; onRetry() }) }
        }
        Surface(shape = RoundedCornerShape(22.dp), color = Color.White, border = BorderStroke(1.dp, profileLine)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Professional identity", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = profileInk)
                Text("About", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = profileTeal)
                Text(details?.summary?.takeIf { it.isNotBlank() } ?: "Introduce yourself and share what you bring to your work.", color = profileMuted, fontSize = 14.sp, lineHeight = 21.sp)
                HorizontalDivider(color = profileLine)
                Text("Skills", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = profileTeal)
                Text(details?.skills?.takeIf { it.isNotBlank() } ?: "Add the skills that define your expertise.", color = profileMuted, fontSize = 14.sp, lineHeight = 21.sp)
                if (canEdit) TextButton(onClick = { editing = true }, contentPadding = PaddingValues(0.dp)) { Text("Update professional details", color = profileTeal) }
            }
        }
        if(canEdit) Surface(shape=RoundedCornerShape(22.dp),color=Color.White,border=BorderStroke(1.dp,profileLine)){Box(Modifier.fillMaxWidth().padding(18.dp)){SeraPresenceSettings(token,userId)}}
        if (loading) SeraLoadingCards(count = 1)
        if (error.isNotBlank()) { Text(error, color = MaterialTheme.colorScheme.error); TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) } }
        Surface(shape = RoundedCornerShape(22.dp), color = Color.White, border = BorderStroke(1.dp, profileLine)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
                if (token.isNotBlank()) TextButton(onClick = { changingPassword = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Icon(Icons.Outlined.Lock, null, Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)); Text("Change password", Modifier.weight(1f), color = profileInk); Icon(Icons.Outlined.ChevronRight, null)
                }
                HorizontalDivider(color = profileLine)
                TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Icon(Icons.Outlined.ExitToApp, null, Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.sign_out), Modifier.weight(1f), color = profileInk); Icon(Icons.Outlined.ChevronRight, null)
                }
            }
        }
    }
}
