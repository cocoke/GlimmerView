package app.glimmerview.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glimmerview.ui.theme.GlimmerColors
import app.glimmerview.ui.theme.GlimmerType
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Diaphragme : l'œuvre vue à travers un iris hexagonal qui s'ouvre avec l'intensité.
 * [aperture] va de 0 (fermé) à 1 (grand ouvert).
 */
@Composable
fun Diaphragm(image: ImageBitmap?, aperture: Float, modifier: Modifier = Modifier, diameter: Dp = 112.dp) {
    val a by animateFloatAsState(aperture, tween(600, easing = FastOutSlowInEasing), label = "iris")
    Canvas(
        modifier
            .size(diameter)
            .clip(CircleShape)
            .background(GlimmerColors.Ink),
    ) {
        val r = size.minDimension / 2f
        val hexRadius = size.minDimension * (0.15f + a * 0.26f)
        val rotation = Math.toRadians(10.0 + a * 40.0)
        val vertices = List(6) { k ->
            val angle = rotation + k * Math.PI / 3.0
            Offset(center.x + hexRadius * cos(angle).toFloat(), center.y + hexRadius * sin(angle).toFloat())
        }
        val hexagon = Path().apply {
            moveTo(vertices[0].x, vertices[0].y)
            for (k in 1 until 6) lineTo(vertices[k].x, vertices[k].y)
            close()
        }
        clipPath(hexagon) {
            if (image != null) drawImageCover(image) else drawRect(GlimmerColors.Linen)
        }
        // Arêtes des lamelles, de chaque sommet vers la bague
        vertices.forEachIndexed { k, v ->
            val b = rotation + k * Math.PI / 3.0 + Math.toRadians(62.0)
            val rim = Offset(center.x + r * cos(b).toFloat(), center.y + r * sin(b).toFloat())
            drawLine(GlimmerColors.Plaster.copy(alpha = 0.26f), v, rim, 1.dp.toPx())
        }
        drawCircle(GlimmerColors.Taupe, r - 0.5.dp.toPx(), style = Stroke(1.dp.toPx()))
    }
}

/** Dessine l'image en remplissant la zone (recadrage centré, comme ContentScale.Crop). */
fun DrawScope.drawImageCover(image: ImageBitmap) {
    val srcAspect = image.width.toFloat() / image.height
    val dstAspect = size.width / size.height
    val (srcW, srcH) = if (srcAspect > dstAspect) {
        (image.height * dstAspect).roundToInt() to image.height
    } else {
        image.width to (image.width / dstAspect).roundToInt()
    }
    drawImage(
        image = image,
        srcOffset = IntOffset((image.width - srcW) / 2, (image.height - srcH) / 2),
        srcSize = IntSize(srcW, srcH),
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
    )
}

/** Cinq pastilles d'ouverture, de la plus fermée à la plus ouverte. */
@Composable
fun ApertureDots(level: Int, onLevel: (Int) -> Unit, labels: List<String>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEachIndexed { index, label ->
            val on = index == level
            val dot by animateDpAsState(if (on) (10 + index * 4).dp else (8 + index * 4).dp, tween(300), label = "dot")
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.RadioButton, onClickLabel = label) { onLevel(index) }
                    .semantics {
                        selected = on
                        contentDescription = label
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(dot)
                        .clip(CircleShape)
                        .background(if (on) GlimmerColors.Prussian else Color.Transparent)
                        .border(1.dp, if (on) GlimmerColors.Prussian else GlimmerColors.Taupe, CircleShape),
                )
            }
        }
    }
}

/** Ouvertures en texte souligné (Préférences). */
@Composable
fun FStopTabs(level: Int, onLevel: (Int) -> Unit, labels: List<String>, descriptions: List<String>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEachIndexed { index, label ->
            val on = index == level
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .clickable(role = Role.RadioButton) { onLevel(index) }
                    .semantics {
                        selected = on
                        contentDescription = "$label, ${descriptions[index]}"
                    }
                    .drawBehind {
                        if (on) {
                            val y = size.height - 1.dp.toPx()
                            drawLine(GlimmerColors.Prussian, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx())
                        }
                    }
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = GlimmerType.numeral, color = if (on) GlimmerColors.Ink else GlimmerColors.Taupe)
            }
        }
    }
}

/**
 * Bague de mise au point : une règle crantée qu'on fait tourner (glisser ou toucher un repère).
 * Le repère sélectionné vient se placer sous l'index bleu de Prusse.
 */
@Composable
fun FocusRing(
    index: Int,
    labels: List<String>,
    descriptions: List<String>,
    onIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
    nearerLabel: String,
    fartherLabel: String,
) {
    val markWidth = 52.dp
    val position by animateFloatAsState(index.toFloat(), tween(500, easing = FastOutSlowInEasing), label = "ring")
    val currentIndex by rememberUpdatedState(index)
    val select by rememberUpdatedState(onIndex)
    var drag by remember { mutableFloatStateOf(0f) }
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(62.dp)
            .clip(shape)
            .background(GlimmerColors.Linen)
            .border(1.dp, GlimmerColors.Stone, shape)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(onDragEnd = { drag = 0f }, onDragCancel = { drag = 0f }) { change, dx ->
                    change.consume()
                    drag += dx
                    val step = markWidth.toPx() * 0.6f
                    if (drag <= -step && currentIndex < labels.lastIndex) {
                        select(currentIndex + 1)
                        drag = 0f
                    } else if (drag >= step && currentIndex > 0) {
                        select(currentIndex - 1)
                        drag = 0f
                    }
                }
            }
            .semantics {
                stateDescription = descriptions[index]
                customActions = listOf(
                    CustomAccessibilityAction(nearerLabel) {
                        if (currentIndex < labels.lastIndex) select(currentIndex + 1)
                        true
                    },
                    CustomAccessibilityAction(fartherLabel) {
                        if (currentIndex > 0) select(currentIndex - 1)
                        true
                    },
                )
            },
    ) {
        // Moletage
        Canvas(Modifier.fillMaxWidth().height(16.dp)) {
            val step = 6.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(GlimmerColors.Stone, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                x += step
            }
        }
        // Repères, décalés pour centrer la sélection sous l'index
        Row(
            Modifier
                .padding(top = 18.dp)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
                    val mw = markWidth.toPx()
                    layout(constraints.maxWidth, placeable.height) {
                        val x = constraints.maxWidth / 2f - (position * mw + mw / 2f)
                        placeable.place(x.roundToInt(), 0)
                    }
                },
        ) {
            labels.forEachIndexed { i, label ->
                val on = i == index
                Column(
                    modifier = Modifier
                        .width(markWidth)
                        .height(44.dp)
                        .clickable(role = Role.Button, onClickLabel = descriptions[i]) { onIndex(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(1.dp, 6.dp).background(if (on) GlimmerColors.Ink else GlimmerColors.Taupe))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = label,
                        style = GlimmerType.numeral.copy(fontSize = 16.sp),
                        fontWeight = if (on) FontWeight.Normal else FontWeight.Light,
                        color = if (on) GlimmerColors.Ink else GlimmerColors.Taupe,
                    )
                }
            }
        }
        // Index
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            drawLine(GlimmerColors.Prussian, Offset(cx, 16.dp.toPx()), Offset(cx, size.height), 1.dp.toPx())
            val tri = Path().apply {
                moveTo(cx - 5.dp.toPx(), 10.dp.toPx())
                lineTo(cx + 5.dp.toPx(), 10.dp.toPx())
                lineTo(cx, 17.dp.toPx())
                close()
            }
            drawPath(tri, GlimmerColors.Prussian)
        }
    }
}
