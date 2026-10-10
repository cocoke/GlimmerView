package app.glimmerview.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
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
 * « Maintenir pour accrocher » : pendant l'appui (1,3 s), le bleu de Prusse remplit tout le
 * bouton de gauche à droite, et le texte s'inverse à son passage : la progression reste
 * visible quel que soit l'endroit où se pose le doigt. Relâcher trop tôt vide le bouton.
 * Avec TalkBack, un simple double appui suffit.
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
    val fill by animateColorAsState(if (done) GlimmerColors.Ink else GlimmerColors.Prussian, tween(300), label = "fill")

    LaunchedEffect(done) {
        if (done) progress.snapTo(1f) else if (!holding) progress.animateTo(0f, tween(350))
    }

    val text = when {
        done -> doneLabel
        holding -> holdingLabel
        else -> label
    }

    Box(
        modifier = modifier
            .dimWhenDisabled(enabled || done)
            .fillMaxWidth()
            .height(62.dp)
            .clip(shape)
            .drawBehind {
                // Le remplissage, lu en phase de dessin : aucune recomposition pendant l'appui.
                drawRect(fill, size = size.copy(width = size.width * progress.value))
            }
            .border(1.dp, GlimmerColors.Ink, shape)
            .pointerInput(done, enabled) {
                if (done || !enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        holding = true
                        val run = scope.launch {
                            val remaining = ((1f - progress.value) * HOLD_MILLIS).toInt().coerceAtLeast(1)
                            progress.animateTo(1f, tween(remaining, easing = LinearEasing))
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            complete()
                        }
                        tryAwaitRelease()
                        holding = false
                        if (run.isActive) {
                            run.cancel()
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
            },
    ) {
        // Deux fois le même contenu : à l'encre sur le fond, et en plâtre sur la partie remplie.
        HoldContent(text, done, GlimmerColors.Ink)
        HoldContent(
            text = text,
            done = done,
            color = GlimmerColors.Plaster,
            modifier = Modifier.drawWithContent {
                clipRect(right = size.width * progress.value) { this@drawWithContent.drawContent() }
            },
        )
    }
}

@Composable
private fun HoldContent(text: String, done: Boolean, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxSize().padding(start = 22.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = GlimmerType.body.copy(fontSize = 16.sp),
            color = color,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Box(
            Modifier.size(46.dp).padding(4.dp).border(1.5.dp, color.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (done) GlimmerIcons.Check else GlimmerIcons.Lift,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private const val HOLD_MILLIS = 1300f
