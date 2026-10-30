package com.blueway.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.blueway.app.R

/** Paleta de marca. En alto contraste los textos pasan a casi negro y las tarjetas a blanco puro. */
data class BwColors(
    val ink: Color,
    val inkSoft: Color,
    val muted: Color,
    val primary: Color,
    val surface: Color,
    val tint: Color,
    val outline: Color,
    val background: Color,
    val green: Color,
    val greenSoft: Color,
    val highContrast: Boolean,
) {
    val gradient: Brush
        get() = if (highContrast) {
            Brush.horizontalGradient(listOf(Color(0xFF0A2CAC), Color(0xFF0A2CAC)))
        } else {
            Brush.horizontalGradient(listOf(Color(0xFF29B6F6), Color(0xFF1678E4), Color(0xFF0A2CAC)))
        }
}

private val Standard = BwColors(
    ink = Color(0xFF0A2CAC),
    inkSoft = Color(0xFF0C2A8F),
    muted = Color(0xFF55618A),
    primary = Color(0xFF1678E4),
    surface = Color.White,
    tint = Color(0xFFEFF6FE),
    outline = Color(0xFFDCE6F5),
    background = Color(0xFFF8FBFF),
    green = Color(0xFF0B8A5A),
    greenSoft = Color(0xFFE4F8EF),
    highContrast = false,
)

private val HighContrast = BwColors(
    ink = Color(0xFF00105C),
    inkSoft = Color(0xFF00105C),
    muted = Color(0xFF1F2A4D),
    primary = Color(0xFF0A2CAC),
    surface = Color.White,
    tint = Color(0xFFFFFFFF),
    outline = Color(0xFF00105C),
    background = Color.White,
    green = Color(0xFF00603A),
    greenSoft = Color(0xFFFFFFFF),
    highContrast = true,
)

val LocalBw = compositionLocalOf { Standard }
val LocalReduceMotion = compositionLocalOf { false }

@OptIn(ExperimentalTextApi::class)
private fun nunito(weight: FontWeight) = Font(
    R.font.nunito,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Nunito = FontFamily(
    nunito(FontWeight.Normal),
    nunito(FontWeight.Medium),
    nunito(FontWeight.SemiBold),
    nunito(FontWeight.Bold),
    nunito(FontWeight.ExtraBold),
)

private val BwType = Typography(
    headlineLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, lineHeight = 27.sp),
    titleMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 23.sp),
    titleSmall = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 16.sp),
    labelMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 11.5.sp),
)

@Composable
fun BlueWayTheme(highContrast: Boolean, reduceMotion: Boolean, content: @Composable () -> Unit) {
    val colors = if (highContrast) HighContrast else Standard
    CompositionLocalProvider(LocalBw provides colors, LocalReduceMotion provides reduceMotion) {
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = colors.primary,
                onPrimary = Color.White,
                surface = colors.surface,
                onSurface = colors.ink,
                background = colors.background,
                onBackground = colors.ink,
                surfaceContainerLow = colors.surface,
                surfaceContainerHigh = colors.surface,
            ),
            typography = BwType,
            content = content,
        )
    }
}
