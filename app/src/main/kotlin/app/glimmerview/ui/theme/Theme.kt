package app.glimmerview.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Thème « Galerie » : murs plâtre, encre, un seul accent bleu de Prusse.
 * Volontairement clair et fixe (pas de couleurs dynamiques) : les œuvres apportent la couleur.
 */
@Composable
fun GlimmerTheme(content: @Composable () -> Unit) {
    val colors = lightColorScheme(
        primary = GlimmerColors.Ink,
        onPrimary = GlimmerColors.Plaster,
        primaryContainer = GlimmerColors.Linen,
        onPrimaryContainer = GlimmerColors.Ink,
        secondary = GlimmerColors.Prussian,
        onSecondary = GlimmerColors.Plaster,
        secondaryContainer = GlimmerColors.Linen,
        onSecondaryContainer = GlimmerColors.Ink,
        tertiary = GlimmerColors.Prussian,
        onTertiary = GlimmerColors.Plaster,
        background = GlimmerColors.Plaster,
        onBackground = GlimmerColors.Ink,
        surface = GlimmerColors.Plaster,
        onSurface = GlimmerColors.Ink,
        surfaceVariant = GlimmerColors.Linen,
        onSurfaceVariant = GlimmerColors.Taupe,
        surfaceContainerLowest = GlimmerColors.Mat,
        surfaceContainerLow = GlimmerColors.Plaster,
        surfaceContainer = GlimmerColors.Linen,
        surfaceContainerHigh = GlimmerColors.Linen,
        surfaceContainerHighest = GlimmerColors.Linen,
        outline = GlimmerColors.Stone,
        outlineVariant = GlimmerColors.Stone,
    )
    MaterialTheme(colorScheme = colors, typography = MaterialTypography) {
        CompositionLocalProvider(
            LocalContentColor provides GlimmerColors.Ink,
            LocalTextStyle provides GlimmerType.body,
            content = content,
        )
    }
}
