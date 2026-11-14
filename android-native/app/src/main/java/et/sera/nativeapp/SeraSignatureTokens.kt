package et.sera.nativeapp

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Generated from brand/sera-tokens.json; use only on Signature components. */
internal data class SignatureColors(val primary:Color,val onPrimary:Color,val surface:Color,val text:Color,val activity:Color,val call:Color,val activityContainer:Color,val background:Color,val muted:Color,val border:Color,val soft:Color,val onActivity:Color,val activityInk:Color,val danger:Color)
internal fun signatureColors(dark:Boolean):SignatureColors=if(dark) SignatureColors(
    primary=Color(0xFF43D5CC),
    onPrimary=Color(0xFF073B3B),
    surface=Color(0xFF152326),
    text=Color(0xFFF0F7F6),
    activity=Color(0xFFC4B5FD),
    call=Color(0xFFC4B5FD),
    activityContainer=Color(0xFF35254C),
    background=Color(0xFF0D171A),
    muted=Color(0xFFA0B6B8),
    border=Color(0xFF355056),
    soft=Color(0xFF1D3033),
    onActivity=Color(0xFF35106A),
    activityInk=Color(0xFFE9DDFF),
    danger=Color(0xFFFFB4AB)
) else SignatureColors(
    primary=Color(0xFF0D7C86),
    onPrimary=Color(0xFFFFFFFF),
    surface=Color(0xFFFFFFFF),
    text=Color(0xFF111827),
    activity=Color(0xFF7C3AED),
    call=Color(0xFF4C1D95),
    activityContainer=Color(0xFFF3ECFF),
    background=Color(0xFFF6FAF9),
    muted=Color(0xFF536C70),
    border=Color(0xFFD9E8E5),
    soft=Color(0xFFEEF5F4),
    onActivity=Color(0xFFFFFFFF),
    activityInk=Color(0xFF6D28D9),
    danger=Color(0xFFB3263A)
)
internal object SignatureMetrics {
    val gap=8.dp
    val padding=16.dp
    val radius=16.dp
    val largeRadius=24.dp
    val target=48.dp
    val body=15.sp
    val metadata=12.sp
    val heading=28.sp
}
