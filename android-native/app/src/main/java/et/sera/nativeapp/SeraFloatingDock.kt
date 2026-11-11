package et.sera.nativeapp

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val dockBackground = Color(0xFF173438)
private val dockActive = Color(0xFF7CE1D6)
private val dockIdle = Color(0xFFD3E0E0)

private fun dockIcon(section: String): ImageVector = when (section) {
    "Home" -> Icons.Outlined.Home
    "Network" -> Icons.Outlined.PeopleOutline
    "Jobs" -> Icons.Outlined.WorkOutline
    "Inbox" -> Icons.Outlined.ChatBubbleOutline
    else -> Icons.Outlined.AccountCircle
}

/** Floating pill dock: equal-sized, accessible destinations with no selection background. */
@Composable
internal fun SeraFloatingDock(
    selected: String,
    sections: List<String>,
    onNavigate: (String) -> Unit
) {
    Surface(
        color = dockBackground,
        shape = RoundedCornerShape(36.dp),
        shadowElevation = 14.dp,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            sections.forEach { section ->
                val active = selected == section
                val tint by animateColorAsState(
                    targetValue = if (active) dockActive else dockIdle,
                    animationSpec = spring(),
                    label = "dock tint"
                )
                Column(
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true)
                        ) { onNavigate(section) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(dockIcon(section), contentDescription = section, tint = tint,
                        modifier = Modifier.size(23.dp))
                    
                }
            }
        }
    }
}
