package app.glimmerview.lab

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.glimmerview.ml.ModelCatalog
import app.glimmerview.ml.ModelSpec

/** Écran de validation technique (Phase 0). Interface provisoire, remplacée après la passe de design. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabScreen(viewModel: LabViewModel) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::loadImage)
    }
    LifecycleResumeEffect(Unit) {
        viewModel.refreshWallpaperStats()
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel.message) {
        viewModel.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.message = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("GlimmerView Lab")
                    Text("Phase 0 · validation technique", style = MaterialTheme.typography.labelMedium)
                }
            })
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Section("Appareil") {
                    Text(viewModel.device, style = MaterialTheme.typography.bodySmall)
                    Text("GPU : ${viewModel.gpu}", style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                Section("1 · Image") {
                    Button(onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text("Choisir une photo") }
                    viewModel.image?.let { bitmap ->
                        val preview = remember(bitmap) { bitmap.asImageBitmap() }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                bitmap = preview,
                                contentDescription = "Photo choisie",
                                modifier = Modifier.height(120.dp),
                                contentScale = ContentScale.Fit,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("${bitmap.width}×${bitmap.height}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    viewModel.imageError?.let { ErrorText(it) }
                }
            }
            item {
                Section("2 · Modèles") {
                    ModelCatalog.all.forEach { spec ->
                        ModelRow(
                            spec = spec,
                            state = viewModel.downloads[spec.id] ?: DownloadState.Absent,
                            onDownload = { viewModel.download(spec) },
                            onDelete = { viewModel.delete(spec) },
                        )
                    }
                }
            }
            item {
                Section("3 · Mesures") {
                    viewModel.configs.forEach { config ->
                        val available = viewModel.isAvailable(config)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = viewModel.selected[config.key] == true,
                                onCheckedChange = { viewModel.toggle(config, it) },
                                enabled = available && !viewModel.running,
                            )
                            Column {
                                Text(config.label, style = MaterialTheme.typography.bodyMedium)
                                if (!available) {
                                    Text("Modèle à télécharger", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    Text("Inférences par mesure", style = MaterialTheme.typography.bodyMedium)
                    Row {
                        listOf(3, 5, 10).forEach { n ->
                            FilterChip(
                                selected = viewModel.runs == n,
                                onClick = { viewModel.runs = n },
                                label = { Text("$n") },
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        }
                    }
                    Button(
                        onClick = viewModel::runBenchmarks,
                        enabled = viewModel.image != null && !viewModel.running,
                    ) { Text("Lancer les mesures") }
                    if (viewModel.image == null) Text("Choisis d'abord une photo.", style = MaterialTheme.typography.labelSmall)
                    viewModel.progress?.let {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            items(viewModel.results) { result ->
                ResultCard(result, onPreview = { viewModel.openPreview(result) })
            }
            item {
                Section("Rapport") {
                    Text(
                        "Copie le rapport et colle-le dans la conversation pour trancher le choix du runtime.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { copyToClipboard(context, viewModel.report()) }) { Text("Copier") }
                        OutlinedButton(onClick = { share(context, viewModel.report()) }) { Text("Partager") }
                    }
                    Text(
                        "Fond d'écran : ${viewModel.wallpaperStats ?: "pas encore de statistiques (applique un fond puis utilise le téléphone)"}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun ModelRow(spec: ModelSpec, state: DownloadState, onDownload: () -> Unit, onDelete: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(spec.label, style = MaterialTheme.typography.bodyMedium)
        Text(
            "${spec.license} · ${"%.0f".format(spec.approxDownloadBytes / 1e6)} Mo",
            style = MaterialTheme.typography.labelSmall,
        )
        when (state) {
            DownloadState.Absent -> OutlinedButton(onClick = onDownload) { Text("Télécharger") }
            is DownloadState.InProgress -> LinearProgressIndicator(
                progress = { state.fraction },
                modifier = Modifier.fillMaxWidth(),
            )
            DownloadState.Done -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✓ Téléchargé", style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = onDelete) { Text("Supprimer") }
            }
            is DownloadState.Failed -> Column {
                ErrorText(state.message)
                OutlinedButton(onClick = onDownload) { Text("Réessayer") }
            }
        }
    }
}

@Composable
private fun ResultCard(item: ResultUi, onPreview: () -> Unit) {
    val r = item.result
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            item.thumbnail?.let {
                Image(bitmap = it, contentDescription = "Carte de profondeur", modifier = Modifier.size(96.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(r.config.label, style = MaterialTheme.typography.titleSmall)
                if (!r.ok) {
                    ErrorText(r.error ?: "Erreur")
                } else {
                    Text(
                        "Moyenne ${LabReport.ms(r.averageMs)} (min ${LabReport.ms(r.minMs)}, max ${LabReport.ms(r.maxMs)})",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Init ${LabReport.ms(r.initMs)} · 1re ${LabReport.ms(r.firstRunMs)} · " +
                            "prétrait. ${LabReport.ms(r.preprocessMs)} · PSS ${"%.0f".format(r.pssMb)} Mo",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = onPreview) { Text("Aperçu 3D") }
                }
            }
        }
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

private fun copyToClipboard(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText("GlimmerView Lab", text))
    Toast.makeText(context, "Rapport copié", Toast.LENGTH_SHORT).show()
}

private fun share(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(intent, "Partager le rapport"))
}
