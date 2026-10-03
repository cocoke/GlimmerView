package app.glimmerview.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.glimmerview.R
import app.glimmerview.ui.theme.GlimmerColors
import app.glimmerview.ui.theme.GlimmerIcons
import app.glimmerview.ui.theme.GlimmerType

enum class SocleTab(val icon: ImageVector, val label: Int) {
    GALLERY(GlimmerIcons.Gallery, R.string.tab_gallery),
    ATELIER(GlimmerIcons.Atelier, R.string.tab_atelier),
    COLLECTION(GlimmerIcons.Collection, R.string.tab_collection),
    PREFERENCES(GlimmerIcons.Preferences, R.string.tab_preferences),
}

/** Le socle : barre noire, l'onglet actif devient une pastille claire avec son nom. */
@Composable
fun Socle(selected: SocleTab?, onSelect: (SocleTab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(GlimmerColors.Ink)
            .navigationBarsPadding()
            .height(66.dp)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (tab in SocleTab.entries) {
            val on = tab == selected
            val label = stringResource(tab.label)
            Row(
                modifier = Modifier
                    .height(48.dp)
                    .widthIn(min = 48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (on) GlimmerColors.Plaster else Color.Transparent)
                    .clickable(role = Role.Tab, onClickLabel = label) { onSelect(tab) }
                    .semantics { this.selected = on }
                    .animateContentSize(tween(260))
                    .padding(start = if (on) 14.dp else 0.dp, end = if (on) 18.dp else 0.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = if (on) null else label,
                    tint = if (on) GlimmerColors.Ink else GlimmerColors.Plaster.copy(alpha = 0.74f),
                    modifier = Modifier
                        .padding(horizontal = if (on) 0.dp else 13.5.dp)
                        .size(21.dp),
                )
                if (on) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = GlimmerType.body.copy(fontSize = 14.sp, letterSpacing = 0.01.em),
                        fontWeight = FontWeight.SemiBold,
                        color = GlimmerColors.Ink,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
