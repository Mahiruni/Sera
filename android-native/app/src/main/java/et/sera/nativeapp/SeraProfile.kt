package et.sera.nativeapp

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign

@Composable
fun SeraProfile(identity: String, loading: Boolean, error: String, onRetry: () -> Unit, onSignOut: () -> Unit, token: String = "", userId: String = "") {
    var editing by remember { mutableStateOf(false) }
    if (editing && token.isNotBlank() && userId.isNotBlank()) SeraNativeProfileEditor(token, userId, onDismiss = { editing = false }, onSaved = { editing = false; onRetry() })
    val name = identity.lineSequence().firstOrNull().orEmpty().ifBlank { "SERA member" }
    val email = identity.lineSequence().drop(1).firstOrNull().orEmpty()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ElevatedCard(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().animateContentSize()
        ) {
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(90.dp)
                    .background(Color(0xFF173438)))
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier.offset(y = (-38).dp).size(92.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE0F4F1)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(name.take(1).uppercase(), color = Color(0xFF087E83),
                            fontSize = 37.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(name, fontWeight = FontWeight.Bold, fontSize = 25.sp,
                        color = Color(0xFF183B40), textAlign = TextAlign.Center)
                    if (email.isNotBlank()) Text(email, color = Color(0xFF607478),
                        fontSize = 13.sp, textAlign = TextAlign.Center)
                    Surface(
                        color = Color(0xFFE5F6F2),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(stringResource(R.string.profile_identity),
                            color = Color(0xFF087E83), fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
                    }
                }
            }
        }
        if (token.isNotBlank() && userId.isNotBlank()) {
            Button(onClick = { editing = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                shape = RoundedCornerShape(16.dp)) { Text("Edit profile") }
        }
        if (loading) SeraLoadingCards(count = 1)
        if (error.isNotBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
        ElevatedCard(
            colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Professional identity", color = Color(0xFF183B40),
                    fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.profile_pending),
                    color = Color(0xFF607478), fontSize = 13.sp)
            }
        }
        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            border = BorderStroke(1.dp, Color(0xFFB6D3D2)),
            shape = RoundedCornerShape(16.dp)
        ) { Text(stringResource(R.string.sign_out)) }
    }
}
