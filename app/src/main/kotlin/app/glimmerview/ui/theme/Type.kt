@file:OptIn(ExperimentalTextApi::class)

package app.glimmerview.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.glimmerview.R

/** Spectral (Production Type, licence OFL) : titres et texte. */
val Spectral = FontFamily(
    Font(R.font.spectral_light, FontWeight.Light),
    Font(R.font.spectral_light_italic, FontWeight.Light, FontStyle.Italic),
    Font(R.font.spectral_regular, FontWeight.Normal),
    Font(R.font.spectral_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.spectral_medium, FontWeight.Medium),
    Font(R.font.spectral_semibold, FontWeight.SemiBold),
)

/** Hanken Grotesk (licence OFL) : réservée au cartel, en petites capitales espacées. */
val Hanken = FontFamily(
    Font(
        R.font.hanken_grotesk,
        FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
)

/** Échelle typographique de la charte « Galerie ». */
object GlimmerType {
    val display = TextStyle(
        fontFamily = Spectral,
        fontWeight = FontWeight.Light,
        fontSize = 42.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.015).em,
    )
    val headline = TextStyle(
        fontFamily = Spectral,
        fontWeight = FontWeight.Light,
        fontSize = 32.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.012).em,
    )
    val title = TextStyle(
        fontFamily = Spectral,
        fontWeight = FontWeight.Light,
        fontSize = 28.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.012).em,
    )
    val tirette = TextStyle(
        fontFamily = Spectral,
        fontWeight = FontWeight.Light,
        fontStyle = FontStyle.Italic,
        fontSize = 23.sp,
        lineHeight = 28.sp,
    )
    val body = TextStyle(fontFamily = Spectral, fontSize = 15.sp, lineHeight = 22.sp)
    val bodySmall = TextStyle(fontFamily = Spectral, fontSize = 13.sp, lineHeight = 19.sp)
    val caption = TextStyle(fontFamily = Spectral, fontSize = 12.sp, lineHeight = 17.sp)
    val strong = TextStyle(fontFamily = Spectral, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp)
    val italic = TextStyle(fontFamily = Spectral, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 20.sp)
    val numeral = TextStyle(fontFamily = Spectral, fontWeight = FontWeight.Light, fontSize = 17.sp)
    val cartel = TextStyle(
        fontFamily = Hanken,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.16.em,
    )
}

/** Typographie Material : tout en Spectral (le cartel passe par [GlimmerType.cartel]). */
internal val MaterialTypography: Typography = Typography().run {
    fun TextStyle.spectral() = copy(fontFamily = Spectral)
    Typography(
        displayLarge = displayLarge.spectral().copy(fontWeight = FontWeight.Light),
        displayMedium = displayMedium.spectral().copy(fontWeight = FontWeight.Light),
        displaySmall = displaySmall.spectral().copy(fontWeight = FontWeight.Light),
        headlineLarge = headlineLarge.spectral().copy(fontWeight = FontWeight.Light),
        headlineMedium = headlineMedium.spectral().copy(fontWeight = FontWeight.Light),
        headlineSmall = headlineSmall.spectral().copy(fontWeight = FontWeight.Light),
        titleLarge = titleLarge.spectral(),
        titleMedium = titleMedium.spectral(),
        titleSmall = titleSmall.spectral(),
        bodyLarge = bodyLarge.spectral(),
        bodyMedium = bodyMedium.spectral(),
        bodySmall = bodySmall.spectral(),
        labelLarge = labelLarge.spectral(),
        labelMedium = labelMedium.spectral(),
        labelSmall = labelSmall.spectral(),
    )
}
