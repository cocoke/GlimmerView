package app.glimmerview.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glimmerview.GlimmerViewModel
import app.glimmerview.R
import app.glimmerview.WorkRef
import app.glimmerview.core.WorkOrigin
import app.glimmerview.ui.components.Cartel
import app.glimmerview.ui.components.GlimmerSheet
import app.glimmerview.ui.components.Hairline
import app.glimmerview.ui.components.LivingFrame
import app.glimmerview.ui.theme.GlimmerColors
import app.glimmerview.ui.theme.GlimmerIcons
import app.glimmerview.ui.theme.GlimmerType
import kotlinx.coroutines.delay

/**
 * Menu d'une œuvre, ouvert par un appui long sur son cadre : la voir, la régler, retoucher sa
 * profondeur, la renommer, l'accrocher, l'ajouter à la collection ou l'en retirer.
 * Les actions qui changent d'écran partent tout de suite ; celles qui agissent sur place
 * (renommer, ajouter, retirer, supprimer) attendent que le panneau soit refermé.
 */
@Composable
fun WorkActionsSheet(vm: GlimmerViewModel, ref: WorkRef) {
    val card = vm.card(ref) ?: return
    val savedId = (ref as? WorkRef.Saved)?.id
    var afterClose by remember { mutableStateOf<(() -> Unit)?>(null) }

    GlimmerSheet(
        onDismiss = {
            vm.hideActions()
            afterClose?.invoke()
        },
    ) { close ->
        val now = { action: () -> Unit ->
            action()
            close()
        }
        val later = { action: () -> Unit ->
            afterClose = action
            close()
        }

        Row(
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LivingFrame(
                image = vm.frameImage(ref),
                mat = 5.dp,
                radius = 4.dp,
                elevation = 6.dp,
                still = true,
                modifier = Modifier.size(60.dp, 78.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Cartel(
                        stringResource(
                            when (card.origin) {
                                null -> R.string.sheet_origin_gallery
                                WorkOrigin.CREATED -> R.string.sheet_origin_created
                                WorkOrigin.ACQUIRED -> R.string.sheet_origin_acquired
                            },
                        ),
                    )
                    if (vm.isOnDisplay(ref)) {
                        Box(Modifier.size(5.dp).background(GlimmerColors.Prussian, CircleShape))
                        Cartel(stringResource(R.string.sheet_on_display), color = GlimmerColors.Prussian)
                    }
                }
                Text(
                    card.title,
                    style = GlimmerType.italic.copy(fontSize = 20.sp, lineHeight = 25.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    card.medium,
                    style = GlimmerType.caption,
                    color = GlimmerColors.Taupe,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Défile si l'écran est trop bas (paysage) ; sinon tout tient d'un coup d'œil.
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            Hairline()
            ActionRow(GlimmerIcons.Eye, stringResource(R.string.galerie_see_work)) { now { vm.openWork(ref) } }
            ActionRow(GlimmerIcons.Dial, stringResource(R.string.oeuvre_adjust)) { now { vm.adjust(ref) } }
            ActionRow(GlimmerIcons.Brush, stringResource(R.string.mise_retouch)) { now { vm.retouch(ref) } }
            if (savedId != null) {
                ActionRow(GlimmerIcons.Pencil, stringResource(R.string.mise_rename)) { later { vm.startRename(savedId) } }
            }
            ActionRow(GlimmerIcons.Hang, stringResource(R.string.oeuvre_hang)) { now { vm.hang(ref) } }
            when {
                ref is WorkRef.Scene && card.origin == null ->
                    ActionRow(GlimmerIcons.Heart, stringResource(R.string.action_collect)) { later { vm.toggleCollected(ref) } }
                ref is WorkRef.Scene ->
                    ConfirmRow(
                        label = stringResource(R.string.action_uncollect),
                        confirmLabel = stringResource(R.string.sheet_confirm_remove),
                    ) { later { vm.toggleCollected(ref) } }
                savedId != null && card.origin == WorkOrigin.ACQUIRED ->
                    ConfirmRow(
                        label = stringResource(R.string.action_uncollect),
                        confirmLabel = stringResource(R.string.sheet_confirm_remove),
                    ) { later { vm.deleteWork(savedId, leave = false) } }
                savedId != null ->
                    ConfirmRow(
                        label = stringResource(R.string.action_delete),
                        confirmLabel = stringResource(R.string.sheet_confirm_delete),
                    ) { later { vm.deleteWork(savedId, leave = false) } }
            }
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, color: Color = GlimmerColors.Ink, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = color)
        Text(label, style = GlimmerType.body.copy(fontSize = 16.sp), color = color)
    }
    Hairline()
}

/** Action irréversible : un premier toucher l'arme (libellé en bleu de Prusse), le second la lance. */
@Composable
private fun ConfirmRow(label: String, confirmLabel: String, onConfirm: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(CONFIRM_WINDOW_MILLIS)
            armed = false
        }
    }
    ActionRow(
        icon = GlimmerIcons.Trash,
        label = if (armed) confirmLabel else label,
        color = if (armed) GlimmerColors.Prussian else GlimmerColors.Ink,
        onClick = { if (armed) onConfirm() else armed = true },
    )
}

private const val CONFIRM_WINDOW_MILLIS = 3_000L
