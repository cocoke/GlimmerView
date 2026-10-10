package app.glimmerview.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glimmerview.GlimmerViewModel
import app.glimmerview.R
import app.glimmerview.WorkCard
import app.glimmerview.WorkRef
import app.glimmerview.core.WorkOrigin
import app.glimmerview.ui.components.AccentTitle
import app.glimmerview.ui.components.Cartel
import app.glimmerview.ui.components.CircleButton
import app.glimmerview.ui.components.GlassChip
import app.glimmerview.ui.components.Hairline
import app.glimmerview.ui.components.ParallaxPreview
import app.glimmerview.ui.components.SystemBarIcons
import app.glimmerview.ui.components.Tirette
import app.glimmerview.ui.theme.GlimmerColors
import app.glimmerview.ui.theme.GlimmerIcons
import app.glimmerview.ui.theme.GlimmerType

/** Une œuvre en grand : l'aperçu 3D réagit au téléphone, le cartel en dessous. */
@Composable
fun OeuvreScreen(vm: GlimmerViewModel, ref: WorkRef) {
    SystemBarIcons()
    val card = vm.card(ref) ?: return
    var showPlanes by rememberSaveable { mutableStateOf(false) }
    var sheetHeight by remember { mutableIntStateOf(0) }
    val loaded = vm.preview?.takeIf { it.ref == ref }

    Box(Modifier.fillMaxSize().background(GlimmerColors.Linen)) {
        // L'œuvre occupe tout l'écran au-dessus du cartel, qu'elle déborde de 30 dp sous l'arrondi.
        ArtworkStage(vm = vm, ref = ref, depthView = showPlanes, fillAbove = sheetHeight)
        TopScrim()
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleButton(GlimmerIcons.Back, stringResource(R.string.action_back), onClick = { vm.back() }, glass = true)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircleButton(
                    icon = GlimmerIcons.Share,
                    contentDescription = stringResource(R.string.action_export),
                    onClick = { vm.startExport(ref) },
                    glass = true,
                )
                when {
                    ref is WorkRef.Gallery -> {
                        val collected = card.origin != null
                        CircleButton(
                            icon = if (collected) GlimmerIcons.Check else GlimmerIcons.Heart,
                            contentDescription = stringResource(if (collected) R.string.action_uncollect else R.string.action_collect),
                            onClick = { vm.toggleCollected(ref) },
                            glass = true,
                        )
                    }
                    // Une copie d'œuvre de la galerie se retire depuis la galerie ; le reste se supprime ici.
                    ref is WorkRef.Saved && card.origin != WorkOrigin.ACQUIRED -> {
                        CircleButton(
                            icon = GlimmerIcons.Trash,
                            contentDescription = stringResource(R.string.action_delete),
                            onClick = { vm.deleteWork(ref.id) },
                            glass = true,
                        )
                    }
                }
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
                .padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Label(card)
            Column {
                Hairline()
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    SheetAction(
                        icon = GlimmerIcons.Layers,
                        label = stringResource(if (showPlanes) R.string.oeuvre_recompose else R.string.oeuvre_planes),
                        underlined = showPlanes,
                        onClick = { showPlanes = !showPlanes },
                    )
                    SheetAction(
                        icon = GlimmerIcons.Dial,
                        label = stringResource(R.string.oeuvre_adjust),
                        onClick = { vm.adjust(ref) },
                    )
                }
                Hairline()
            }
            Tirette(stringResource(R.string.oeuvre_hang), onClick = { vm.hang(ref) }, enabled = loaded != null || ref is WorkRef.Saved)
        }

        val hint = stringResource(if (showPlanes) R.string.oeuvre_hint_planes else R.string.oeuvre_hint_tilt)
        if (sheetHeight > 0) {
            GlassChip(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp)
                    .offset { IntOffset(0, -(sheetHeight + 12.dp.roundToPx())) },
            ) {
                Cartel(hint, color = GlimmerColors.Ink, fontSize = 10.sp)
            }
        }
    }
}

/**
 * Scène 3D (ou vignette tant que l'œuvre se charge), au-dessus du cartel.
 * [fillAbove] : hauteur du cartel en pixels, que l'aperçu déborde de 30 dp.
 */
@Composable
fun ArtworkStage(
    vm: GlimmerViewModel,
    ref: WorkRef,
    depthView: Boolean,
    modifier: Modifier = Modifier,
    fillAbove: Int = 0,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val density = LocalDensity.current
    val loaded = vm.preview?.takeIf { it.ref == ref }
    val bottomInset = with(density) { (fillAbove.toDp() - 30.dp).coerceAtLeast(0.dp) }
    Box(modifier.fillMaxSize().padding(bottom = bottomInset)) {
        val thumb = vm.thumbnail(ref)
        if (thumb != null) {
            Image(thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (loaded != null) {
            ParallaxPreview(
                content = loaded.content,
                params = vm.previewParams,
                depthView = depthView,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (vm.previewError != null) {
            GlassChip(Modifier.align(Alignment.Center)) {
                Text(vm.previewError ?: "", style = GlimmerType.bodySmall)
            }
        }
        overlay()
    }
}

/** Voile clair sous la barre d'état, pour garder ses icônes lisibles sur une œuvre sombre. */
@Composable
fun TopScrim() {
    Box(
        Modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        0f to GlimmerColors.Plaster.copy(alpha = 0.7f),
                        1f to Color.Transparent,
                        endY = size.height * 1.6f,
                    ),
                    size = size.copy(height = size.height * 1.6f),
                )
            },
    )
}

@Composable
private fun Label(card: WorkCard) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(card.artist, style = GlimmerType.strong.copy(fontSize = 12.5.sp))
        AccentTitle(
            "*${card.title}*" + (card.year?.let { ", $it" } ?: ""),
            style = GlimmerType.headline,
        )
        Text("${card.medium} · ${card.license}", style = GlimmerType.bodySmall, color = GlimmerColors.Taupe)
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit, underlined: Boolean = false) {
    Row(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp), tint = GlimmerColors.Ink)
        Text(
            text = label,
            style = GlimmerType.body.copy(fontSize = 14.sp),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.drawBehind {
                if (underlined) drawLine(GlimmerColors.Ink, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            },
        )
        Spacer(Modifier.width(2.dp))
    }
}
