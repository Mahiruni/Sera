package et.sera.nativeapp

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Shared chat tokens. Use platform fonts for script fallback and system font scaling. */
internal data class SeraChatPalette(
    val canvas:Color,val surface:Color,val incoming:Color,val outgoing:Color,
    val ink:Color,val muted:Color,val accent:Color,val border:Color,val quote:Color,
    val selection:Color,val onOutgoing:Color=Color.White,val outgoingMeta:Color=Color(0xFFD1ECEA),
    val outgoingLink:Color=Color(0xFFB5FFF4),val outgoingQuote:Color=Color(0xFF09585D)
)
internal fun seraChatPalette(dark:Boolean)=if(dark) SeraChatPalette(
    Color(0xFF101C20),Color(0xFF1B2B30),Color(0xFF25383E),Color(0xFF07696D),
    Color(0xFFE9F4F3),Color(0xFFA8C3C5),Color(0xFF79E3D9),Color(0xFF364D52),
    Color(0xFF17292E),Color(0xFF214346)
) else SeraChatPalette(
    Color(0xFFF5F9F8),Color(0xFFFFFFFF),Color(0xFFFFFFFF),Color(0xFF09696E),
    Color(0xFF183538),Color(0xFF526A6D),Color(0xFF087E83),Color(0xFFDCE8E6),
    Color(0xFFEFF5F4),Color(0xFFE0F0ED)
)
@Composable internal fun seraChatColors()=seraChatPalette(isSystemInDarkTheme())
internal fun seraChatTypography(base:Typography)=base.copy(
    bodyLarge=base.bodyLarge.copy(fontSize=15.sp,lineHeight=23.sp,letterSpacing=0.1.sp,fontWeight=FontWeight.Normal),
    bodyMedium=base.bodyMedium.copy(fontSize=14.sp,lineHeight=21.sp,letterSpacing=0.1.sp,fontWeight=FontWeight.Normal),
    bodySmall=base.bodySmall.copy(fontSize=12.sp,lineHeight=18.sp,letterSpacing=0.1.sp),
    labelSmall=base.labelSmall.copy(fontSize=11.sp,lineHeight=15.sp,letterSpacing=0.15.sp,fontWeight=FontWeight.Medium),
    labelMedium=base.labelMedium.copy(fontSize=12.sp,lineHeight=17.sp,letterSpacing=0.15.sp,fontWeight=FontWeight.Medium),
    titleMedium=base.titleMedium.copy(fontSize=17.sp,lineHeight=23.sp,fontWeight=FontWeight.SemiBold,letterSpacing=0.sp),
    titleLarge=base.titleLarge.copy(fontSize=22.sp,lineHeight=29.sp,fontWeight=FontWeight.SemiBold,letterSpacing=0.sp)
)
internal fun seraChatBody(base:TextStyle)=base.copy(fontSize=15.sp,lineHeight=23.sp,letterSpacing=0.1.sp,fontWeight=FontWeight.Normal)
internal fun seraChatMeta(base:TextStyle)=base.copy(fontSize=11.sp,lineHeight=15.sp,letterSpacing=0.15.sp,fontWeight=FontWeight.Medium)
internal fun seraChatBubbleShape(own:Boolean)=if(own)
    RoundedCornerShape(topStart=20.dp,topEnd=20.dp,bottomStart=20.dp,bottomEnd=8.dp)
    else RoundedCornerShape(topStart=20.dp,topEnd=20.dp,bottomStart=8.dp,bottomEnd=20.dp)
internal fun seraChatScheme(base:ColorScheme,p:SeraChatPalette,dark:Boolean)=base.copy(
    primary=p.accent,onPrimary=if(dark)Color(0xFF10383B)else Color.White,
    primaryContainer=p.selection,onPrimaryContainer=p.ink,
    surface=p.surface,onSurface=p.ink,surfaceVariant=p.quote,onSurfaceVariant=p.muted,
    background=p.canvas,onBackground=p.ink,outline=p.border,outlineVariant=p.border
)
