package app.glimmerview.ui.screens

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glimmerview.BuildConfig
import app.glimmerview.GlimmerViewModel
import app.glimmerview.ModelState
import app.glimmerview.R
import app.glimmerview.Route
import app.glimmerview.ml.ModelCatalog
import app.glimmerview.ui.components.AccentTitle
import app.glimmerview.ui.components.Cartel
import app.glimmerview.ui.components.FStopTabs
import app.glimmerview.ui.components.Hairline
import app.glimmerview.ui.components.InlineLink
import app.glimmerview.ui.components.WireSwitch
import app.glimmerview.ui.theme.GlimmerColors
import app.glimmerview.ui.theme.GlimmerIcons
import app.glimmerview.ui.theme.GlimmerType

/** Préférences : mouvement, batterie, modèle d'IA, à propos. */
@Composable
fun PreferencesRoom(vm: GlimmerViewModel) {
    val settings = vm.settings
    val words = apertureWords()

    RoomPage(padding = androidx.compose.foundation.layout.PaddingValues(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 28.dp)) {
        AccentTitle(stringResource(R.string.prefs_title))

        Section(stringResource(R.string.prefs_motion)) {
            Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.prefs_aperture), style = GlimmerType.body, modifier = Modifier.weight(1f))
                    Text(words[settings.apertureLevel], style = GlimmerType.bodySmall, color = GlimmerColors.Taupe)
                }
                FStopTabs(
                    level = settings.apertureLevel,
                    onLevel = { level -> vm.updateSettings { it.copy(apertureLevel = level) } },
                    labels = apertureLabels(),
                    descriptions = words,
                )
            }
            Hairline()
            SwitchRow(
                title = stringResource(R.string.prefs_recenter),
                subtitle = stringResource(R.string.prefs_recenter_sub),
                checked = settings.recenter,
                onChange = { on -> vm.updateSettings { it.copy(recenter = on) } },
            )
            Hairline()
            SwitchRow(
                title = stringResource(R.string.prefs_reduce),
                subtitle = stringResource(R.string.prefs_reduce_sub),
                checked = settings.reduceMotion,
                onChange = { on -> vm.updateSettings { it.copy(reduceMotion = on) } },
            )
        }

        Section(stringResource(R.string.prefs_battery)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.prefs_rate), style = GlimmerType.body, modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (rate in listOf(30, 60)) {
                        val on = settings.frameRate == rate
                        Text(
                            text = stringResource(R.string.prefs_rate_value, rate),
                            style = GlimmerType.body.copy(fontSize = 14.sp),
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (on) GlimmerColors.Ink else GlimmerColors.Taupe,
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .clickable(role = Role.RadioButton) { vm.updateSettings { it.copy(frameRate = rate) } }
                                .semantics { selected = on }
                                .drawBehind {
                                    if (on) {
                                        drawLine(GlimmerColors.Ink, Offset(0f, size.height - 8.dp.toPx()), Offset(size.width, size.height - 8.dp.toPx()), 1.5.dp.toPx())
                                    }
                                }
                                .padding(top = 11.dp),
                        )
                    }
                }
            }
            Hairline()
            SwitchRow(
                title = stringResource(R.string.prefs_eco),
                subtitle = stringResource(R.string.prefs_eco_sub),
                checked = settings.pauseInBatterySaver,
                onChange = { on -> vm.updateSettings { it.copy(pauseInBatterySaver = on) } },
            )
        }

        Section(stringResource(R.string.prefs_ai)) {
            val model = ModelCatalog.DEFAULT
            val sizeMb = "%.0f".format(model.approxDownloadBytes / 1e6)
            val status = when (val state = vm.modelState) {
                ModelState.Absent -> stringResource(R.string.prefs_model_absent, sizeMb)
                is ModelState.Downloading -> stringResource(R.string.prefs_model_downloading, (state.fraction * 100).toInt())
                ModelState.Ready -> stringResource(R.string.prefs_model_ready, sizeMb)
                is ModelState.Failed -> stringResource(R.string.prefs_model_failed, state.message)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.prefs_model), style = GlimmerType.body)
                    Text(status, style = GlimmerType.caption.copy(fontSize = 12.5.sp), color = GlimmerColors.Taupe)
                }
                when (vm.modelState) {
                    ModelState.Ready -> InlineLink(stringResource(R.string.action_delete), onClick = vm::deleteModel)
                    is ModelState.Downloading -> Unit
                    else -> InlineLink(stringResource(R.string.action_download), onClick = vm::downloadModel)
                }
            }
            Hairline()
            Text(
                stringResource(R.string.prefs_privacy),
                style = GlimmerType.caption.copy(fontSize = 12.5.sp),
                color = GlimmerColors.Taupe,
                modifier = Modifier.padding(vertical = 10.dp),
            )
        }

        Section(stringResource(R.string.prefs_library)) {
            LaunchedEffect(Unit) { vm.measureLibrary() }
            val context = LocalContext.current
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.prefs_library_downloads), style = GlimmerType.body)
                    Text(
                        if (vm.libraryBytes > 0) {
                            stringResource(R.string.prefs_library_size, Formatter.formatShortFileSize(context, vm.libraryBytes))
                        } else {
                            stringResource(R.string.prefs_library_empty)
                        },
                        style = GlimmerType.caption.copy(fontSize = 12.5.sp),
                        color = GlimmerColors.Taupe,
                    )
                }
                if (vm.libraryBytes > 0) InlineLink(stringResource(R.string.prefs_library_clear), onClick = vm::clearLibrary)
            }
        }

        Section(stringResource(R.string.prefs_about)) {
            Text(
                stringResource(R.string.prefs_version, BuildConfig.VERSION_NAME),
                style = GlimmerType.body,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                stringResource(R.string.prefs_licenses),
                style = GlimmerType.caption.copy(fontSize = 12.5.sp),
                color = GlimmerColors.Taupe,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            Hairline()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { vm.navigate(Route.Lab) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.prefs_lab), style = GlimmerType.body)
                    Text(stringResource(R.string.prefs_lab_sub), style = GlimmerType.caption.copy(fontSize = 12.5.sp), color = GlimmerColors.Taupe)
                }
                Icon(GlimmerIcons.Chevron, contentDescription = null, modifier = Modifier.size(16.dp), tint = GlimmerColors.Ink)
            }
            Hairline()
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Cartel(title, modifier = Modifier.padding(bottom = 4.dp))
        Hairline()
        content()
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = GlimmerType.body)
            Text(subtitle, style = GlimmerType.caption.copy(fontSize = 12.5.sp), color = GlimmerColors.Taupe)
        }
        WireSwitch(checked = checked, onCheckedChange = onChange, contentDescription = title)
    }
}
