package app.glimmerview.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.glimmerview.ui.theme.GlimmerColors

/**
 * Interrupteur « à fil » : un fil de 2 dp et une perle qui glisse.
 * Allumé, la perle se remplit d'encre et s'entoure d'un halo bleu de Prusse.
 */
@Composable
fun WireSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val t by animateFloatAsState(if (checked) 1f else 0f, tween(300), label = "wire")
    Canvas(
        modifier = modifier
            .size(56.dp, 44.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .semantics { this.contentDescription = contentDescription },
    ) {
        val inset = 6.dp.toPx()
        val radius = 7.dp.toPx()
        val cy = size.height / 2f
        drawLine(
            color = lerp(GlimmerColors.Stone, GlimmerColors.Ink, t),
            start = Offset(inset, cy),
            end = Offset(size.width - inset, cy),
            strokeWidth = 2.dp.toPx(),
        )
        val cx = inset + radius + (size.width - 2 * (inset + radius)) * t
        if (t > 0f) drawCircle(GlimmerColors.Prussian.copy(alpha = 0.16f * t), radius + 5.dp.toPx(), Offset(cx, cy))
        drawCircle(lerp(GlimmerColors.Plaster, GlimmerColors.Ink, t), radius, Offset(cx, cy))
        drawCircle(GlimmerColors.Ink, radius - 0.75.dp.toPx(), Offset(cx, cy), style = Stroke(1.5.dp.toPx()))
    }
}
