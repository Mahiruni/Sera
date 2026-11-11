package et.sera.nativeapp

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
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
import androidx.compose.ui.unit.dp

private val dockBackground = Color(0xF21A3438)
private val dockActive = Color(0xFFA0F6E6)
private val dockIdle = Color(0xFFD7E5E4)

private fun dockIcon(section: String): ImageVector = when (section) {
    "Home" -> Icons.Outlined.Home
    "Network" -> Icons.Outlined.PeopleOutline
    "Jobs" -> Icons.Outlined.WorkOutline
    "Inbox" -> Icons.Outlined.ChatBubbleOutline
    else -> Icons.Outlined.AccountCircle
}

/** Bottom-attached navigation bar: five equal targets, no floating margins. */
@Composable
internal fun SeraFloatingDock(
    selected: String,
    sections: List<String>,
    onNavigate: (String) -> Unit,
    profileAvatarUrl: String? = null
) {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xE6335053), dockBackground, Color(0xF2132B30))))
                .padding(horizontal = 6.dp, vertical = 2.dp),
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
                val scale by animateFloatAsState(if (active) 1.06f else 1f, spring(), label = "dock icon scale")
                Column(
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .selectable(
                            selected = active,
                            role = Role.Tab,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true),
                            onClick = { onNavigate(section) }
                        )
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (section == "Profile" && !profileAvatarUrl.isNullOrBlank()) {
                        SeraMediaImage(
                            url = profileAvatarUrl,
                            description = "Your profile",
                            widthPx = 96, heightPx = 96,
                            modifier = Modifier.size(26.dp).clip(CircleShape)
                        )
                    } else {
                        Icon(dockIcon(section), contentDescription = section, tint = tint,
                            modifier = Modifier.size(24.dp).graphicsLayer { scaleX = scale; scaleY = scale })
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.size(width = 10.dp, height = 2.dp)
                        .background(if (active) dockActive else Color.Transparent, CircleShape))
                    
                }
            }
        }
    }
}
