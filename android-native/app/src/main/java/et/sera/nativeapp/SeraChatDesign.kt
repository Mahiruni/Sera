package et.sera.nativeapp

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Shared chat tokens. Use platform fonts for script fallback and system font scaling. */
internal data class SeraChatPalette(
    val canvas:Color,val surface:Color,val incoming:Color,val outgoing:Color,
    val ink:Color,val muted:Color,val accent:Color,val border:Color,val quote:Color,
    val selection:Color,val onOutgoing:Color,val outgoingMeta:Color,
    val outgoingLink:Color,val outgoingQuote:Color,val read:Color,
    val button:Color,val onButton:Color,val outgoingBorder:Color,val header:Color
)
internal fun seraChatPalette(dark:Boolean)=if(dark) SeraChatPalette(
    canvas=Color(0xFF142125),surface=Color(0xFF152326),incoming=Color(0xFF293B40),outgoing=Color(0xFF18544F),
    ink=Color(0xFFE8F1EF),muted=Color(0xFFA9BDBB),accent=Color(0xFF8AD7CA),border=Color(0xFF364B4E),
    quote=Color(0xFF1B2B2E),selection=Color(0xFF244B48),onOutgoing=Color(0xFFEFFBF8),outgoingMeta=Color(0xFFB7D6CE),
    outgoingLink=Color(0xFFA4F0DC),outgoingQuote=Color(0xFF123F3E),read=Color(0xFF99ECD6),
    button=Color(0xFF83D7CA),onButton=Color(0xFF103633),outgoingBorder=Color(0xFF2F635F),header=Color(0xFF0D191D)
) else SeraChatPalette(
    canvas=Color(0xFFF5F9F8),surface=Color.White,incoming=Color.White,outgoing=Color(0xFFD4EFEB),
    ink=Color(0xFF20363A),muted=Color(0xFF5B7073),accent=Color(0xFF086E70),border=Color(0xFFDCE7E3),
    quote=Color(0xFFEDF3F1),selection=Color(0xFFD4EAE4),onOutgoing=Color(0xFF16443F),outgoingMeta=Color(0xFF476C64),
    outgoingLink=Color(0xFF095E59),outgoingQuote=Color(0xFFCCE6E1),read=Color(0xFF096A5B),
    button=Color(0xFF087E83),onButton=Color.White,outgoingBorder=Color(0xFFC6E2DB),header=Color(0xFFE0EEEB)
)
// Follow the active scoped theme, including dark previews and conversation dialogs.
@Composable internal fun seraChatColors()=seraChatPalette(MaterialTheme.colorScheme.surface.luminance()<.5f)
internal fun seraChatTypography(base:Typography)=base.copy(
    bodyLarge=seraChatBody(base.bodyLarge),
    bodyMedium=base.bodyMedium.copy(fontFamily=FontFamily.SansSerif,fontSize=14.sp,lineHeight=22.sp,letterSpacing=0.sp,fontWeight=FontWeight.Normal),
    bodySmall=base.bodySmall.copy(fontFamily=FontFamily.SansSerif,fontSize=12.sp,lineHeight=18.sp,letterSpacing=0.sp),
    labelSmall=seraChatMeta(base.labelSmall),
    labelMedium=base.labelMedium.copy(fontSize=12.sp,lineHeight=18.sp,letterSpacing=0.1.sp,fontWeight=FontWeight.Medium),
    labelLarge=base.labelLarge.copy(fontSize=13.sp,lineHeight=20.sp,letterSpacing=0.sp,fontWeight=FontWeight.Medium),
    titleMedium=base.titleMedium.copy(fontSize=17.sp,lineHeight=24.sp,fontWeight=FontWeight.SemiBold,letterSpacing=(-0.15).sp),
    titleLarge=base.titleLarge.copy(fontSize=22.sp,lineHeight=30.sp,fontWeight=FontWeight.SemiBold,letterSpacing=(-0.3).sp)
)
internal fun seraChatBody(base:TextStyle)=base.copy(fontFamily=FontFamily.SansSerif,fontSize=15.sp,lineHeight=24.sp,letterSpacing=0.sp,fontWeight=FontWeight.Normal)
internal fun seraChatMeta(base:TextStyle)=base.copy(fontFamily=FontFamily.SansSerif,fontSize=11.sp,lineHeight=16.sp,letterSpacing=0.1.sp,fontWeight=FontWeight.Medium,fontFeatureSettings="tnum")
internal fun seraChatBubbleShape(own:Boolean)=if(own)
    RoundedCornerShape(topStart=22.dp,topEnd=22.dp,bottomStart=22.dp,bottomEnd=9.dp)
    else RoundedCornerShape(topStart=22.dp,topEnd=22.dp,bottomStart=9.dp,bottomEnd=22.dp)
internal fun seraChatScheme(base:ColorScheme,p:SeraChatPalette,dark:Boolean)=base.copy(
    primary=p.accent,onPrimary=if(dark)Color(0xFF10383B)else Color.White,
    primaryContainer=p.selection,onPrimaryContainer=p.ink,
    surface=p.surface,onSurface=p.ink,surfaceVariant=p.quote,onSurfaceVariant=p.muted,
    background=p.canvas,onBackground=p.ink,outline=p.border,outlineVariant=p.border
)
