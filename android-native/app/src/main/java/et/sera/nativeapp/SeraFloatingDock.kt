package et.sera.nativeapp

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
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

private val dockBackground = Color(0xDC173438)
private val dockActive = Color(0xFF8EF3E4)
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
        color = Color.Transparent,
        shape = RoundedCornerShape(100.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .28f)),
        shadowElevation = 10.dp,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xEE315257), dockBackground, Color(0xEE122D32))))
                .padding(horizontal = 8.dp, vertical = 5.dp),
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
                val scale by animateFloatAsState(if (active) 1.10f else 1f, spring(), label = "dock icon scale")
                Column(
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp)
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
                        modifier = Modifier.size(25.dp).graphicsLayer { scaleX = scale; scaleY = scale })
                    Spacer(Modifier.height(5.dp))
                    Box(Modifier.size(width = if (active) 11.dp else 3.dp, height = 3.dp)
                        .background(if (active) dockActive else Color.Transparent, CircleShape))
                    
                }
            }
        }
    }
}
