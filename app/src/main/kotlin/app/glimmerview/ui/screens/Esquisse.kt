package app.glimmerview.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import app.glimmerview.GlimmerViewModel
import app.glimmerview.R
import app.glimmerview.SketchSession
import app.glimmerview.WorkRef
import app.glimmerview.data.PixelRect
import app.glimmerview.data.SketchTool
import app.glimmerview.ui.components.AccentTitle
import app.glimmerview.ui.components.Cartel
import app.glimmerview.ui.components.CircleButton
import app.glimmerview.ui.components.GlassChip
import app.glimmerview.ui.components.GlimmerSheet
import app.glimmerview.ui.components.Hairline
import app.glimmerview.ui.components.InkPill
import app.glimmerview.ui.components.InlineLink
import app.glimmerview.ui.components.SystemBarIcons
import app.glimmerview.ui.components.TextTabs
import app.glimmerview.ui.components.WireSwitch
import app.glimmerview.ui.theme.GlimmerColors
import app.glimmerview.ui.theme.GlimmerIcons
import app.glimmerview.ui.theme.GlimmerType
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Esquisse : retoucher la profondeur sur sa carte « cyanotype ».
 * Deux modes : peindre (pinceaux) ou toucher une zone (une personne, un objet) et l'avancer d'un geste.
 */
@Composable
fun EsquisseScreen(vm: GlimmerViewModel, ref: WorkRef.Saved) {
    SystemBarIcons()
    val session = vm.sketch?.takeIf { it.ref == ref }
    val canvas = remember(session) { session?.let(::SketchCanvas) }
    var zoneMode by rememberSaveable { mutableStateOf(false) }
    var tool by rememberSaveable { mutableStateOf(SketchTool.NEARER) }
    var brush by rememberSaveable { mutableIntStateOf(2) }
    var overlay by rememberSaveable { mutableStateOf(true) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var sheetHeight by remember { mutableIntStateOf(0) }
    var cursor by remember { mutableStateOf<Offset?>(null) }
    var tightness by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val context = LocalContext.current
    val zoneEmpty = stringResource(R.string.sketch_zone_empty)
    val zoneTiny = stringResource(R.string.sketch_zone_tiny)
    val canvasDescription = stringResource(R.string.sketch_canvas_description)

    // Lecture de la version : recompose quand la carte change (annuler, valider…).
    val version = canvas?.version ?: 0
    val edited = version >= 0 && canvas?.sketch?.edited == true
    val leave: () -> Unit = { if (edited) confirmDiscard = true else vm.back() }
    BackHandler(onBack = leave)

    Box(Modifier.fillMaxSize().background(GlimmerColors.Linen)) {
        // Scène : carte de profondeur (et photo en transparence) au-dessus du panneau.
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = with(density) { (sheetHeight.toDp() - 30.dp).coerceAtLeast(0.dp) }),
        ) {
            if (canvas == null) {
                GlassChip(Modifier.align(Alignment.Center)) {
                    Cartel(stringResource(R.string.sketch_loading), color = GlimmerColors.Ink)
                }
            } else {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(top = 64.dp, bottom = 40.dp)
                        .pointerInput(canvas, zoneMode, tool, brush) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val fit = fitRect(Size(size.width.toFloat(), size.height.toFloat()), canvas.sketch.width, canvas.sketch.height)
                                val start = fit.toImage(down.position) ?: return@awaitEachGesture
                                if (zoneMode) {
                                    if (waitForUpOrCancellation() == null) return@awaitEachGesture
                                    // Toucher de nouveau la zone la resserre ; ailleurs, on repart d’une zone large.
                                    val level = if (canvas.sketch.isSelected(start.x, start.y)) tightness + 1 else 0
                                    tightness = if (canvas.sketch.select(start.x, start.y, level)) level else 0
                                    if (canvas.sketch.selection == null) Toast.makeText(context, zoneTiny, Toast.LENGTH_SHORT).show()
                                    canvas.renderAll()
                                    return@awaitEachGesture
                                }
                                down.consume()
                                val radius = brushRadius(brush, canvas.sketch.width)
                                val target = canvas.sketch.valueAt(start.x, start.y)
                                canvas.sketch.checkpoint()
                                canvas.render(canvas.sketch.dab(start.x, start.y, radius, tool, target))
                                cursor = down.position
                                var last = start
                                drag(down.id) { change ->
                                    cursor = change.position
                                    fit.toImage(change.position)?.let { p ->
                                        canvas.render(canvas.sketch.stroke(last.x, last.y, p.x, p.y, radius, tool, target))
                                        last = p
                                    }
                                    change.consume()
                                }
                                cursor = null
                            }
                        }
                        .semantics { contentDescription = canvasDescription },
                ) {
                    // Lire la version abonne ce dessin aux retouches : il est refait à chaque changement.
                    if (canvas.version < 0) return@Canvas
                    val fit = fitRect(size, canvas.sketch.width, canvas.sketch.height)
                    val dstOffset = IntOffset(fit.left.roundToInt(), fit.top.roundToInt())
                    val dstSize = IntSize(
                        (canvas.sketch.width * fit.scale).roundToInt(),
                        (canvas.sketch.height * fit.scale).roundToInt(),
                    )
                    drawImage(canvas.image, dstOffset = dstOffset, dstSize = dstSize)
                    if (overlay) {
                        drawImage(
                            canvas.photo,
                            srcSize = IntSize(canvas.photo.width, canvas.photo.height),
                            dstOffset = dstOffset,
                            dstSize = dstSize,
                            alpha = 0.32f,
                        )
                    }
                    cursor?.let { at ->
                        val r = brushRadius(brush, canvas.sketch.width) * fit.scale
                        drawCircle(GlimmerColors.Ink, r, at, style = Stroke(1.dp.toPx()))
                        drawCircle(GlimmerColors.Plaster, r - 2.dp.toPx(), at, style = Stroke(1.dp.toPx()))
                    }
                }
                // Légende : du loin au près
                GlassChip(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 40.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Cartel(stringResource(R.string.sketch_far), color = GlimmerColors.Ink, fontSize = 9.5.sp)
                        Box(
                            Modifier
                                .size(70.dp, 8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Brush.horizontalGradient(listOf(GlimmerColors.CyanoFar, GlimmerColors.CyanoMid, GlimmerColors.CyanoNear))),
                        )
                        Cartel(stringResource(R.string.sketch_near), color = GlimmerColors.Ink, fontSize = 9.5.sp)
                    }
                }
            }
        }

        // Barre du haut : retour, annuler, rétablir, valider
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircleButton(GlimmerIcons.Back, stringResource(R.string.action_back), onClick = leave, glass = true)
            Spacer(Modifier.weight(1f))
            CircleButton(
                GlimmerIcons.Undo,
                stringResource(R.string.sketch_undo),
                onClick = { if (canvas?.sketch?.undo() == true) canvas.renderAll() },
                glass = true,
                enabled = version >= 0 && canvas?.sketch?.canUndo == true,
            )
            CircleButton(
                GlimmerIcons.Redo,
                stringResource(R.string.sketch_redo),
                onClick = { if (canvas?.sketch?.redo() == true) canvas.renderAll() },
                glass = true,
                enabled = version >= 0 && canvas?.sketch?.canRedo == true,
            )
            Spacer(Modifier.weight(1f))
            InkPill(stringResource(R.string.sketch_save), onClick = vm::saveSketch, enabled = edited)
        }

        // Panneau des outils
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { sheetHeight = it.height }
                .shadow(24.dp, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp), ambientColor = GlimmerColors.Ink, spotColor = GlimmerColors.Ink)
                .background(GlimmerColors.Plaster, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Cartel(stringResource(R.string.sketch_kicker))
                AccentTitle(stringResource(R.string.sketch_title), style = GlimmerType.title.copy(fontSize = 26.sp, lineHeight = 28.sp))
            }
            TextTabs(
                labels = listOf(stringResource(R.string.sketch_mode_paint), stringResource(R.string.sketch_mode_zone)),
                selected = if (zoneMode) 1 else 0,
                onSelect = { index ->
                    zoneMode = index == 1
                    if (!zoneMode && canvas?.sketch?.selection != null) {
                        canvas.sketch.clearSelection()
                        canvas.renderAll()
                    }
                },
            )
            if (zoneMode) {
                Text(stringResource(R.string.sketch_zone_hint), style = GlimmerType.bodySmall, color = GlimmerColors.Taupe)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for ((key, icon, label) in sketchTools()) {
                    ToolButton(
                        icon = icon,
                        label = label,
                        selected = !zoneMode && tool == key,
                        onClick = {
                            if (!zoneMode) {
                                tool = key
                            } else {
                                val rect = canvas?.sketch?.applyToSelection(key)
                                if (rect == null) Toast.makeText(context, zoneEmpty, Toast.LENGTH_SHORT).show() else canvas.render(rect)
                            }
                        },
                    )
                }
            }
            if (!zoneMode) {
                Column {
                    Hairline()
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Cartel(stringResource(R.string.sketch_brush), modifier = Modifier.weight(1f))
                        for (level in 0 until BRUSH_LEVELS) {
                            val on = level == brush
                            val description = stringResource(R.string.sketch_brush_size, level + 1, BRUSH_LEVELS)
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .clickable(role = Role.RadioButton, onClickLabel = description) { brush = level }
                                    .semantics {
                                        selected = on
                                        contentDescription = description
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    Modifier
                                        .size((12 + level * 5.5f).dp)
                                        .clip(CircleShape)
                                        .background(if (on) GlimmerColors.Prussian.copy(alpha = 0.14f) else Color.Transparent)
                                        .border(1.dp, if (on) GlimmerColors.Prussian else GlimmerColors.Taupe, CircleShape),
                                )
                            }
                        }
                    }
                }
            }
            Column {
                Hairline()
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.sketch_overlay), style = GlimmerType.body)
                        Text(stringResource(R.string.sketch_overlay_sub), style = GlimmerType.caption.copy(fontSize = 12.5.sp), color = GlimmerColors.Taupe)
                    }
                    WireSwitch(overlay, { overlay = it }, contentDescription = stringResource(R.string.sketch_overlay))
                }
            }
        }
    }

    if (confirmDiscard) {
        GlimmerSheet(onDismiss = { confirmDiscard = false }) { close ->
            AccentTitle(stringResource(R.string.sketch_discard_title), style = GlimmerType.title.copy(fontSize = 24.sp, lineHeight = 28.sp))
            Text(stringResource(R.string.sketch_discard_body), style = GlimmerType.bodySmall, color = GlimmerColors.Taupe)
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InlineLink(stringResource(R.string.sketch_keep), onClick = close, lineColor = GlimmerColors.Stone)
                InkPill(stringResource(R.string.sketch_discard), onClick = {
                    confirmDiscard = false
                    vm.discardSketch()
                })
            }
        }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(60.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(if (selected) GlimmerColors.Ink else Color.Transparent)
                .border(1.dp, GlimmerColors.Ink, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) GlimmerColors.Plaster else GlimmerColors.Ink, modifier = Modifier.size(20.dp))
        }
        Cartel(label, color = if (selected) GlimmerColors.Ink else GlimmerColors.Taupe, fontSize = 9.sp)
    }
}

@Composable
private fun sketchTools(): List<Triple<SketchTool, ImageVector, String>> = listOf(
    Triple(SketchTool.NEARER, GlimmerIcons.Plus, stringResource(R.string.sketch_tool_nearer)),
    Triple(SketchTool.FARTHER, GlimmerIcons.Minus, stringResource(R.string.sketch_tool_farther)),
    Triple(SketchTool.SMOOTH, GlimmerIcons.Wave, stringResource(R.string.sketch_tool_smooth)),
    Triple(SketchTool.FLATTEN, GlimmerIcons.Flat, stringResource(R.string.sketch_tool_flatten)),
    Triple(SketchTool.ERASE, GlimmerIcons.Eraser, stringResource(R.string.sketch_tool_erase)),
)

private const val BRUSH_LEVELS = 5

/** Rayon du pinceau en pixels de la carte, selon le niveau choisi (fraction de la largeur). */
private fun brushRadius(level: Int, width: Int): Float =
    floatArrayOf(0.03f, 0.045f, 0.065f, 0.09f, 0.13f)[level.coerceIn(0, BRUSH_LEVELS - 1)] * width

/** Placement de l'image entière dans la zone (comme ContentScale.Fit). */
private class FitRect(val left: Float, val top: Float, val scale: Float, private val width: Int, private val height: Int) {
    fun toImage(p: Offset): Offset? {
        val x = (p.x - left) / scale
        val y = (p.y - top) / scale
        return if (x < 0f || y < 0f || x >= width || y >= height) null else Offset(x, y)
    }
}

private fun fitRect(area: Size, width: Int, height: Int): FitRect {
    val scale = min(area.width / width, area.height / height)
    return FitRect((area.width - width * scale) / 2f, (area.height - height * scale) / 2f, scale, width, height)
}

/** Rendu « cyanotype » de la carte en cours, mis à jour par zones après chaque retouche. */
@Stable
private class SketchCanvas(session: SketchSession) {
    val sketch = session.sketch
    private val bitmap: Bitmap = createBitmap(sketch.width, sketch.height)
    val image: ImageBitmap = bitmap.asImageBitmap()
    val photo: ImageBitmap = session.color.asImageBitmap()
    var version by mutableIntStateOf(0)
        private set
    private val pixels = IntArray(sketch.width * sketch.height)

    init {
        renderAll()
    }

    fun renderAll() = render(sketch.bounds)

    fun render(rect: PixelRect) {
        if (rect.isEmpty) return
        val w = sketch.width
        val selection = sketch.selection
        for (j in rect.top until rect.bottom) {
            for (i in rect.left until rect.right) {
                val k = j * w + i
                val d = sketch.depth[k].coerceIn(0f, 1f)
                var color = CYANO[(d * 255f + 0.5f).toInt()]
                if (selection != null) {
                    val s = selection[k]
                    color = when {
                        s > 0.15f && s < 0.85f -> if (d > 0.55f) PRUSSIAN else MAT // contour, contrasté selon le fond
                        s > 0f -> mix(color, MAT, 0.35f * s)
                        else -> color
                    }
                }
                pixels[k] = color
            }
        }
        bitmap.setPixels(pixels, rect.top * w + rect.left, w, rect.left, rect.top, rect.right - rect.left, rect.bottom - rect.top)
        version++
    }

    private companion object {
        val MAT = argb(0xFB, 0xFA, 0xF6)
        val PRUSSIAN = argb(0x1F, 0x3A, 0x5F)

        /** Rampe cyanotype de la charte : bleu de Prusse au loin, papier au premier plan. */
        val CYANO = IntArray(256) { i ->
            val t = i / 255f
            if (t < 0.5f) mix(argb(0x14, 0x24, 0x3B), argb(0x3D, 0x5A, 0x80), t * 2f)
            else mix(argb(0x3D, 0x5A, 0x80), argb(0xED, 0xEA, 0xE2), (t - 0.5f) * 2f)
        }

        fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

        fun mix(a: Int, b: Int, t: Float): Int {
            fun channel(shift: Int): Int {
                val ca = (a shr shift) and 0xFF
                val cb = (b shr shift) and 0xFF
                return (ca + (cb - ca) * t).roundToInt().coerceIn(0, 255)
            }
            return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }
    }
}
