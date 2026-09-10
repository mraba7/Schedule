package com.mrabah.oneuischedule.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.*
import com.mrabah.oneuischedule.R

/**
 * IBM Plex Sans Arabic: Arabic and Latin drawn as one family, so "الحصة 2"
 * and "8:05" share a baseline. The widget cannot use it — the launcher's
 * process only reaches system fonts.
 */
internal val PlexArabic = FontFamily(
    Font(R.font.plex_arabic_regular, FontWeight.Normal),
    Font(R.font.plex_arabic_medium, FontWeight.Medium),
    Font(R.font.plex_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.plex_arabic_bold, FontWeight.Bold),
)

internal fun typographyOf(base: Typography) = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = PlexArabic),
    displayMedium = base.displayMedium.copy(fontFamily = PlexArabic),
    displaySmall = base.displaySmall.copy(fontFamily = PlexArabic),
    headlineLarge = base.headlineLarge.copy(fontFamily = PlexArabic),
    headlineMedium = base.headlineMedium.copy(fontFamily = PlexArabic),
    headlineSmall = base.headlineSmall.copy(fontFamily = PlexArabic),
    titleLarge = base.titleLarge.copy(fontFamily = PlexArabic),
    titleMedium = base.titleMedium.copy(fontFamily = PlexArabic),
    titleSmall = base.titleSmall.copy(fontFamily = PlexArabic),
    bodyLarge = base.bodyLarge.copy(fontFamily = PlexArabic),
    bodyMedium = base.bodyMedium.copy(fontFamily = PlexArabic),
    bodySmall = base.bodySmall.copy(fontFamily = PlexArabic),
    labelLarge = base.labelLarge.copy(fontFamily = PlexArabic),
    labelMedium = base.labelMedium.copy(fontFamily = PlexArabic),
    labelSmall = base.labelSmall.copy(fontFamily = PlexArabic),
)


@Composable
internal fun ScheduleTheme(dark:Boolean=isSystemInDarkTheme(),content:@Composable ()->Unit) {
    val colors=if(dark) darkColorScheme(
        primary=Color(0xFFE3C890),onPrimary=Color(0xFF352A14),primaryContainer=Color(0xFF344350),onPrimaryContainer=Color(0xFFF4ECD9),
        secondary=Color(0xFFB2C9E0),background=Color(0xFF101A24),surface=Color(0xFF17232F),onSurface=Color(0xFFF0F2F5),
        onSurfaceVariant=Color(0xFFB7C3D0),surfaceContainerLow=Color(0xFF192632),surfaceContainerHigh=Color(0xFF253442)
    ) else lightColorScheme(
        primary=Color(0xFF705327),onPrimary=Color.White,primaryContainer=Color(0xFFEEE2CC),onPrimaryContainer=Color(0xFF342B1D),
        secondary=Color(0xFF3F617A),background=Color(0xFFF5F6F8),surface=Color.White,onSurface=Color(0xFF192B3A),
        onSurfaceVariant=Color(0xFF596976),surfaceContainerLow=Color(0xFFF0F3F6),surfaceContainerHigh=Color(0xFFE7EDF2)
    )
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme=colors,typography=typographyOf(Typography()),content=content)
    }
}
