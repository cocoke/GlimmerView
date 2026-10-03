package app.glimmerview.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glimmerview.ui.theme.GlimmerColors
import app.glimmerview.ui.theme.GlimmerType

/** Le nom, « Glimmer » droit et « View » en italique. */
@Composable
fun Wordmark(modifier: Modifier = Modifier, fontSize: TextUnit = 22.sp) {
    Text(
        text = accentText("Glimmer*View*"),
        style = GlimmerType.title.copy(fontSize = fontSize, lineHeight = fontSize),
        modifier = modifier.clearAndSetSemantics { contentDescription = "GlimmerView" },
    )
}

/** L'emblème : trois lames de verre en perspective, comme les plans d'une image en profondeur. */
@Composable
fun PrismEmblem(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    val unit = size / 32f
    val blade = unit * 20
    Box(modifier.size(size)) {
        val tilt = Modifier.graphicsLayer {
            rotationY = -26f
            rotationX = 12f
            cameraDistance = 6f * density
        }
        Box(
            Modifier
                .offset(unit * 2, unit * 9)
                .size(blade)
                .then(tilt)
                .background(GlimmerColors.Prussian.copy(alpha = 0.88f), RoundedCornerShape(unit * 4)),
        )
        Box(
            Modifier
                .offset(unit * 6, unit * 6)
                .size(blade)
                .then(tilt)
                .background(GlimmerColors.Stone.copy(alpha = 0.9f), RoundedCornerShape(unit * 4)),
        )
        Box(
            Modifier
                .offset(unit * 10, unit * 3)
                .size(blade)
                .then(tilt)
                .background(
                    Brush.linearGradient(listOf(Color.White.copy(alpha = 0.95f), GlimmerColors.Plaster.copy(alpha = 0.6f))),
                    RoundedCornerShape(unit * 4),
                )
                .border(1.dp, GlimmerColors.Ink.copy(alpha = 0.2f), RoundedCornerShape(unit * 4)),
        )
    }
}
