package app.glimmerview.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glimmerview.GlimmerViewModel
import app.glimmerview.R
import app.glimmerview.ui.components.AccentTitle
import app.glimmerview.ui.components.Cartel
import app.glimmerview.ui.components.Hairline
import app.glimmerview.ui.components.LivingFrame
import app.glimmerview.ui.components.PrismEmblem
import app.glimmerview.ui.components.SystemBarIcons
import app.glimmerview.ui.components.Tirette
import app.glimmerview.ui.components.Wordmark
import app.glimmerview.ui.theme.GlimmerColors
import app.glimmerview.ui.theme.GlimmerType

/** Vernissage : le premier lancement, une œuvre vivante et trois promesses. */
@Composable
fun VernissageScreen(vm: GlimmerViewModel) {
    SystemBarIcons()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GlimmerColors.Plaster)
            .systemBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PrismEmblem()
            Wordmark()
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                LivingFrame(
                    image = vm.galleryFrames["dawn"],
                    mat = 16.dp,
                    modifier = Modifier.fillMaxHeight().aspectRatio(262f / 340f, matchHeightConstraintsFirst = true),
                )
            }
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, fontSize = 13.sp, color = GlimmerColors.Ink)) {
                        append(stringResource(R.string.scene_dawn))
                    }
                    append(stringResource(R.string.vernissage_caption))
                },
                style = GlimmerType.caption.copy(fontSize = 11.sp),
                color = GlimmerColors.Taupe,
                modifier = Modifier.align(Alignment.End).padding(end = 18.dp),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AccentTitle(stringResource(R.string.vernissage_title), style = GlimmerType.headline.copy(fontSize = 35.sp, lineHeight = 37.sp))
            Text(stringResource(R.string.vernissage_body), style = GlimmerType.body, color = GlimmerColors.Taupe)
        }

        Column {
            Promise(stringResource(R.string.promise_private_label), stringResource(R.string.promise_private))
            Promise(stringResource(R.string.promise_free_label), stringResource(R.string.promise_free))
            Promise(stringResource(R.string.promise_light_label), stringResource(R.string.promise_light))
            Hairline()
        }

        Tirette(stringResource(R.string.vernissage_enter), onClick = vm::finishOnboarding)
    }
}

@Composable
private fun Promise(label: String, text: String) {
    Hairline()
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Cartel(label, modifier = Modifier.width(84.dp))
        Text(text, style = GlimmerType.body.copy(fontSize = 14.sp))
    }
}
