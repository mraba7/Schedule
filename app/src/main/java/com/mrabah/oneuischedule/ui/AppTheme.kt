package com.mrabah.oneuischedule.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.*
import com.mrabah.oneuischedule.R

/** Shared Arabic typography across the app and widget detail screens. */
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
    val context=androidx.compose.ui.platform.LocalContext.current
    val prefs=remember{context.getSharedPreferences("app_preferences",0)}
    var revision by remember{mutableStateOf(0)}
    DisposableEffect(prefs){val l=android.content.SharedPreferences.OnSharedPreferenceChangeListener{_,_->revision++};prefs.registerOnSharedPreferenceChangeListener(l);onDispose{prefs.unregisterOnSharedPreferenceChangeListener(l)}}
    val mode=remember(revision){prefs.getString("theme","system")}
    val scale=remember(revision){prefs.getFloat("font",1f).coerceIn(.85f,1.4f)}
    val density=androidx.compose.ui.platform.LocalDensity.current
    val effectiveDark=when(mode){"dark"->true;"light"->false;else->dark}
    val baseColors=if(effectiveDark) darkColorScheme(
        primary=Color(0xFFE3C890),onPrimary=Color(0xFF352A14),primaryContainer=Color(0xFF344350),onPrimaryContainer=Color(0xFFF4ECD9),
        secondary=Color(0xFFB2C9E0),secondaryContainer=Color(0xFF2C4355),onSecondaryContainer=Color(0xFFDCE9F3),surfaceVariant=Color(0xFF2A3947),surfaceContainer=Color(0xFF1D2B38),surfaceContainerHighest=Color(0xFF2A3A49),background=Color(0xFF101A24),surface=Color(0xFF17232F),onSurface=Color(0xFFF0F2F5),
        onSurfaceVariant=Color(0xFFB7C3D0),surfaceContainerLow=Color(0xFF192632),surfaceContainerHigh=Color(0xFF253442)
    ) else lightColorScheme(
        primary=Color(0xFF705327),onPrimary=Color.White,primaryContainer=Color(0xFFEEE2CC),onPrimaryContainer=Color(0xFF342B1D),
        secondary=Color(0xFF3F617A),secondaryContainer=Color(0xFFE1EBF2),onSecondaryContainer=Color(0xFF203B4C),surfaceVariant=Color(0xFFE4EAF0),surfaceContainer=Color(0xFFEDF1F5),surfaceContainerHighest=Color(0xFFE0E7EE),background=Color(0xFFF5F6F8),surface=Color.White,onSurface=Color(0xFF192B3A),
        onSurfaceVariant=Color(0xFF596976),surfaceContainerLow=Color(0xFFF0F3F6),surfaceContainerHigh=Color(0xFFE7EDF2)
    )
    val accent=remember(revision){prefs.getString("accent","gold")}
    val colors=when(accent){"blue"->baseColors.copy(primary=Color(if(effectiveDark)0xFFA6CEFA else 0xFF245D89),onPrimary=Color(if(effectiveDark)0xFF102F47 else 0xFFFFFFFF),primaryContainer=Color(if(effectiveDark)0xFF203E56 else 0xFFDCECF9),onPrimaryContainer=Color(if(effectiveDark)0xFFE4F1FD else 0xFF19364C));"violet"->baseColors.copy(primary=Color(if(effectiveDark)0xFFD2B9F2 else 0xFF71508B),onPrimary=Color(if(effectiveDark)0xFF352247 else 0xFFFFFFFF),primaryContainer=Color(if(effectiveDark)0xFF42314E else 0xFFF0E4F8),onPrimaryContainer=Color(if(effectiveDark)0xFFF4E9FB else 0xFF3D284D));else->baseColors}
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl,androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,density.fontScale*scale)) {
        MaterialTheme(colorScheme=colors,typography=typographyOf(Typography()),shapes=Shapes(small=androidx.compose.foundation.shape.RoundedCornerShape(androidx.compose.ui.unit.Dp(12f)),medium=androidx.compose.foundation.shape.RoundedCornerShape(androidx.compose.ui.unit.Dp(18f)),large=androidx.compose.foundation.shape.RoundedCornerShape(androidx.compose.ui.unit.Dp(24f))),content=content)
    }
}
