package et.sera.nativeapp

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Explore
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


private fun dockIcon(section: String): ImageVector = when (section) {
    "Search" -> Icons.Outlined.Explore
    "Home" -> Icons.Outlined.Home
    "Network" -> Icons.Outlined.PeopleOutline
    "Jobs" -> Icons.Outlined.WorkOutline
    "Inbox" -> Icons.Outlined.ChatBubbleOutline
    else -> Icons.Outlined.AccountCircle
}

/** Floating, safe-area-aware dock; no full-width background or costly blur. */
@Composable
internal fun SeraFloatingDock(
    selected: String,
    sections: List<String>,
    onNavigate: (String) -> Unit,
    canCompose: Boolean = true,
    profileAvatarUrl: String? = null,
    inboxCount: Int = 0
) {
    val colors=MaterialTheme.colorScheme
    val dockActive=colors.primary
    val dockIdle=colors.onSurfaceVariant
    val haptics=androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=12.dp,vertical=8.dp),contentAlignment=Alignment.Center){
    Surface(
        color = colors.surface.copy(alpha=.96f),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, colors.outlineVariant),
        shadowElevation = 5.dp,
        tonalElevation = 0.dp,
        modifier = Modifier.widthIn(max=420.dp).fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth()
                .height(58.dp).padding(horizontal = 6.dp),
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
                            enabled = true,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true)
                        ) { haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove); onNavigate(section) }
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
                    } else if(section == "Inbox") {
                        BadgedBox(badge={if(inboxCount>0)Badge(containerColor=dockActive,contentColor=colors.onPrimary){Text(if(inboxCount>99)"99+" else inboxCount.toString(),fontSize=10.sp,fontWeight=FontWeight.Bold)}}){
                            Icon(dockIcon(section),contentDescription=if(inboxCount>0)"Messages, $inboxCount unread" else "Messages",tint=tint,modifier=Modifier.size(24.dp).graphicsLayer{scaleX=scale;scaleY=scale})
                        }
                    } else {
                        Icon(dockIcon(section), contentDescription = if(section=="Search")"Discover" else section, tint = tint,
                            modifier = Modifier.size(24.dp).graphicsLayer { scaleX = scale; scaleY = scale })
                    }
                    Spacer(Modifier.height(5.dp))
                    Box(Modifier.size(width = if (active) 11.dp else 3.dp, height = 3.dp)
                        .background(if (active) dockActive else Color.Transparent, CircleShape))
                    
                }
            }
        }
    }
}
}
