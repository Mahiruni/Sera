package et.sera.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Compact, consistent branded app bar with native accessible icon controls. */
@Composable
internal fun SeraNativeHeader(
    section: String,
    onInbox: () -> Unit,
    onLanguage: () -> Unit,
    onMenu: () -> Unit
) {
    Surface(color = Color(0xF8F5FAFB), shadowElevation = 1.dp) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenu, modifier = Modifier.size(44.dp)) {
                SeraNavigationGlyph("Menu", Color(0xFF123D42), Modifier.semantics { contentDescription = "Open navigation menu" })
            }
            Spacer(Modifier.width(3.dp))
            Text("SERA", color = Color(0xFF087E83), fontSize = 25.sp, letterSpacing = (-1.1).sp,
                fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black)
            Text(".et", color = Color(0xFF183B40), fontSize = 25.sp, letterSpacing = (-1.2).sp,
                fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onInbox, modifier = Modifier.size(48.dp)) {
                SeraNavigationGlyph("Inbox", if (section == "Inbox") Color(0xFF087E83) else Color(0xFF183B40),
                    Modifier.semantics { contentDescription = "Messages" }, active = section == "Inbox")
            }
            IconButton(onClick = onLanguage, modifier = Modifier.size(48.dp)) {
                SeraNavigationGlyph("Language", Color(0xFF183B40), Modifier.semantics { contentDescription = "Language" })
            }
        }
    }
}
