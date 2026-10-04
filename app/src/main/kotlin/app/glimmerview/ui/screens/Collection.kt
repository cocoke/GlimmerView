package app.glimmerview.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glimmerview.GlimmerViewModel
import app.glimmerview.R
import app.glimmerview.Route
import app.glimmerview.WorkRef
import app.glimmerview.core.WorkInfo
import app.glimmerview.core.WorkOrigin
import app.glimmerview.ui.components.AccentTitle
import app.glimmerview.ui.components.Cartel
import app.glimmerview.ui.components.CircleButton
import app.glimmerview.ui.components.Hairline
import app.glimmerview.ui.components.InlineLink
import app.glimmerview.ui.components.LivingFrame
import app.glimmerview.ui.components.TextTabs
import app.glimmerview.ui.components.Tirette
import app.glimmerview.ui.theme.GlimmerColors
import app.glimmerview.ui.theme.GlimmerIcons
import app.glimmerview.ui.theme.GlimmerType

/** Ma collection : les œuvres créées ou accrochées, en accrochage « salon ». */
@Composable
fun CollectionRoom(vm: GlimmerViewModel) {
    val tabs = listOf(
        stringResource(R.string.collection_all),
        stringResource(R.string.collection_created),
        stringResource(R.string.collection_acquired),
    )
    val shown = when (vm.collectionTab) {
        1 -> vm.works.filter { it.origin == WorkOrigin.CREATED }
        // Acquises : venues de la galerie ou reçues dans un fichier .dwp.
        2 -> vm.works.filter { it.origin != WorkOrigin.CREATED }
        else -> vm.works
    }
    val current = vm.works.firstOrNull { it.id == vm.currentWorkId }

    RoomPage(padding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 28.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Cartel(stringResource(R.string.collection_kicker))
                AccentTitle(stringResource(R.string.collection_title))
            }
            CircleButton(GlimmerIcons.Plus, stringResource(R.string.atelier_title_plain), onClick = { vm.selectTab(Route.Atelier) })
        }

        if (current != null) {
            Column {
                Hairline()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { vm.adjust(WorkRef.Saved(current.id)) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier
                            .size(16.dp)
                            .drawBehind {
                                drawCircle(GlimmerColors.Prussian.copy(alpha = 0.14f), radius = size.minDimension / 2f)
                                drawCircle(GlimmerColors.Prussian, radius = 4.dp.toPx())
                            },
                    )
                    Text(
                        text = buildAnnotatedString {
                            append(stringResource(R.string.collection_current))
                            append(" ")
                            withStyle(SpanStyle(fontStyle = FontStyle.Italic, fontSize = 15.sp)) { append(current.title) }
                        },
                        style = GlimmerType.body.copy(fontSize = 13.5.sp),
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                    )
                    Text(
                        stringResource(R.string.collection_adjust),
                        style = GlimmerType.bodySmall,
                        modifier = Modifier.drawBehind {
                            drawLine(
                                GlimmerColors.Ink,
                                androidx.compose.ui.geometry.Offset(0f, size.height),
                                androidx.compose.ui.geometry.Offset(size.width, size.height),
                                1.dp.toPx(),
                            )
                        },
                    )
                }
                Hairline()
            }
        }

        TextTabs(labels = tabs, selected = vm.collectionTab, onSelect = { vm.collectionTab = it }, withRule = false)

        if (shown.isEmpty()) {
            EmptyCollection(vm)
        } else {
            SalonWall(vm, shown)
        }
    }
}

/** Accrochage « salon » : deux colonnes de cadres de tailles variées. */
@Composable
private fun SalonWall(vm: GlimmerViewModel, works: List<WorkInfo>) {
    // Hauteurs relatives (largeur = 1) qui alternent portraits, paysages et petits formats.
    val ratios = floatArrayOf(1.34f, 0.72f, 1.28f, 1.1f, 0.64f, 1.36f, 0.9f, 1.2f)
    val left = works.filterIndexed { i, _ -> i % 2 == 0 }
    val right = works.filterIndexed { i, _ -> i % 2 == 1 }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            left.forEachIndexed { i, info -> SalonFrame(vm, info, ratios[(i * 2) % ratios.size]) }
        }
        Column(Modifier.weight(1f).padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            right.forEachIndexed { i, info -> SalonFrame(vm, info, ratios[(i * 2 + 1) % ratios.size]) }
        }
    }
}

@Composable
private fun SalonFrame(vm: GlimmerViewModel, info: WorkInfo, ratio: Float) {
    val ref = WorkRef.Saved(info.id)
    val isCurrent = info.id == vm.currentWorkId
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            LivingFrame(
                image = vm.frameImage(ref),
                mat = if (ratio < 0.8f) 8.dp else 11.dp,
                elevation = 10.dp,
                contentDescription = info.title,
                onClick = { vm.openWork(ref) },
                onLongClick = { vm.showActions(ref) },
                onLongClickLabel = stringResource(R.string.action_more),
                modifier = Modifier.fillMaxWidth().height(maxWidth * ratio),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (isCurrent) Box(Modifier.size(6.dp).background(GlimmerColors.Prussian, CircleShape))
            Text(
                text = info.title,
                style = GlimmerType.italic.copy(fontSize = 14.sp),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun EmptyCollection(vm: GlimmerViewModel) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.collection_empty), style = GlimmerType.body, color = GlimmerColors.Taupe)
        Spacer(Modifier.height(4.dp))
        Tirette(stringResource(R.string.collection_create), onClick = { vm.selectTab(Route.Atelier) })
        InlineLink(stringResource(R.string.collection_browse), onClick = { vm.selectTab(Route.Galerie) }, style = GlimmerType.italic)
    }
}
