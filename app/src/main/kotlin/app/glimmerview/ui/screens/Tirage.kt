package app.glimmerview.ui.screens

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glimmerview.GenStage
import app.glimmerview.GlimmerViewModel
import app.glimmerview.Generation
import app.glimmerview.R
import app.glimmerview.ui.components.AccentTitle
import app.glimmerview.ui.components.Cartel
import app.glimmerview.ui.components.CircleButton
import app.glimmerview.ui.components.Hairline
import app.glimmerview.ui.components.SystemBarIcons
import app.glimmerview.ui.components.Tirette
import app.glimmerview.ui.theme.GlimmerColors
import app.glimmerview.ui.theme.GlimmerIcons
import app.glimmerview.ui.theme.GlimmerType
import kotlin.math.max
import kotlin.math.roundToInt

/** Le tirage : la photo passe du flou au net pendant que les courbes de profondeur se dessinent. */
@Composable
fun TirageScreen(vm: GlimmerViewModel) {
    SystemBarIcons()
    val generation = vm.generation ?: return
    val stage = generation.stage
    val target = when (stage) {
        GenStage.PREPARE -> 3f
        GenStage.DOWNLOAD -> 3f + 22f * generation.downloadFraction
        GenStage.DEPTH -> if (generation.firstRun) 62f else 58f
        GenStage.CONTOURS -> 82f
        GenStage.FINISH -> 95f
        GenStage.DONE -> 100f
        GenStage.FAILED -> 0f
    }
    val percent by animateFloatAsState(target, tween(if (stage == GenStage.DEPTH) 1600 else 700, easing = FastOutSlowInEasing), label = "percent")
    val done = stage == GenStage.DONE
    val failed = stage == GenStage.FAILED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GlimmerColors.Plaster)
            .systemBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleButton(GlimmerIcons.Close, stringResource(R.string.tirage_cancel), onClick = { vm.back() })
            Spacer(Modifier.width(12.dp))
            Cartel(
                stringResource(
                    when {
                        failed -> R.string.tirage_kicker_failed
                        done -> R.string.tirage_kicker_done
                        else -> R.string.tirage_kicker
                    },
                ),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${percent.roundToInt()}",
                style = GlimmerType.title.copy(fontSize = 34.sp, lineHeight = 36.sp),
            )
            Text("%", style = GlimmerType.title.copy(fontSize = 22.sp, fontStyle = FontStyle.Italic))
        }

        Print(generation, percent, stringResource(R.string.tirage_print_description), Modifier.weight(1f).fillMaxWidth())

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AccentTitle(
                stringResource(
                    when {
                        failed -> R.string.tirage_title_failed
                        done -> R.string.tirage_title_done
                        else -> R.string.tirage_title
                    },
                ),
                style = GlimmerType.title,
            )
            Text(
                text = when {
                    failed -> generation.error ?: ""
                    stage == GenStage.DOWNLOAD -> stringResource(R.string.tirage_download, (generation.downloadFraction * 100).roundToInt())
                    else -> stringResource(R.string.tirage_privacy)
                },
                style = GlimmerType.bodySmall.copy(fontSize = 13.5.sp),
                color = GlimmerColors.Taupe,
            )
        }

        Column {
            Step("I", stringResource(R.string.tirage_step_depth), stage, from = GenStage.DEPTH, to = GenStage.CONTOURS, generation)
            Step("II", stringResource(R.string.tirage_step_contours), stage, from = GenStage.CONTOURS, to = GenStage.FINISH, generation)
            Step("III", stringResource(R.string.tirage_step_finish), stage, from = GenStage.FINISH, to = GenStage.DONE, generation)
        }

        if (failed) {
            Tirette(stringResource(R.string.tirage_retry), onClick = { vm.back() })
        } else {
            Tirette(stringResource(R.string.tirage_open), onClick = vm::openCreation, enabled = done)
        }
    }
}

/** Le tirage sous passe-partout : flou → net, puis les isolignes tracées d'un trait. */
@Composable
private fun Print(generation: Generation, percent: Float, description: String, modifier: Modifier) {
    val sharpness = ((percent - 25f) / 70f).coerceIn(0f, 1f)
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(generation.contours.isNotEmpty()) {
        if (generation.contours.isNotEmpty()) reveal.animateTo(1f, tween(2200, easing = LinearEasing))
    }
    Box(
        modifier = modifier
            .shadow(16.dp, RoundedCornerShape(6.dp), ambientColor = GlimmerColors.Ink, spotColor = GlimmerColors.Ink)
            .background(GlimmerColors.Mat, RoundedCornerShape(6.dp))
            .border(1.dp, GlimmerColors.FrameEdge, RoundedCornerShape(6.dp))
            .padding(12.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(GlimmerColors.Linen)
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(percent / 100f, 0f..1f)
            },
    ) {
        val photo = generation.photo
        val blurred = generation.blurred
        if (photo != null) {
            if (Build.VERSION.SDK_INT >= 31) {
                Image(
                    bitmap = photo,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur((14f * (1f - sharpness)).dp),
                )
            } else {
                if (blurred != null) {
                    Image(blurred, null, contentScale = ContentScale.Crop, filterQuality = FilterQuality.High, modifier = Modifier.fillMaxSize())
                }
                Image(photo, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(sharpness))
            }
            ContourLines(photo, generation.contours, reveal.value)
        }
    }
}

@Composable
private fun ContourLines(photo: ImageBitmap, contours: List<FloatArray>, reveal: Float) {
    if (contours.isEmpty()) return
    Canvas(Modifier.fillMaxSize()) {
        // Même recadrage que ContentScale.Crop
        val scale = max(size.width / photo.width, size.height / photo.height)
        val dx = (size.width - photo.width * scale) / 2f
        val dy = (size.height - photo.height * scale) / 2f
        val w = photo.width * scale
        val h = photo.height * scale
        val under = 2.6.dp.toPx()
        val over = 1.2.dp.toPx()
        contours.forEachIndexed { level, segments ->
            val local = ((reveal - level * 0.12f) / 0.5f).coerceIn(0f, 1f)
            val count = (segments.size / 4 * local).toInt()
            for (pass in 0..1) {
                for (s in 0 until count) {
                    val i = s * 4
                    val a = Offset(dx + segments[i] * w, dy + segments[i + 1] * h)
                    val b = Offset(dx + segments[i + 2] * w, dy + segments[i + 3] * h)
                    if (pass == 0) {
                        drawLine(GlimmerColors.Prussian.copy(alpha = 0.75f), a, b, under, StrokeCap.Round)
                    } else {
                        drawLine(GlimmerColors.Plaster.copy(alpha = 0.9f), a, b, over, StrokeCap.Round)
                    }
                }
            }
        }
    }
}

@Composable
private fun Step(number: String, label: String, stage: GenStage, from: GenStage, to: GenStage, generation: Generation) {
    val order = stage.ordinal
    val isDone = order >= to.ordinal && stage != GenStage.FAILED
    val active = order >= from.ordinal && !isDone && stage != GenStage.FAILED
    val ink = if (isDone || active) GlimmerColors.Ink else GlimmerColors.Taupe
    Hairline()
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(number, style = GlimmerType.italic.copy(fontSize = 16.sp), color = ink, modifier = Modifier.width(30.dp))
        Text(label, style = GlimmerType.body.copy(fontSize = 14.5.sp), color = ink, modifier = Modifier.weight(1f))
        Cartel(
            text = stringResource(
                when {
                    isDone -> R.string.tirage_status_done
                    active -> R.string.tirage_status_active
                    else -> R.string.tirage_status_pending
                },
            ),
            color = when {
                isDone -> GlimmerColors.Ink
                active -> GlimmerColors.Prussian
                else -> GlimmerColors.Taupe
            },
            fontSize = 10.sp,
        )
    }
    // Le premier tirage passe d'abord par le téléchargement du modèle.
    if (number == "I" && stage == GenStage.DOWNLOAD && generation.firstRun) {
        Text(
            stringResource(R.string.tirage_download_hint),
            style = GlimmerType.caption,
            color = GlimmerColors.Prussian,
            modifier = Modifier.padding(start = 30.dp, bottom = 8.dp),
        )
    }
}
