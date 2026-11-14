package et.sera.nativeapp

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


/** Shared signature geometry; footer destinations retain their exact existing shapes. */
@Composable internal fun SeraDockGlyph(section:String,tint:Color,modifier:Modifier=Modifier){
    val key=when(section){"Home"->"Home";"Network"->"PeopleOutline";"Explore"->"Explore";"Search"->"Search";"Jobs"->"WorkOutline";"Inbox"->"Inbox";else->"AccountCircle"}
    SeraSignatureGlyph(key,tint,modifier,size=23.dp)
}

/** Edge-docked, safe-area-aware glass surface; no extra bottom margin. */
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
    Box(Modifier.fillMaxWidth().testTag("sera_dock_root").navigationBarsPadding().padding(horizontal=12.dp),contentAlignment=Alignment.BottomCenter){
    Surface(
        color = colors.surface.copy(alpha=.96f),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, colors.outlineVariant),
        shadowElevation = 1.dp,
        tonalElevation = 0.dp,
        modifier = Modifier.widthIn(max=420.dp).fillMaxWidth().testTag("sera_dock_surface")
    ) {
        Row(
            Modifier.fillMaxWidth()
                .height(58.dp).padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            sections.forEach { section ->
                val active = selected == section
                val exploreLabel = androidx.compose.ui.res.stringResource(R.string.explore_title)
                val tint by animateColorAsState(
                    targetValue = if (active) dockActive else dockIdle,
                    animationSpec = spring(),
                    label = "dock tint"
                )
                val scale by animateFloatAsState(if (active) 1.04f else 1f, spring(), label = "dock icon scale")
                Column(
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .testTag("sera_dock_"+section)
                        .semantics { this.selected=active;contentDescription=when(section){"Explore"->exploreLabel;"Search"->"Search";"Inbox"->if(inboxCount>0)"Messages, $inboxCount unread" else "Messages";else->section} }
                        .clickable(
                            enabled = true,
                            role = Role.Tab,
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
                            SeraDockGlyph(section,tint,Modifier.graphicsLayer{scaleX=scale;scaleY=scale})
                        }
                    } else {
                        SeraDockGlyph(section,tint,Modifier.graphicsLayer { scaleX = scale; scaleY = scale })
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
