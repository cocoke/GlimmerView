package app.glimmerview.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glimmerview.GlimmerViewModel
import app.glimmerview.R
import app.glimmerview.WorkRef
import app.glimmerview.data.Room
import app.glimmerview.data.Exhibit
import app.glimmerview.ui.components.AccentTitle
import app.glimmerview.ui.components.Cartel
import app.glimmerview.ui.components.CircleButton
import app.glimmerview.ui.components.Hairline
import app.glimmerview.ui.components.InlineLink
import app.glimmerview.ui.components.LivingFrame
import app.glimmerview.ui.components.TextTabs
import app.glimmerview.ui.components.Wordmark
import app.glimmerview.ui.theme.GlimmerColors
import app.glimmerview.ui.theme.GlimmerIcons
import app.glimmerview.ui.theme.GlimmerType

/** La galerie : une œuvre à l'affiche et les acquisitions, salle par salle. */
@Composable
fun GalerieRoom(vm: GlimmerViewModel) {
    val rooms = listOf(stringResource(R.string.room_all)) + Room.entries.map { stringResource(it.label) }
    val shown = if (vm.room == 0) vm.exhibits else vm.exhibits.filter { it.room == Room.entries[vm.room - 1] }
    val featured = shown.firstOrNull()
    val others = shown.drop(1)

    RoomPage {
        Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
            Wordmark(fontSize = 21.sp)
            Spacer(Modifier.weight(1f))
            CircleButton(
                icon = GlimmerIcons.Collection,
                contentDescription = stringResource(R.string.tab_collection),
                onClick = { vm.selectTab(app.glimmerview.Route.Collection) },
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Cartel(stringResource(R.string.galerie_kicker))
            AccentTitle(stringResource(R.string.galerie_title))
        }
        TextTabs(labels = rooms, selected = vm.room, onSelect = { vm.room = it })

        if (featured != null) Featured(vm, featured)

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cartel(stringResource(R.string.galerie_acquisitions), color = GlimmerColors.Ink)
            Spacer(Modifier.weight(1f))
            Text(
                pluralStringResource(R.plurals.works_count, shown.size, shown.size),
                style = GlimmerType.caption,
                color = GlimmerColors.Taupe,
            )
        }
        Hairline()
        if (others.isEmpty()) {
            Text(stringResource(R.string.galerie_room_empty), style = GlimmerType.bodySmall, color = GlimmerColors.Taupe)
        }
        others.chunked(2).forEachIndexed { row, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                pair.forEachIndexed { column, exhibit ->
                    Acquisition(vm, exhibit, height = if ((row + column) % 2 == 0) 190 else 214, modifier = Modifier.weight(1f))
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Featured(vm: GlimmerViewModel, exhibit: Exhibit) {
    val card = vm.galleryCard(exhibit)
    val ref = WorkRef.Gallery(exhibit.id)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        val frameWidth = maxWidth * 0.58f
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
            LivingFrame(
                image = vm.frameImage(ref),
                mat = 13.dp,
                contentDescription = card.title,
                onClick = { vm.openWork(ref) },
                onLongClick = { vm.showActions(ref) },
                onLongClickLabel = stringResource(R.string.action_more),
                modifier = Modifier.size(frameWidth, frameWidth * (274f / 214f)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Hairline()
                Spacer(Modifier.height(8.dp))
                Cartel(stringResource(R.string.galerie_featured), color = GlimmerColors.Prussian, fontSize = 10.sp)
                Text(card.artist, style = GlimmerType.strong.copy(fontSize = 12.5.sp))
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(card.title) }
                        card.year?.let { append(", $it") }
                    },
                    style = GlimmerType.italic.copy(fontSize = 17.sp, fontStyle = FontStyle.Normal),
                )
                Text("${card.medium} · ${card.license}", style = GlimmerType.caption, color = GlimmerColors.Taupe)
                Spacer(Modifier.height(6.dp))
                InlineLink(
                    text = stringResource(R.string.galerie_see_work),
                    onClick = { vm.openWork(ref) },
                    style = GlimmerType.italic,
                )
            }
        }
    }
}

@Composable
private fun Acquisition(vm: GlimmerViewModel, exhibit: Exhibit, height: Int, modifier: Modifier = Modifier) {
    val card = vm.galleryCard(exhibit)
    val ref = WorkRef.Gallery(exhibit.id)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LivingFrame(
            image = vm.frameImage(ref),
            mat = 10.dp,
            elevation = 10.dp,
            contentDescription = card.title,
            onClick = { vm.openWork(ref) },
            onLongClick = { vm.showActions(ref) },
            onLongClickLabel = stringResource(R.string.action_more),
            modifier = Modifier.fillMaxWidth().height(height.dp),
        )
        Box {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, fontSize = 15.sp, color = GlimmerColors.Ink)) {
                        append(card.title)
                    }
                    append(" · ")
                    append(stringResource(exhibit.room.label).lowercase())
                },
                style = GlimmerType.caption,
                color = GlimmerColors.Taupe,
            )
        }
    }
}
