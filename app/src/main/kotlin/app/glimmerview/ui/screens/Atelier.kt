package app.glimmerview.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glimmerview.GlimmerViewModel
import app.glimmerview.R
import app.glimmerview.WorkRef
import app.glimmerview.core.WorkOrigin
import app.glimmerview.ui.components.AccentTitle
import app.glimmerview.ui.components.Cartel
import app.glimmerview.ui.components.Hairline
import app.glimmerview.ui.components.LivingFrame
import app.glimmerview.ui.theme.GlimmerColors
import app.glimmerview.ui.theme.GlimmerIcons
import app.glimmerview.ui.theme.GlimmerType

/** L'atelier : choisir une photo (sélecteur système, aucune permission) et la transformer en œuvre. */
@Composable
fun AtelierRoom(vm: GlimmerViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.startCreation(uri)
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val created = vm.works.filter { it.origin == WorkOrigin.CREATED }.take(3)

    RoomPage(padding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 28.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Cartel(stringResource(R.string.atelier_kicker))
            AccentTitle(stringResource(R.string.atelier_title))
            Text(
                stringResource(R.string.atelier_body),
                style = GlimmerType.body.copy(fontSize = 14.5.sp),
                color = GlimmerColors.Taupe,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        // Le cadre vide qui attend son œuvre
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(232.dp)
                .shadow(16.dp, RoundedCornerShape(6.dp), ambientColor = GlimmerColors.Ink, spotColor = GlimmerColors.Ink)
                .background(GlimmerColors.Mat, RoundedCornerShape(6.dp))
                .border(1.dp, GlimmerColors.FrameEdge, RoundedCornerShape(6.dp))
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.atelier_pick), onClick = pick)
                .padding(14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(GlimmerColors.Linen)
                .drawBehind {
                    drawRect(Brush.verticalGradient(0f to GlimmerColors.Ink.copy(alpha = 0.28f), 1f to Color.Transparent, endY = 10.dp.toPx()))
                    drawRect(
                        Brush.linearGradient(
                            0f to Color.Transparent,
                            0.5f to Color.White.copy(alpha = 0.35f),
                            1f to Color.Transparent,
                            start = androidx.compose.ui.geometry.Offset(size.width * 0.05f, 0f),
                            end = androidx.compose.ui.geometry.Offset(size.width * 0.45f, size.height * 0.12f),
                        ),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(56.dp).border(1.dp, GlimmerColors.Ink, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(GlimmerIcons.Plus, contentDescription = null, modifier = Modifier.size(20.dp), tint = GlimmerColors.Ink)
                }
                Text(stringResource(R.string.atelier_pick), style = GlimmerType.tirette)
                Cartel(stringResource(R.string.atelier_formats))
            }
        }

        if (created.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Cartel(stringResource(R.string.atelier_recent), color = GlimmerColors.Ink)
            }
            Hairline()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                created.forEach { info ->
                    val ref = WorkRef.Saved(info.id)
                    LivingFrame(
                        image = vm.frameImage(ref),
                        mat = 6.dp,
                        elevation = 8.dp,
                        contentDescription = info.title,
                        onClick = { vm.openWork(ref) },
                        modifier = Modifier.weight(1f).height(132.dp),
                    )
                }
                repeat(3 - created.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        Column(Modifier.padding(top = 8.dp)) {
            Cartel(stringResource(R.string.atelier_tips), modifier = Modifier.padding(bottom = 6.dp))
            Tip(stringResource(R.string.atelier_tip_subject))
            Tip(stringResource(R.string.atelier_tip_depth))
            Tip(stringResource(R.string.atelier_tip_portrait))
            Hairline()
        }
    }
}

@Composable
private fun Tip(text: String) {
    Hairline()
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .padding(top = 8.dp)
                .size(5.dp)
                .background(GlimmerColors.Prussian, CircleShape),
        )
        Text(text, style = GlimmerType.body.copy(fontSize = 14.5.sp), modifier = Modifier.weight(1f))
    }
}
