package app.glimmerview.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glimmerview.ui.theme.GlimmerColors
import app.glimmerview.ui.theme.GlimmerIcons
import app.glimmerview.ui.theme.GlimmerType
import kotlinx.coroutines.launch

/**
 * « Maintenir pour accrocher » : un anneau bleu de Prusse se remplit pendant l'appui (1,3 s).
 * Relâcher trop tôt le vide. Avec TalkBack, un simple double appui suffit.
 */
@Composable
fun HoldToApply(
    label: String,
    holdingLabel: String,
    doneLabel: String,
    done: Boolean,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val complete by rememberUpdatedState(onComplete)
    var holding by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(31.dp)

    LaunchedEffect(done) {
        if (done) progress.snapTo(1f) else if (!holding) progress.animateTo(0f, tween(350))
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(62.dp)
            .clip(shape)
            .background(if (done) GlimmerColors.Ink else Color.Transparent)
            .border(1.dp, GlimmerColors.Ink, shape)
            .alpha(if (enabled || done) 1f else 0.4f)
            .pointerInput(done, enabled) {
                if (done || !enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        holding = true
                        val fill = scope.launch {
                            val remaining = ((1f - progress.value) * HOLD_MILLIS).toInt().coerceAtLeast(1)
                            progress.animateTo(1f, tween(remaining, easing = LinearEasing))
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            complete()
                        }
                        tryAwaitRelease()
                        holding = false
                        if (fill.isActive) {
                            fill.cancel()
                            scope.launch { progress.animateTo(0f, tween(350)) }
                        }
                    },
                )
            }
            .semantics {
                role = Role.Button
                contentDescription = if (done) doneLabel else label
                if (!done && enabled) {
                    onClick(label = label) {
                        complete()
                        true
                    }
                }
            }
            .padding(start = 22.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = when {
                done -> doneLabel
                holding -> holdingLabel
                else -> label
            },
            style = GlimmerType.body.copy(fontSize = 16.sp),
            color = if (done) GlimmerColors.Plaster else GlimmerColors.Ink,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(46.dp)) {
                val stroke = 1.5.dp.toPx()
                val radius = 19.dp.toPx()
                val topLeft = Offset(size.width / 2f - radius, size.height / 2f - radius)
                drawCircle(
                    color = if (done) GlimmerColors.Plaster.copy(alpha = 0.25f) else GlimmerColors.Stone,
                    radius = radius,
                    style = Stroke(stroke),
                )
                drawArc(
                    color = if (done) GlimmerColors.Plaster else GlimmerColors.Prussian,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.value,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            Icon(
                imageVector = if (done) GlimmerIcons.Check else GlimmerIcons.Lift,
                contentDescription = null,
                tint = if (done) GlimmerColors.Plaster else GlimmerColors.Ink,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private const val HOLD_MILLIS = 1300f
