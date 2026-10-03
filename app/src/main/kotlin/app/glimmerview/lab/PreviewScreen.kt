package app.glimmerview.lab

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.glimmerview.render.ParallaxGlView

/** Aperçu interactif : même moteur de rendu que le fond d'écran, avec mesures et réglages. */
@Composable
fun PreviewScreen(viewModel: LabViewModel) {
    BackHandler { viewModel.closePreview() }
    val image = viewModel.image ?: return
    val depth = viewModel.previewDepth
    val params = viewModel.params
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var glView by remember { mutableStateOf<ParallaxGlView?>(null) }
    var showDepth by remember { mutableStateOf(false) }
    var continuous by remember { mutableStateOf(false) }
    var measureGpu by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                ParallaxGlView(ctx).also { view ->
                    view.onStats = { viewModel.onPreviewStats(it, view.measureGpu) }
                    glView = view
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        val view = glView
        if (view != null) {
            DisposableEffect(lifecycleOwner, view) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> view.start()
                        Lifecycle.Event.ON_PAUSE -> view.stop()
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    view.stop()
                }
            }
            LaunchedEffect(view, depth) { if (depth != null) view.setContent(image, depth) }
            LaunchedEffect(view, params) { view.setParams(params) }
            LaunchedEffect(view, showDepth) { view.setDepthView(showDepth) }
            LaunchedEffect(view, continuous) { view.setContinuous(continuous) }
            LaunchedEffect(view, measureGpu) { view.measureGpu = measureGpu }
        }

        // Toucher l'image = viser ce sujet (sa profondeur devient le plan immobile)
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        viewModel.focusAt(
                            offset.x / size.width,
                            offset.y / size.height,
                            size.width.toFloat() / size.height,
                        )
                    }
                },
        )

        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(12.dp),
        ) {
            StatsBar(viewModel, glView, depthReady = depth != null)
            Spacer(Modifier.weight(1f))
            if (controlsVisible) {
                Controls(
                    viewModel = viewModel,
                    showDepth = showDepth,
                    onShowDepth = { showDepth = it },
                    continuous = continuous,
                    onContinuous = { continuous = it },
                    measureGpu = measureGpu,
                    onMeasureGpu = { measureGpu = it },
                    onApply = { viewModel.applyWallpaper(context) },
                    onHide = { controlsVisible = false },
                )
            } else {
                Button(onClick = { controlsVisible = true }, modifier = Modifier.align(Alignment.End)) {
                    Text("Réglages")
                }
            }
        }
    }
}

@Composable
private fun StatsBar(viewModel: LabViewModel, view: ParallaxGlView?, depthReady: Boolean) {
    val stats = viewModel.previewStats
    val text = buildString {
        if (!depthReady) append("Calcul de la profondeur… ")
        if (stats != null) {
            append("%.2f ms/image · %.0f i/s · %d×%d".format(stats.frameMs, stats.fps, stats.width, stats.height))
        } else {
            append("Incline le téléphone")
        }
        view?.let { append(" · capteur ${it.tiltSensor.source}") }
        viewModel.postProcessTimings?.let { append(" · post-traitement ${LabReport.ms(it.totalMs)}") }
    }
    Surface(color = Color.Black.copy(alpha = 0.55f), shape = RoundedCornerShape(12.dp)) {
        Text(text, color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(10.dp))
    }
}

@Composable
private fun Controls(
    viewModel: LabViewModel,
    showDepth: Boolean,
    onShowDepth: (Boolean) -> Unit,
    continuous: Boolean,
    onContinuous: (Boolean) -> Unit,
    measureGpu: Boolean,
    onMeasureGpu: (Boolean) -> Unit,
    onApply: () -> Unit,
    onHide: () -> Unit,
) {
    val params = viewModel.params
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Intensité : %.1f %% de la largeur".format(params.strength * 100), style = MaterialTheme.typography.labelLarge)
            Slider(
                value = params.strength,
                onValueChange = { viewModel.updateParams(params.copy(strength = it)) },
                valueRange = 0f..0.08f,
            )
            Text("Plan immobile : %.2f (touche l'image pour viser un sujet)".format(params.focus), style = MaterialTheme.typography.labelLarge)
            Slider(
                value = params.focus,
                onValueChange = { viewModel.updateParams(params.copy(focus = it)) },
            )
            Text("Zoom minimal : %.2f".format(params.zoom), style = MaterialTheme.typography.labelLarge)
            Slider(
                value = params.zoom,
                onValueChange = { viewModel.updateParams(params.copy(zoom = it)) },
                valueRange = 1f..1.3f,
            )
            Text("Qualité (pas de ray-marching)", style = MaterialTheme.typography.labelLarge)
            Row {
                listOf(0, 8, 16, 32).forEach { steps ->
                    FilterChip(
                        selected = params.steps == steps,
                        onClick = { viewModel.updateParams(params.copy(steps = steps)) },
                        label = { Text(if (steps == 0) "simple" else "$steps") },
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
            }
            SwitchRow("Contours affinés (filtre guidé + anti-halo)", viewModel.edgeAware, viewModel::updateEdgeAware)
            SwitchRow("Voir la carte de profondeur", showDepth, onShowDepth)
            SwitchRow("Inverser l'axe horizontal", params.invertX) { viewModel.updateParams(params.copy(invertX = it)) }
            SwitchRow("Inverser l'axe vertical", params.invertY) { viewModel.updateParams(params.copy(invertY = it)) }
            SwitchRow("Rendu continu (pour mesurer)", continuous, onContinuous)
            SwitchRow("Mesurer le GPU (glFinish)", measureGpu, onMeasureGpu)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onApply, enabled = viewModel.previewDepth != null) { Text("Définir comme fond d'écran") }
                OutlinedButton(onClick = viewModel::closePreview) { Text("Retour") }
            }
            TextButton(onClick = onHide) { Text("Masquer les réglages") }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
