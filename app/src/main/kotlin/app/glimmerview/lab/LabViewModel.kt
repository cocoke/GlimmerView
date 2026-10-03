package app.glimmerview.lab

import android.app.Application
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.glimmerview.core.DepthMap
import app.glimmerview.core.ParallaxParams
import app.glimmerview.core.WallpaperStore
import app.glimmerview.ml.Backend
import app.glimmerview.ml.BenchConfig
import app.glimmerview.ml.BenchResult
import app.glimmerview.ml.DepthBenchmark
import app.glimmerview.ml.DepthPostProcessor
import app.glimmerview.ml.InputSize
import app.glimmerview.ml.ModelCatalog
import app.glimmerview.ml.ModelSpec
import app.glimmerview.ml.ModelStore
import app.glimmerview.ml.PostProcessTimings
import app.glimmerview.render.GlInfo
import app.glimmerview.render.PreviewStats
import app.glimmerview.wallpaper.GlimmerWallpaperService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

enum class Screen { Lab, Preview }

sealed interface DownloadState {
    data object Absent : DownloadState
    data class InProgress(val fraction: Float) : DownloadState
    data object Done : DownloadState
    data class Failed(val message: String) : DownloadState
}

class ResultUi(val result: BenchResult, val thumbnail: ImageBitmap?)

class LabViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    private val modelStore = ModelStore(application)
    private val wallpaperStore = WallpaperStore(application)

    /** Thread unique pour l'inférence : certains accélérateurs exigent un thread stable. */
    private val mlDispatcher = Executors.newSingleThreadExecutor { Thread(it, "GlimmerML") }.asCoroutineDispatcher()

    // ONNX Runtime + XNNPACK est exclu : son noyau Resize refuse ce modèle à l'initialisation.
    val configs: List<BenchConfig> = listOf(
        BenchConfig(ModelCatalog.DA2_SMALL_TFLITE, Backend.LITERT_GPU, SQUARE),
        BenchConfig(ModelCatalog.DA2_SMALL_TFLITE, Backend.LITERT_CPU, SQUARE),
        BenchConfig(ModelCatalog.DA2_SMALL_TFLITE, Backend.LITERT_NPU, SQUARE),
        BenchConfig(ModelCatalog.DA2_SMALL_ONNX, Backend.ORT_CPU, SQUARE),
        BenchConfig(ModelCatalog.DA2_SMALL_ONNX_INT8, Backend.ORT_CPU, SQUARE),
        BenchConfig(ModelCatalog.DA2_SMALL_ONNX_INT8, Backend.ORT_CPU, PORTRAIT),
    )

    var screen by mutableStateOf(Screen.Lab)
        private set

    var image by mutableStateOf<Bitmap?>(null)
        private set
    var imageError by mutableStateOf<String?>(null)
        private set

    val downloads = mutableStateMapOf<String, DownloadState>()
    val selected = mutableStateMapOf<String, Boolean>()
    var runs by mutableIntStateOf(5)
    var running by mutableStateOf(false)
        private set
    var progress by mutableStateOf<String?>(null)
        private set
    val results = mutableStateListOf<ResultUi>()

    val device: String = DeviceInfo.describe(application)
    var gpu by mutableStateOf("…")
        private set
    var wallpaperStats by mutableStateOf<String?>(null)
        private set

    // Aperçu 3D
    var previewResult by mutableStateOf<ResultUi?>(null)
        private set
    var previewDepth by mutableStateOf<DepthMap?>(null)
        private set
    var postProcessTimings by mutableStateOf<PostProcessTimings?>(null)
        private set
    var edgeAware by mutableStateOf(true)
        private set
    var params by mutableStateOf(ParallaxParams())
        private set
    var previewStats by mutableStateOf<PreviewStats?>(null)
        private set
    val renderMeasurements = mutableStateMapOf<Int, String>()

    /** Message ponctuel à afficher (toast). */
    var message by mutableStateOf<String?>(null)

    init {
        ModelCatalog.all.forEach {
            downloads[it.id] = if (modelStore.isDownloaded(it)) DownloadState.Done else DownloadState.Absent
        }
        configs.forEach { config ->
            selected[config.key] = config.backend == Backend.LITERT_GPU ||
                config.backend == Backend.LITERT_CPU ||
                (config.model == ModelCatalog.DA2_SMALL_ONNX_INT8 && config.inputSize == SQUARE)
        }
        viewModelScope.launch {
            gpu = withContext(Dispatchers.Default) { GlInfo.describe() }
        }
        refreshWallpaperStats()
    }

    fun isAvailable(config: BenchConfig) = downloads[config.model.id] == DownloadState.Done

    fun loadImage(uri: Uri) {
        viewModelScope.launch {
            imageError = null
            runCatching { withContext(Dispatchers.IO) { ImageLoading.decode(context, uri, MAX_IMAGE_SIDE) } }
                .onSuccess {
                    image = it
                    results.clear()
                }
                .onFailure { imageError = it.message ?: it.javaClass.simpleName }
        }
    }

    fun download(spec: ModelSpec) {
        if (downloads[spec.id] is DownloadState.InProgress) return
        downloads[spec.id] = DownloadState.InProgress(0f)
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    modelStore.download(spec) { received, total ->
                        viewModelScope.launch {
                            downloads[spec.id] = DownloadState.InProgress((received.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            downloads[spec.id] = outcome.fold(
                onSuccess = { DownloadState.Done },
                onFailure = { DownloadState.Failed(it.message ?: it.javaClass.simpleName) },
            )
        }
    }

    fun delete(spec: ModelSpec) {
        modelStore.delete(spec)
        downloads[spec.id] = DownloadState.Absent
    }

    fun toggle(config: BenchConfig, enabled: Boolean) {
        selected[config.key] = enabled
    }

    fun runBenchmarks() {
        val bitmap = image ?: return
        val toRun = configs.filter { selected[it.key] == true && isAvailable(it) }
        if (toRun.isEmpty() || running) return
        running = true
        results.clear()
        viewModelScope.launch {
            for ((index, config) in toRun.withIndex()) {
                progress = "(${index + 1}/${toRun.size}) ${config.label}…"
                val result = withContext(mlDispatcher) { DepthBenchmark.run(context, modelStore, config, bitmap, runs) }
                val thumbnail = result.output?.let {
                    withContext(Dispatchers.Default) { DepthPostProcessor.preview(it).asImageBitmap() }
                }
                results += ResultUi(result, thumbnail)
            }
            progress = null
            running = false
        }
    }

    fun openPreview(result: ResultUi) {
        if (result.result.output == null) return
        previewResult = result
        previewDepth = null
        previewStats = null
        renderMeasurements.clear()
        screen = Screen.Preview
        recomputeDepth()
    }

    fun closePreview() {
        screen = Screen.Lab
    }

    fun updateEdgeAware(enabled: Boolean) {
        edgeAware = enabled
        recomputeDepth()
    }

    fun updateParams(value: ParallaxParams) {
        params = value
    }

    /** Vise le sujet touché : sa profondeur devient le plan immobile. Coordonnées de la vue (0..1). */
    fun focusAt(x: Float, y: Float, viewAspect: Float) {
        val depth = previewDepth ?: return
        val bitmap = image ?: return
        val imageAspect = bitmap.width.toFloat() / bitmap.height
        val (u, v) = if (imageAspect > viewAspect) {
            (x - 0.5f) * (viewAspect / imageAspect) + 0.5f to y
        } else {
            x to (y - 0.5f) * (imageAspect / viewAspect) + 0.5f
        }
        params = params.copy(focus = depth.sample(u, v))
    }

    fun onPreviewStats(stats: PreviewStats, measuringGpu: Boolean) {
        previewStats = stats
        if (measuringGpu) {
            renderMeasurements[params.steps] = "%.2f ms (%d×%d)".format(stats.frameMs, stats.width, stats.height)
        }
    }

    fun applyWallpaper(activityContext: Context) {
        val bitmap = image ?: return
        val depth = previewDepth ?: return
        val current = params
        viewModelScope.launch {
            withContext(Dispatchers.IO) { wallpaperStore.save(bitmap, depth, current) }
            val component = ComponentName(context, GlimmerWallpaperService::class.java)
            if (WallpaperManager.getInstance(context).wallpaperInfo?.component == component) {
                message = "Fond d'écran mis à jour"
                return@launch
            }
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
            try {
                activityContext.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                activityContext.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            }
        }
    }

    fun refreshWallpaperStats() {
        viewModelScope.launch {
            wallpaperStats = withContext(Dispatchers.IO) { wallpaperStore.readStats() }
                ?.let(LabReport::formatWallpaperStats)
        }
    }

    fun report(): String {
        val timings = postProcessTimings?.let {
            "${LabReport.ms(it.totalMs)} (normalisation ${LabReport.ms(it.normalizeMs)}, " +
                "redimensionnement ${LabReport.ms(it.resizeMs)}, contours ${LabReport.ms(it.refineMs)})"
        }
        return LabReport.build(
            device = device,
            gpu = gpu,
            thermal = DeviceInfo.thermalStatus(context),
            image = image?.let { "${it.width}×${it.height}" },
            runs = runs,
            results = results.map { it.result },
            postProcess = timings,
            render = renderMeasurements.toMap(),
            wallpaper = wallpaperStats,
        )
    }

    private fun recomputeDepth() {
        val raw = previewResult?.result?.output ?: return
        val bitmap = image ?: return
        val edges = edgeAware
        viewModelScope.launch {
            val refined = withContext(Dispatchers.Default) { DepthPostProcessor.refine(raw, bitmap, edgeAware = edges) }
            previewDepth = refined.depth
            postProcessTimings = refined.timings
        }
    }

    override fun onCleared() {
        mlDispatcher.close()
    }

    private companion object {
        val SQUARE = InputSize(518, 518)
        val PORTRAIT = InputSize(392, 868)
        const val MAX_IMAGE_SIDE = 2560
    }
}
