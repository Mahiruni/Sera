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


/** Exact 24x24 Lucide 0.468.0 paths used by the web shell (ISC license). */
@Composable
internal fun SeraDockGlyph(section:String,tint:Color,modifier:Modifier=Modifier){
    val paths=remember(section){
        val segments=when(section){
            "Home"->listOf("M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8","M3 10a2 2 0 0 1 .709-1.528l7-5.999a2 2 0 0 1 2.582 0l7 5.999A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z")
            "Network"->listOf("M18 21a8 8 0 0 0-16 0","M15 8a5 5 0 1 1-10 0a5 5 0 1 1 10 0","M22 20c0-3.37-2-6.5-4-8a5 5 0 0 0-.45-8.3")
            "Search"->listOf("m16.24 7.76-1.804 5.411a2 2 0 0 1-1.265 1.265L7.76 16.24l1.804-5.411a2 2 0 0 1 1.265-1.265z","M22 12a10 10 0 1 1-20 0a10 10 0 1 1 20 0")
            "Jobs"->listOf("M12 12h.01","M16 6V4a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v2","M22 13a18.15 18.15 0 0 1-20 0","M4 6h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z")
            "Inbox"->listOf("M14 9a2 2 0 0 1-2 2H6l-4 4V4a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2z","M18 9h2a2 2 0 0 1 2 2v11l-4-4h-6a2 2 0 0 1-2-2v-1")
            else->listOf("M22 12a10 10 0 1 1-20 0a10 10 0 1 1 20 0","M16 8a4 4 0 1 1-8 0a4 4 0 1 1 8 0","M5 20v-1a7 7 0 0 1 14 0v1")
        }
        segments.map{PathParser().parsePathString(it).toPath()}
    }
    Canvas(modifier.size(23.dp)){
        val factor=size.minDimension/24f
        withTransform({scale(factor,factor,pivot=Offset.Zero)}){
            paths.forEach{drawPath(it,tint,style=Stroke(width=1.8f,cap=StrokeCap.Round,join=StrokeJoin.Round))}
        }
    }
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
                        .semantics { this.selected=active;contentDescription=when(section){"Search"->"Discover";"Inbox"->if(inboxCount>0)"Messages, $inboxCount unread" else "Messages";else->section} }
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
