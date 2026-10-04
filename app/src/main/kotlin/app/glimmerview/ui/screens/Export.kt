package app.glimmerview.ui.screens

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import app.glimmerview.GlimmerViewModel
import app.glimmerview.R
import app.glimmerview.WorkRef
import app.glimmerview.format.DwpFormat
import app.glimmerview.ui.components.Cartel
import app.glimmerview.ui.components.GlimmerSheet
import app.glimmerview.ui.components.InkPill
import app.glimmerview.ui.components.InlineLink
import app.glimmerview.ui.theme.GlimmerColors
import app.glimmerview.ui.theme.GlimmerType
import kotlinx.coroutines.launch
import java.io.File

/**
 * Panneau « Exporter » : l'œuvre part en fichier .dwp, partagé avec une autre app ou enregistré
 * sur le téléphone.
 */
@Composable
fun ExportSheet(vm: GlimmerViewModel, ref: WorkRef) {
    val card = vm.card(ref) ?: return
    GlimmerSheet(onDismiss = vm::cancelExport) { close ->
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var preparing by remember { mutableStateOf(false) }
        val chooserTitle = stringResource(R.string.export_chooser)
        val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(DwpFormat.MIME_TYPE)) { uri ->
            if (uri != null) {
                vm.saveExport(ref, uri)
                close()
            }
        }

        Cartel(stringResource(R.string.export_kicker))
        Text(
            card.title,
            style = GlimmerType.headline.copy(fontStyle = FontStyle.Italic),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(R.string.export_body),
            style = GlimmerType.body.copy(fontSize = 14.5.sp),
            color = GlimmerColors.Taupe,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InlineLink(
                stringResource(R.string.export_save),
                onClick = { save.launch(vm.exportFileName(ref)) },
                style = GlimmerType.italic.copy(fontSize = 15.sp),
            )
            InkPill(
                text = stringResource(if (preparing) R.string.export_preparing else R.string.export_share),
                enabled = !preparing,
                onClick = {
                    preparing = true
                    scope.launch {
                        val file = vm.prepareShare(ref)
                        preparing = false
                        if (file != null) {
                            context.startActivity(shareIntent(context, file, card.title, chooserTitle))
                            close()
                        }
                    }
                },
            )
        }
    }
}

/** Sélecteur de partage d'Android ; le fichier est lu à travers le FileProvider de l'app. */
private fun shareIntent(context: Context, file: File, title: String, chooserTitle: String): Intent {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".exports", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = DwpFormat.MIME_TYPE
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, title)
        clipData = ClipData.newRawUri(title, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return Intent.createChooser(send, chooserTitle)
}
