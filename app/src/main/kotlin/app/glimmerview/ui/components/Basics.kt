package app.glimmerview.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.ConfigurationCompat
import app.glimmerview.ui.theme.GlimmerColors
import app.glimmerview.ui.theme.GlimmerIcons
import app.glimmerview.ui.theme.GlimmerType
import java.util.Locale

/** Étiquette de cartel : petites capitales espacées en Hanken Grotesk. */
@Composable
fun Cartel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = GlimmerColors.Taupe,
    fontSize: TextUnit = 10.5.sp,
) {
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.ROOT
    Text(
        text = text.uppercase(locale),
        modifier = modifier,
        style = GlimmerType.cartel.copy(fontSize = fontSize),
        color = color,
        maxLines = 1,
    )
}

/**
 * Estompe un contrôle inactif. Couche graphique à valeur variable plutôt que `Modifier.alpha`
 * conditionnel : la chaîne de modificateurs reste la même quand le contrôle s’active, et son fond
 * ne disparaît pas.
 */
fun Modifier.dimWhenDisabled(enabled: Boolean): Modifier = graphicsLayer { alpha = if (enabled) 1f else 0.4f }

/** Filet de 1 dp. */
@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = GlimmerColors.Stone) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/** Texte où les passages entre astérisques passent en italique : « Créer une *œuvre* ». */
fun accentText(source: String): AnnotatedString = buildAnnotatedString {
    var italic = false
    for (part in source.split('*')) {
        if (italic) withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(part) } else append(part)
        italic = !italic
    }
}

/** Titre de salle : Spectral Light, accent en italique. */
@Composable
fun AccentTitle(source: String, modifier: Modifier = Modifier, style: TextStyle = GlimmerType.display) {
    Text(accentText(source), modifier = modifier, style = style)
}

/** Bouton rond de 44 dp au trait fin ; variante « verre » posée sur une image. */
@Composable
fun CircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glass: Boolean = false,
    filled: Boolean = false,
    size: Dp = 44.dp,
    iconSize: Dp = 18.dp,
    enabled: Boolean = true,
) {
    val background = when {
        filled -> GlimmerColors.Ink
        glass -> GlimmerColors.Plaster.copy(alpha = 0.86f)
        else -> Color.Transparent
    }
    val border = when {
        filled -> GlimmerColors.Ink
        glass -> GlimmerColors.Ink.copy(alpha = 0.12f)
        else -> GlimmerColors.Stone
    }
    Box(
        modifier = modifier
            .dimWhenDisabled(enabled)
            .size(size)
            .clip(CircleShape)
            .background(background)
            .border(1.dp, border, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (filled) GlimmerColors.Plaster else GlimmerColors.Ink,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** La « tirette » : l'action principale, en italique, soulignée d'un trait d'encre. */
@Composable
fun Tirette(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector = GlimmerIcons.Arrow,
) {
    Row(
        modifier = modifier
            .dimWhenDisabled(enabled)
            .fillMaxWidth()
            .height(60.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .drawBehind {
                drawLine(GlimmerColors.Ink, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1.dp.toPx())
            }
            .padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text, style = GlimmerType.tirette, maxLines = 1)
        Box(
            modifier = Modifier
                .size(50.dp)
                .shadow(10.dp, CircleShape, ambientColor = GlimmerColors.Ink, spotColor = GlimmerColors.Ink)
                .background(GlimmerColors.Ink, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = GlimmerColors.Plaster, modifier = Modifier.size(19.dp))
        }
    }
}

/** Onglets en texte, soulignés : salles de la galerie, filtres de la collection. */
@Composable
fun TextTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    withRule: Boolean = true,
) {
    Box(modifier.fillMaxWidth()) {
        if (withRule) Hairline(Modifier.align(Alignment.BottomStart))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            labels.forEachIndexed { index, label ->
                val on = index == selected
                Box(
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clickable(role = Role.Tab) { onSelect(index) }
                        .semantics { this.selected = on }
                        .drawBehind {
                            if (on) {
                                val y = size.height - 0.75.dp.toPx()
                                drawLine(GlimmerColors.Ink, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx())
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = GlimmerType.body.copy(fontSize = 14.5.sp),
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (on) GlimmerColors.Ink else GlimmerColors.Taupe,
                    )
                }
            }
        }
    }
}

/** Lien en texte souligné d'un filet (« Voir l'œuvre », « Ajuster »). */
@Composable
fun InlineLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = GlimmerType.bodySmall,
    lineColor: Color = GlimmerColors.Ink,
) {
    Text(
        text = text,
        style = style,
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .drawBehind {
                drawLine(lineColor, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1.dp.toPx())
            }
            .padding(vertical = 2.dp),
    )
}

/** Petite pastille d'encre pour une action brève (« Valider »). */
@Composable
fun InkPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier = modifier
            .dimWhenDisabled(enabled)
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(GlimmerColors.Ink)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = GlimmerType.body.copy(fontSize = 14.sp), fontWeight = FontWeight.Medium, color = GlimmerColors.Plaster)
    }
}

/** Pastille en verre dépoli posée sur une œuvre (indications, légendes). */
@Composable
fun GlassChip(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(GlimmerColors.Plaster.copy(alpha = 0.88f))
            .heightIn(min = 30.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}
