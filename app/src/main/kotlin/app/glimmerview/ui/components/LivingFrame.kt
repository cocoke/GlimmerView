package app.glimmerview.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.glimmerview.ui.theme.GlimmerColors
import app.glimmerview.ui.theme.LocalTilt

/**
 * Cadre vivant : une œuvre sous passe-partout qui bascule avec le téléphone.
 * L'ombre portée suit la bascule (ombre de la plateforme calculée en 3D), un reflet glisse
 * sur le verre et l'image se décale légèrement derrière le passe-partout.
 * Toutes les lectures de l'inclinaison se font en phase de dessin : aucune recomposition.
 */
@Composable
fun LivingFrame(
    image: ImageBitmap?,
    modifier: Modifier = Modifier,
    mat: Dp = 12.dp,
    radius: Dp = 6.dp,
    innerRadius: Dp = 2.dp,
    elevation: Dp = 14.dp,
    still: Boolean = false,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
) {
    val tilt = LocalTilt.current
    val shape = RoundedCornerShape(radius)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.975f else 1f, tween(160), label = "press")

    val haptics = LocalHapticFeedback.current
    val clickModifier = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = interaction,
            indication = null,
            role = Role.Button,
            onLongClickLabel = onLongClickLabel,
            onLongClick = onLongClick?.let { action ->
                {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    action()
                }
            },
            onClick = onClick ?: {},
        )
    } else {
        Modifier
    }
    val semanticsModifier = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                val x = if (still) 0f else tilt.x
                val y = if (still) 0f else tilt.y
                rotationY = x * 9f
                rotationX = -y * 7f
                scaleX = press
                scaleY = press
                cameraDistance = 14f * density
                shadowElevation = elevation.toPx()
                this.shape = shape
                clip = false
                ambientShadowColor = GlimmerColors.Ink
                spotShadowColor = GlimmerColors.Ink
            }
            .background(GlimmerColors.Mat, shape)
            .border(1.dp, GlimmerColors.FrameEdge, shape)
            .then(clickModifier)
            .then(semanticsModifier)
            .padding(mat),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(innerRadius))
                .background(GlimmerColors.Linen),
        ) {
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val x = if (still) 0f else tilt.x
                            val y = if (still) 0f else tilt.y
                            scaleX = 1.08f
                            scaleY = 1.08f
                            translationX = -x * 6.dp.toPx()
                            translationY = -y * 4.dp.toPx()
                        },
                )
            }
            // Creux du passe-partout + reflet sur le verre
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            Brush.verticalGradient(
                                0f to GlimmerColors.Ink.copy(alpha = 0.32f),
                                1f to Color.Transparent,
                                endY = 10.dp.toPx(),
                            ),
                        )
                        drawRect(GlimmerColors.Ink.copy(alpha = 0.10f), style = Stroke(1.dp.toPx()))
                        val x = if (still) 0f else tilt.x
                        val center = size.width * (0.28f + 0.45f * x)
                        val half = size.width * 0.18f
                        val dir = Offset(0.985f, 0.174f) // gradient à 100°, comme sur la maquette
                        drawRect(
                            Brush.linearGradient(
                                0f to Color.Transparent,
                                0.5f to Color.White.copy(alpha = 0.26f),
                                1f to Color.Transparent,
                                start = Offset(center, size.height / 2f) - dir * half,
                                end = Offset(center, size.height / 2f) + dir * half,
                            ),
                        )
                    },
            )
        }
    }
}
