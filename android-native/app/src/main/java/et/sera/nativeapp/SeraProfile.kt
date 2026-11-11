package et.sera.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SeraProfile(identity: String, loading: Boolean, error: String, onRetry: () -> Unit, onSignOut: () -> Unit) {
    val name = identity.lineSequence().firstOrNull().orEmpty().ifBlank { "SERA member" }
    val email = identity.lineSequence().drop(1).firstOrNull().orEmpty()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ElevatedCard(shape = RoundedCornerShape(24.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(84.dp).clip(CircleShape).background(Color(0xFFE0F4F1)), contentAlignment = Alignment.Center) {
                    Text(name.take(1).uppercase(), color = Color(0xFF087E83), fontSize = 36.sp, fontWeight = FontWeight.Bold)
                }
                Text(name, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color(0xFF183B40))
                if (email.isNotBlank()) Text(email, color = Color.Gray, fontSize = 14.sp)
                Text(stringResource(R.string.profile_identity), color = Color(0xFF087E83), fontSize = 13.sp)
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error.isNotBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
        Text(stringResource(R.string.profile_pending), color = Color(0xFF50656A))
        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.sign_out)) }
    }
}
