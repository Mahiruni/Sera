package et.sera.nativeapp

import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color

/** Shared Sera surface roles. Explicit containers prevent Material's default lavender. */
internal fun seraColorScheme(dark:Boolean):ColorScheme = if(dark) darkColorScheme(
    primary=Color(0xFF43D5CC), onPrimary=Color(0xFF073B3B),
    primaryContainer=Color(0xFF204642),onPrimaryContainer=Color(0xFFB5F2EC),
    secondary=Color(0xFF91C9C5),onSecondary=Color(0xFF163634),
    secondaryContainer=Color(0xFF244440),onSecondaryContainer=Color(0xFFDEF4F1),
    tertiary=Color(0xFFC4B5FD),onTertiary=Color(0xFF35106A),tertiaryContainer=Color(0xFF35254C),onTertiaryContainer=Color(0xFFE9DDFF),
    background=Color(0xFF0D171A),onBackground=Color(0xFFF0F7F6),
    surface=Color(0xFF152326),onSurface=Color(0xFFF0F7F6),
    surfaceVariant=Color(0xFF1D3033),onSurfaceVariant=Color(0xFFA0B6B8),
    surfaceContainerLowest=Color(0xFF0D171A),surfaceContainerLow=Color(0xFF152326),
    surfaceContainer=Color(0xFF1D3033),surfaceContainerHigh=Color(0xFF23373A),surfaceContainerHighest=Color(0xFF2A4144),
    outline=Color(0xFF668487),outlineVariant=Color(0xFF263B3E),surfaceTint=Color(0xFF43D5CC),
    error=Color(0xFFFFB4AB),onError=Color(0xFF690005)
) else lightColorScheme(
    primary=Color(0xFF0D7C86),onPrimary=Color.White,
    primaryContainer=Color(0xFFE5F4F2),onPrimaryContainer=Color(0xFF0A5E66),
    secondary=Color(0xFF0A5E66),onSecondary=Color.White,
    secondaryContainer=Color(0xFFE5F4F2),onSecondaryContainer=Color(0xFF0A5E66),
    tertiary=Color(0xFF7C3AED),onTertiary=Color.White,tertiaryContainer=Color(0xFFF3ECFF),onTertiaryContainer=Color(0xFF6D28D9),
    background=Color(0xFFF6FAF9),onBackground=Color(0xFF16363A),
    surface=Color.White,onSurface=Color(0xFF16363A),
    surfaceVariant=Color(0xFFEEF5F4),onSurfaceVariant=Color(0xFF5F7A7F),
    surfaceContainerLowest=Color.White,surfaceContainerLow=Color(0xFFF6FAF9),
    surfaceContainer=Color(0xFFEEF5F4),surfaceContainerHigh=Color(0xFFE5F4F2),surfaceContainerHighest=Color(0xFFDDEEEB),
    outline=Color(0xFF819E9C),outlineVariant=Color(0xFFD9E8E5),surfaceTint=Color(0xFF0D7C86),
    error=Color(0xFFB3263A),onError=Color.White
)
