package app.glimmerview.ui.screens

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glimmerview.ApplyState
import app.glimmerview.GlimmerViewModel
import app.glimmerview.Placement
import app.glimmerview.R
import app.glimmerview.Route
import app.glimmerview.WorkRef
import app.glimmerview.ui.components.AccentTitle
import app.glimmerview.ui.components.Cartel
import app.glimmerview.ui.components.Hairline
import app.glimmerview.ui.components.HoldToApply
import app.glimmerview.ui.components.InlineLink
import app.glimmerview.ui.components.SystemBarIcons
import app.glimmerview.ui.theme.GlimmerColors
import app.glimmerview.ui.theme.GlimmerType

/** Accrochage : choisir l'écran, puis maintenir pour accrocher. */
@Composable
fun AccrocherScreen(vm: GlimmerViewModel, ref: WorkRef) {
    SystemBarIcons(darkStatusBarIcons = false, darkNavigationBarIcons = true)
    var placement by rememberSaveable { mutableStateOf(Placement.BOTH) }
    val state = vm.applyState
    val thumb = vm.thumbnail(ref)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.onPickerClosed(placement)
    }
    LaunchedEffect(state) {
        if (state is ApplyState.NeedsPicker) {
            vm.onPickerLaunched(state.placement)
            try {
                picker.launch(vm.livePickerIntent())
            } catch (_: ActivityNotFoundException) {
                picker.launch(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            }
        }
    }

    Box(Modifier.fillMaxSize().background(GlimmerColors.Linen)) {
        if (thumb != null) {
            // Profondeur de champ : l'œuvre recule et se brouille derrière le panneau (flou dès Android 12).
            Image(
                bitmap = thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 0.94f
                        scaleY = 0.94f
                    }
                    .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(6.dp) else Modifier),
            )
        }
        Box(Modifier.fillMaxSize().background(GlimmerColors.Ink.copy(alpha = 0.28f)))

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .shadow(28.dp, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp), ambientColor = GlimmerColors.Ink, spotColor = GlimmerColors.Ink)
                .background(GlimmerColors.Plaster, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 4.dp).background(GlimmerColors.Stone, CircleShape))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Cartel(stringResource(R.string.apply_kicker))
                AccentTitle(stringResource(R.string.apply_title), style = GlimmerType.headline.copy(fontSize = 30.sp, lineHeight = 32.sp))
            }

            Column {
                Hairline()
                Row(
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PlacementOption(
                        thumb = thumb,
                        title = stringResource(R.string.apply_home),
                        subtitle = stringResource(R.string.apply_home_sub),
                        selected = placement == Placement.HOME,
                        clock = false,
                        icons = true,
                        onSelect = { placement = Placement.HOME },
                        modifier = Modifier.weight(1f),
                    )
                    PlacementOption(
                        thumb = thumb,
                        title = stringResource(R.string.apply_lock),
                        subtitle = stringResource(R.string.apply_lock_sub),
                        selected = placement == Placement.LOCK,
                        clock = true,
                        icons = false,
                        onSelect = { placement = Placement.LOCK },
                        modifier = Modifier.weight(1f),
                    )
                    PlacementOption(
                        thumb = thumb,
                        title = stringResource(R.string.apply_both),
                        subtitle = stringResource(R.string.apply_both_sub),
                        selected = placement == Placement.BOTH,
                        clock = true,
                        icons = true,
                        onSelect = { placement = Placement.BOTH },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Hairline()
            Text(stringResource(R.string.apply_note), style = GlimmerType.bodySmall, color = GlimmerColors.Taupe)

            val done = state is ApplyState.Done && state.placement == placement
            HoldToApply(
                label = stringResource(R.string.apply_hold),
                holdingLabel = stringResource(R.string.apply_holding),
                doneLabel = stringResource(
                    when (placement) {
                        Placement.HOME -> R.string.apply_done_home
                        Placement.LOCK -> R.string.apply_done_lock
                        Placement.BOTH -> R.string.apply_done_both
                    },
                ),
                done = done,
                enabled = state == ApplyState.Idle || state is ApplyState.Done,
                onComplete = { vm.apply(ref, placement) },
            )

            Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                InlineLink(
                    text = stringResource(if (done) R.string.action_close else R.string.action_cancel),
                    onClick = { vm.back() },
                    lineColor = GlimmerColors.Stone,
                )
                Spacer(Modifier.weight(1f))
                if (done) {
                    InlineLink(
                        text = stringResource(R.string.apply_see_collection),
                        onClick = { vm.selectTab(Route.Collection) },
                        style = GlimmerType.italic.copy(fontSize = 16.sp),
                    )
                }
            }
        }
    }
}

/** Un petit téléphone encadré, suspendu à son fil sur la cimaise. */
@Composable
private fun PlacementOption(
    thumb: ImageBitmap?,
    title: String,
    subtitle: String,
    selected: Boolean,
    clock: Boolean,
    icons: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lift by animateDpAsState(if (selected) (-6).dp else 0.dp, tween(400), label = "lift")
    val elevation by animateDpAsState(if (selected) 16.dp else 5.dp, tween(400), label = "elevation")
    Column(
        modifier = modifier
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.width(1.dp).height(14.dp).background(GlimmerColors.Stone))
        Box(
            Modifier
                .offset { IntOffset(0, (lift - 10.dp).roundToPx()) }
                .size(82.dp, 148.dp)
                .shadow(elevation, RoundedCornerShape(14.dp), ambientColor = GlimmerColors.Ink, spotColor = GlimmerColors.Ink)
                .background(GlimmerColors.Mat, RoundedCornerShape(14.dp))
                .border(1.dp, GlimmerColors.Stone, RoundedCornerShape(14.dp))
                .padding(4.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(GlimmerColors.Linen),
        ) {
            if (thumb != null) {
                Image(thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            if (clock) {
                Text(
                    text = "09:41",
                    style = GlimmerType.title.copy(fontSize = 19.sp),
                    color = GlimmerColors.Ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
            if (icons) {
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(horizontal = 9.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    repeat(4) {
                        Box(
                            Modifier
                                .weight(1f)
                                .height(10.dp)
                                .background(GlimmerColors.Mat.copy(alpha = 0.8f), RoundedCornerShape(3.dp)),
                        )
                    }
                }
            }
        }
        Column(
            modifier = Modifier.offset(y = (-10).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = GlimmerType.body.copy(fontSize = 13.5.sp), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            Text(subtitle, style = GlimmerType.caption.copy(fontSize = 11.5.sp), color = GlimmerColors.Taupe, textAlign = TextAlign.Center)
            Box(
                Modifier
                    .padding(top = 3.dp)
                    .size(6.dp)
                    .background(if (selected) GlimmerColors.Prussian else Color.Transparent, CircleShape),
            )
        }
    }
}
