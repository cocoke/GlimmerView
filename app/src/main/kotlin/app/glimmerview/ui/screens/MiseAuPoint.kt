package app.glimmerview.ui.screens

import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glimmerview.GlimmerViewModel
import app.glimmerview.R
import app.glimmerview.WorkRef
import app.glimmerview.core.ApertureScale
import app.glimmerview.core.FocusScale
import app.glimmerview.ui.components.ApertureDots
import app.glimmerview.ui.components.Cartel
import app.glimmerview.ui.components.CircleButton
import app.glimmerview.ui.components.Diaphragm
import app.glimmerview.ui.components.FocusRing
import app.glimmerview.ui.components.GlassChip
import app.glimmerview.ui.components.SystemBarIcons
import app.glimmerview.ui.components.Tirette
import app.glimmerview.ui.components.WireSwitch
import app.glimmerview.ui.theme.GlimmerColors
import app.glimmerview.ui.theme.GlimmerIcons
import app.glimmerview.ui.theme.GlimmerType
import kotlin.math.roundToInt

/**
 * Mise au point : régler l'effet comme on règle un objectif.
 * Le diaphragme dose l'intensité, la bague choisit le plan immobile, un toucher sur l'œuvre vise le sujet.
 */
@Composable
fun MiseAuPointScreen(vm: GlimmerViewModel, ref: WorkRef) {
    SystemBarIcons()
    val params = vm.previewParams
    val level = ApertureScale.level(params.strength)
    val focusIndex = FocusScale.index(params.focus)
    val words = apertureWords()
    val focusWordList = focusWords()
    val fLabels = apertureLabels()
    var cyanotype by rememberSaveable { mutableStateOf(false) }
    var sheetHeight by remember { mutableIntStateOf(0) }
    var stageSize by remember { mutableStateOf(IntSize.Zero) }
    var marker by remember { mutableStateOf<Offset?>(null) }

    Box(Modifier.fillMaxSize().background(GlimmerColors.Linen)) {
        ArtworkStage(vm = vm, ref = ref, depthView = cyanotype, fillAbove = sheetHeight) {
            // Toucher l'œuvre : le point touché devient le plan immobile.
            Box(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { stageSize = it }
                    .pointerInput(Unit) {
                        detectTapGestures { tap ->
                            marker = tap
                            vm.focusAt(tap.x / size.width, tap.y / size.height, size.width.toFloat() / size.height)
                        }
                    },
            )
            val target = marker ?: Offset(stageSize.width * 0.5f, stageSize.height * 0.48f)
            val animated by animateOffsetAsState(target, tween(350), label = "marker")
            // Le repère ne glisse que vers un point touché ; au repos, il reste au centre de l'œuvre.
            val position = if (marker == null) target else animated
            if (stageSize != IntSize.Zero) {
                FocusMarker(
                    Modifier.offset { IntOffset((position.x - 30.dp.toPx()).roundToInt(), (position.y - 30.dp.toPx()).roundToInt()) },
                )
                GlassChip(
                    Modifier.offset {
                        IntOffset((position.x - 70.dp.toPx()).roundToInt().coerceAtLeast(8.dp.roundToPx()), (position.y + 38.dp.toPx()).roundToInt())
                    },
                ) {
                    Cartel(stringResource(R.string.mise_still_plane, focusWordList[focusIndex]), color = GlimmerColors.Ink, fontSize = 10.sp)
                }
            }
        }
        TopScrim()

        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleButton(GlimmerIcons.Back, stringResource(R.string.action_back), onClick = { vm.back() }, glass = true)
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .height(44.dp)
                    .background(GlimmerColors.Plaster.copy(alpha = 0.86f), RoundedCornerShape(22.dp))
                    .border(1.dp, GlimmerColors.Ink.copy(alpha = 0.12f), RoundedCornerShape(22.dp))
                    .padding(start = 16.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cartel(stringResource(R.string.mise_cyanotype), color = GlimmerColors.Ink)
                WireSwitch(cyanotype, { cyanotype = it }, contentDescription = stringResource(R.string.mise_cyanotype_description))
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { sheetHeight = it.height }
                .shadow(24.dp, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp), ambientColor = GlimmerColors.Ink, spotColor = GlimmerColors.Ink)
                .background(GlimmerColors.Plaster, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Diaphragm(image = vm.thumbnail(ref), aperture = 0.15f + level * 0.2f)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Cartel(stringResource(R.string.mise_aperture))
                    Text(fLabels[level], style = GlimmerType.title.copy(fontSize = 34.sp, lineHeight = 36.sp))
                    Text(words[level], style = GlimmerType.bodySmall, color = GlimmerColors.Taupe)
                    ApertureDots(
                        level = level,
                        onLevel = { vm.updateParams(params.copy(strength = ApertureScale.strength(it))) },
                        labels = fLabels.mapIndexed { i, f -> "$f, ${words[i]}" },
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FocusRing(
                    index = focusIndex,
                    labels = focusLabels(),
                    descriptions = focusWordList,
                    onIndex = {
                        marker = null
                        vm.updateParams(params.copy(focus = FocusScale.focus(it)))
                    },
                    nearerLabel = stringResource(R.string.mise_nearer),
                    fartherLabel = stringResource(R.string.mise_farther),
                )
                Cartel(stringResource(R.string.mise_focus, focusWordList[focusIndex]))
            }
            Tirette(stringResource(R.string.mise_hang), onClick = { vm.hang(ref) })
        }
    }
}

/** Repère de mise au point : un cercle de verre et un point bleu de Prusse. */
@Composable
private fun FocusMarker(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(60.dp)
            .border(1.dp, GlimmerColors.Plaster, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(34.dp).border(1.dp, GlimmerColors.Ink, CircleShape), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(GlimmerColors.Plaster, CircleShape)
                    .padding(2.dp)
                    .background(GlimmerColors.Prussian, CircleShape),
            )
        }
    }
}
