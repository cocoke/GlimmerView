package app.glimmerview

import android.app.Application
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.edit
import androidx.core.graphics.scale
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.glimmerview.core.ApertureScale
import app.glimmerview.core.DepthMap
import app.glimmerview.core.FocusScale
import app.glimmerview.core.ParallaxParams
import app.glimmerview.core.WallpaperSettings
import app.glimmerview.core.WallpaperStore
import app.glimmerview.core.WorkInfo
import app.glimmerview.core.WorkLibrary
import app.glimmerview.core.WorkOrigin
import app.glimmerview.data.Contours
import app.glimmerview.data.ImageLoading
import app.glimmerview.data.Scene
import app.glimmerview.data.SceneArt
import app.glimmerview.ml.Backend
import app.glimmerview.ml.DepthEstimator
import app.glimmerview.ml.DepthPostProcessor
import app.glimmerview.ml.InputSize
import app.glimmerview.ml.ModelCatalog
import app.glimmerview.ml.ModelStore
import app.glimmerview.ml.RawDepth
import app.glimmerview.ui.components.PreviewContent
import app.glimmerview.wallpaper.GlimmerWallpaperService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Ce qu'affichent le cartel et les vignettes. */
data class WorkCard(
    val ref: WorkRef,
    val title: String,
    val artist: String,
    val medium: String,
    val license: String,
    /** Année affichée après le titre (œuvres de la galerie), null pour les créations datées. */
    val year: Int?,
    /** Null pour une œuvre de la galerie qui n'est pas (encore) dans la collection. */
    val origin: WorkOrigin?,
)

/** Œuvre chargée en pleine résolution pour l'aperçu 3D. */
class LoadedWork(val ref: WorkRef, val content: PreviewContent)

sealed interface ModelState {
    data object Absent : ModelState
    data class Downloading(val fraction: Float) : ModelState
    data object Ready : ModelState
    data class Failed(val message: String) : ModelState
}

enum class GenStage { PREPARE, DOWNLOAD, DEPTH, CONTOURS, FINISH, DONE, FAILED }

data class Generation(
    val stage: GenStage = GenStage.PREPARE,
    val photo: ImageBitmap? = null,
    /** Version minuscule de la photo, étirée pour simuler le flou du début. */
    val blurred: ImageBitmap? = null,
    val downloadFraction: Float = 0f,
    val contours: List<FloatArray> = emptyList(),
    val resultId: String? = null,
    val error: String? = null,
    /** Premier tirage : le modèle doit d'abord être téléchargé. */
    val firstRun: Boolean = false,
)

enum class Placement { HOME, LOCK, BOTH }

sealed interface ApplyState {
    data object Idle : ApplyState
    data object Working : ApplyState
    data class NeedsPicker(val placement: Placement) : ApplyState
    data class WaitingPicker(val placement: Placement) : ApplyState
    data class Done(val placement: Placement) : ApplyState
}

class GlimmerViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    private val library = WorkLibrary(application)
    private val wallpaperStore = WallpaperStore(application)
    private val modelStore = ModelStore(application)
    private val prefs = application.getSharedPreferences("glimmer_ui", Context.MODE_PRIVATE)

    /** Thread unique pour l'inférence : ONNX Runtime y garde ses tampons au chaud. */
    private val mlDispatcher = Executors.newSingleThreadExecutor { Thread(it, "GlimmerML") }.asCoroutineDispatcher()

    // ── Navigation ────────────────────────────────────────────────────────────

    val backStack = mutableStateListOf<Route>()

    // ── Galerie ───────────────────────────────────────────────────────────────

    val scenes: List<Scene> = SceneArt.all
    /** Vignettes plein écran (format 9:20), comme l'œuvre accrochée. */
    val sceneThumbs = mutableStateMapOf<String, ImageBitmap>()

    /** Vignettes au format des cadres : la scène est recomposée, tous ses plans restent visibles. */
    val sceneFrames = mutableStateMapOf<String, ImageBitmap>()

    /** Salle affichée : 0 = toutes, puis l'ordre de [app.glimmerview.data.Room]. */
    var room by mutableIntStateOf(0)

    // ── Collection ────────────────────────────────────────────────────────────

    var works by mutableStateOf<List<WorkInfo>>(emptyList())
        private set
    val workThumbs = mutableStateMapOf<String, ImageBitmap>()
    var currentWorkId by mutableStateOf<String?>(null)
        private set
    var collectionTab by mutableIntStateOf(0)

    // ── Préférences ───────────────────────────────────────────────────────────

    var settings by mutableStateOf(WallpaperSettings())
        private set
    var modelState by mutableStateOf<ModelState>(ModelState.Absent)
        private set

    // ── Œuvre et mise au point ────────────────────────────────────────────────

    var preview by mutableStateOf<LoadedWork?>(null)
        private set
    var previewParams by mutableStateOf(ParallaxParams())
        private set
    var previewError by mutableStateOf<String?>(null)
        private set
    private var previewJob: Job? = null
    private var saveParamsJob: Job? = null

    // ── Tirage et accrochage ──────────────────────────────────────────────────

    var generation by mutableStateOf<Generation?>(null)
        private set
    private var generationJob: Job? = null

    var applyState by mutableStateOf<ApplyState>(ApplyState.Idle)
        private set

    /** Message ponctuel (toast). */
    var message by mutableStateOf<String?>(null)

    init {
        backStack += if (prefs.getBoolean(KEY_ONBOARDED, false)) Route.Galerie else Route.Vernissage
        modelState = if (modelStore.isDownloaded(ModelCatalog.DEFAULT)) ModelState.Ready else ModelState.Absent
        // Les cadres de la galerie d'abord (premier écran), dessinés en parallèle.
        viewModelScope.launch {
            scenes.map { scene ->
                async(Dispatchers.Default) { scene.id to SceneArt.renderColor(scene, FRAME_WIDTH, FRAME_HEIGHT) }
            }.awaitAll().forEach { (id, frame) -> sceneFrames[id] = frame.asImageBitmap() }
            scenes.map { scene ->
                async(Dispatchers.Default) { scene.id to SceneArt.renderColor(scene, PREVIEW_THUMB_WIDTH, PREVIEW_THUMB_HEIGHT) }
            }.awaitAll().forEach { (id, thumb) -> sceneThumbs[id] = thumb.asImageBitmap() }
        }
        viewModelScope.launch {
            settings = withContext(Dispatchers.IO) {
                modelStore.pruneUnknown()
                wallpaperStore.loadSettings()
            }
            refreshLibrary()
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    val route: Route get() = backStack.lastOrNull() ?: Route.Galerie

    fun selectTab(route: Route) {
        backStack.clear()
        backStack += route
    }

    fun navigate(route: Route) {
        backStack += route
    }

    fun back(): Boolean {
        if (backStack.size <= 1) return false
        val popped = backStack.removeAt(backStack.lastIndex)
        if (popped == Route.Tirage) cancelCreation()
        return true
    }

    fun finishOnboarding() {
        prefs.edit { putBoolean(KEY_ONBOARDED, true) }
        selectTab(Route.Galerie)
    }

    fun openWork(ref: WorkRef) {
        navigate(Route.Oeuvre(ref))
        loadPreview(ref)
    }

    fun adjust(ref: WorkRef) {
        navigate(Route.MiseAuPoint(ref))
        loadPreview(ref)
    }

    fun hang(ref: WorkRef) {
        applyState = ApplyState.Idle
        navigate(Route.Accrocher(ref))
    }

    // ── Fiches et vignettes ───────────────────────────────────────────────────

    fun card(ref: WorkRef): WorkCard? = when (ref) {
        is WorkRef.Scene -> SceneArt.find(ref.id)?.let(::sceneCard)
        is WorkRef.Saved -> works.firstOrNull { it.id == ref.id }?.let(::savedCard)
    }

    fun thumbnail(ref: WorkRef): ImageBitmap? = when (ref) {
        is WorkRef.Scene -> sceneThumbs[ref.id]
        is WorkRef.Saved -> workThumbs[ref.id] ?: sceneIdOf(ref.id)?.let { sceneThumbs[it] }
    }

    /** Image d'un cadre : les scènes au format du cadre, les photos recadrées au centre. */
    fun frameImage(ref: WorkRef): ImageBitmap? = when (ref) {
        is WorkRef.Scene -> sceneFrames[ref.id]
        is WorkRef.Saved -> sceneIdOf(ref.id)?.let { sceneFrames[it] } ?: workThumbs[ref.id]
    }

    fun sceneCard(scene: Scene) = WorkCard(
        ref = WorkRef.Scene(scene.id),
        title = context.getString(scene.title),
        artist = context.getString(R.string.artist_collective),
        medium = context.resources.getQuantityString(R.plurals.medium_scene, scene.planes, scene.planes),
        license = context.getString(R.string.license_cc0),
        year = SCENE_YEAR,
        origin = if (isCollected(scene.id)) WorkOrigin.ACQUIRED else null,
    )

    private fun savedCard(info: WorkInfo) = WorkCard(
        ref = WorkRef.Saved(info.id),
        title = info.title,
        artist = info.artist,
        medium = info.medium,
        license = info.license,
        year = if (info.origin == WorkOrigin.ACQUIRED) SCENE_YEAR else null,
        origin = info.origin,
    )

    fun isCollected(sceneId: String): Boolean = works.any { it.id == SCENE_PREFIX + sceneId }

    private fun sceneIdOf(savedId: String): String? = savedId.removePrefix(SCENE_PREFIX).takeIf { savedId.startsWith(SCENE_PREFIX) }

    private suspend fun refreshLibrary() {
        val (list, current) = withContext(Dispatchers.IO) { library.list() to library.currentId() }
        works = list
        currentWorkId = current
        for (info in list) {
            if (info.id in workThumbs) continue
            withContext(Dispatchers.IO) { library.thumbnail(info.id) }?.let { workThumbs[info.id] = it.asImageBitmap() }
        }
        val ids = list.map { it.id }.toSet()
        workThumbs.keys.filter { it !in ids }.forEach { workThumbs.remove(it) }
    }

    // ── Aperçu 3D ─────────────────────────────────────────────────────────────

    fun loadPreview(ref: WorkRef) {
        if (preview?.ref == ref) return
        previewJob?.cancel()
        preview = null
        previewError = null
        previewJob = viewModelScope.launch {
            runCatching { loadWork(ref) }
                .onSuccess { (loaded, params) ->
                    preview = loaded
                    previewParams = params
                }
                .onFailure { if (it !is CancellationException) previewError = it.message ?: it.javaClass.simpleName }
        }
    }

    private suspend fun loadWork(ref: WorkRef): Pair<LoadedWork, ParallaxParams> = when (ref) {
        is WorkRef.Scene -> withContext(Dispatchers.Default) {
            val scene = checkNotNull(SceneArt.find(ref.id)) { "Scène inconnue : ${ref.id}" }
            val color = SceneArt.renderColor(scene, SCENE_WIDTH, SCENE_HEIGHT)
            val depth = SceneArt.renderDepth(scene, SCENE_DEPTH_WIDTH, SCENE_DEPTH_HEIGHT)
            LoadedWork(ref, PreviewContent("scene:${scene.id}", color, depth)) to defaultParams(FocusScale.focus(3))
        }
        is WorkRef.Saved -> withContext(Dispatchers.IO) {
            val stored = checkNotNull(library.load(ref.id)) { "Œuvre introuvable" }
            LoadedWork(ref, PreviewContent("work:${ref.id}", stored.color, stored.depth)) to stored.params
        }
    }

    private fun defaultParams(focus: Float) = ParallaxParams(
        strength = ApertureScale.strength(settings.apertureLevel),
        focus = focus,
    )

    fun updateParams(params: ParallaxParams) {
        previewParams = params
        val ref = preview?.ref as? WorkRef.Saved ?: return
        saveParamsJob?.cancel()
        saveParamsJob = viewModelScope.launch(Dispatchers.IO) {
            delay(300)
            library.saveParams(ref.id, params)
            // L'œuvre accrochée se met à jour en direct sur l'écran d'accueil.
            if (library.currentId() == ref.id) wallpaperStore.saveParams(params)
        }
    }

    /** Vise le sujet touché : sa profondeur devient le plan immobile. Coordonnées de la vue (0..1). */
    fun focusAt(x: Float, y: Float, viewAspect: Float) {
        val content = preview?.content ?: return
        val imageAspect = content.color.width.toFloat() / content.color.height
        val (u, v) = if (imageAspect > viewAspect) {
            (x - 0.5f) * (viewAspect / imageAspect) + 0.5f to y
        } else {
            x to (y - 0.5f) * (imageAspect / viewAspect) + 0.5f
        }
        updateParams(previewParams.copy(focus = content.depth.sample(u, v)))
    }

    // ── Collection ────────────────────────────────────────────────────────────

    /** Ajoute une œuvre de la galerie à la collection, ou l'en retire. */
    fun toggleCollected(ref: WorkRef.Scene) {
        viewModelScope.launch {
            val id = SCENE_PREFIX + ref.id
            if (isCollected(ref.id)) {
                withContext(Dispatchers.IO) { library.delete(id) }
                message = context.getString(R.string.message_removed)
            } else {
                acquire(ref, if (preview?.ref == ref) previewParams else defaultParams(FocusScale.focus(3)))
                message = context.getString(R.string.message_collected)
            }
            refreshLibrary()
        }
    }

    fun deleteWork(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { library.delete(id) }
            if (preview?.ref == WorkRef.Saved(id)) preview = null
            refreshLibrary()
            message = context.getString(R.string.message_deleted)
            back()
        }
    }

    /** Copie une œuvre de la galerie dans la collection (identifiant stable) et renvoie son identifiant. */
    private suspend fun acquire(ref: WorkRef, params: ParallaxParams): String = when (ref) {
        is WorkRef.Saved -> ref.id
        is WorkRef.Scene -> withContext(Dispatchers.Default) {
            val id = SCENE_PREFIX + ref.id
            if (library.contains(id)) {
                library.saveParams(id, params)
            } else {
                val scene = checkNotNull(SceneArt.find(ref.id)) { "Scène inconnue : ${ref.id}" }
                val loaded = preview?.takeIf { it.ref == ref }?.content
                val color = loaded?.color ?: SceneArt.renderColor(scene, SCENE_WIDTH, SCENE_HEIGHT)
                val depth = loaded?.depth ?: SceneArt.renderDepth(scene, SCENE_DEPTH_WIDTH, SCENE_DEPTH_HEIGHT)
                val card = sceneCard(scene)
                val info = WorkInfo(
                    id = id,
                    title = card.title,
                    artist = card.artist,
                    medium = card.medium,
                    license = card.license,
                    origin = WorkOrigin.ACQUIRED,
                    createdAt = System.currentTimeMillis(),
                    width = color.width,
                    height = color.height,
                )
                library.save(info, color, depth, params, SceneArt.renderColor(scene, THUMB_WIDTH, THUMB_HEIGHT))
            }
            id
        }
    }

    // ── Accrochage ────────────────────────────────────────────────────────────

    fun apply(ref: WorkRef, placement: Placement) {
        if (applyState == ApplyState.Working) return
        applyState = ApplyState.Working
        viewModelScope.launch {
            val outcome = runCatching {
                val params = if (preview?.ref == ref) previewParams else null
                val id = acquire(ref, params ?: withContext(Dispatchers.IO) { savedParamsOrDefault(ref) })
                withContext(Dispatchers.IO) {
                    val stored = checkNotNull(library.load(id)) { "Œuvre introuvable" }
                    wallpaperStore.save(stored.color, stored.depth, stored.params)
                    library.setCurrent(id)
                    if (placement == Placement.LOCK) setLockScreenImage(stored.color)
                }
                refreshLibrary()
            }
            applyState = outcome.fold(
                onSuccess = {
                    when {
                        placement == Placement.LOCK -> ApplyState.Done(placement)
                        isLiveWallpaperActive() -> {
                            if (placement == Placement.BOTH) shareWithLockScreen()
                            ApplyState.Done(placement)
                        }
                        else -> ApplyState.NeedsPicker(placement)
                    }
                },
                onFailure = {
                    message = context.getString(R.string.message_apply_failed, it.message ?: it.javaClass.simpleName)
                    ApplyState.Idle
                },
            )
        }
    }

    private fun savedParamsOrDefault(ref: WorkRef): ParallaxParams = when (ref) {
        is WorkRef.Saved -> library.params(ref.id)
        is WorkRef.Scene -> defaultParams(FocusScale.focus(3))
    }

    /** Intention du sélecteur système, qui propose GlimmerView en aperçu. */
    fun livePickerIntent(): Intent =
        Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, wallpaperComponent())

    fun onPickerLaunched(placement: Placement) {
        applyState = ApplyState.WaitingPicker(placement)
    }

    fun onPickerClosed(placement: Placement) {
        applyState = if (isLiveWallpaperActive()) {
            if (placement == Placement.BOTH) shareWithLockScreen()
            ApplyState.Done(placement)
        } else {
            message = context.getString(R.string.message_apply_cancelled)
            ApplyState.Idle
        }
    }

    private fun wallpaperComponent() = ComponentName(context, GlimmerWallpaperService::class.java)

    private fun isLiveWallpaperActive(): Boolean =
        WallpaperManager.getInstance(context).wallpaperInfo?.component == wallpaperComponent()

    /** Retire l'image propre à l'écran de verrouillage : il reprend alors l'œuvre animée de l'accueil. */
    private fun shareWithLockScreen() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { WallpaperManager.getInstance(context).clear(WallpaperManager.FLAG_LOCK) }
        }
    }

    /** Écran de verrouillage : image fixe de l'œuvre, recadrée au format de l'écran. */
    private fun setLockScreenImage(color: Bitmap) {
        val metrics = context.resources.displayMetrics
        val screenAspect = min(metrics.widthPixels, metrics.heightPixels).toFloat() /
            max(metrics.widthPixels, metrics.heightPixels)
        val imageAspect = color.width.toFloat() / color.height
        val crop = if (imageAspect > screenAspect) {
            val w = (color.height * screenAspect).roundToInt()
            Rect((color.width - w) / 2, 0, (color.width + w) / 2, color.height)
        } else {
            val h = (color.width / screenAspect).roundToInt()
            Rect(0, (color.height - h) / 2, color.width, (color.height + h) / 2)
        }
        WallpaperManager.getInstance(context).setBitmap(color, crop, true, WallpaperManager.FLAG_LOCK)
    }

    // ── Tirage : photo → profondeur → œuvre ───────────────────────────────────

    fun startCreation(uri: Uri) {
        generationJob?.cancel()
        val firstRun = !modelStore.isDownloaded(ModelCatalog.DEFAULT)
        generation = Generation(firstRun = firstRun)
        navigate(Route.Tirage)
        generationJob = viewModelScope.launch {
            try {
                val photo = withContext(Dispatchers.IO) { ImageLoading.decode(context, uri, MAX_PHOTO_SIDE) }
                val blurred = withContext(Dispatchers.Default) { scaleToWidth(photo, 24) }
                update { copy(photo = photo.asImageBitmap(), blurred = blurred.asImageBitmap()) }
                if (firstRun) {
                    update { copy(stage = GenStage.DOWNLOAD) }
                    downloadDefaultModel { fraction -> update { copy(downloadFraction = fraction) } }
                }
                update { copy(stage = GenStage.DEPTH) }
                val raw = withContext(mlDispatcher) { estimateDepth(photo) }
                update { copy(stage = GenStage.CONTOURS) }
                val depth = withContext(Dispatchers.Default) { DepthPostProcessor.refine(raw, photo).depth }
                val contours = withContext(Dispatchers.Default) { Contours.trace(depth, CONTOUR_LEVELS) }
                update { copy(stage = GenStage.FINISH, contours = contours) }
                val info = withContext(Dispatchers.IO) { saveCreation(photo, depth) }
                refreshLibrary()
                preview = LoadedWork(WorkRef.Saved(info.id), PreviewContent("work:${info.id}", photo, depth))
                previewParams = withContext(Dispatchers.IO) { library.params(info.id) }
                update { copy(stage = GenStage.DONE, resultId = info.id) }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                update { copy(stage = GenStage.FAILED, error = t.message ?: t.javaClass.simpleName) }
            }
        }
    }

    private inline fun update(block: Generation.() -> Generation) {
        generation = generation?.block()
    }

    /** Fin du tirage : la mise au point remplace l'écran de tirage dans la pile. */
    fun openCreation() {
        val id = generation?.resultId ?: return
        if (route == Route.Tirage) backStack.removeAt(backStack.lastIndex)
        generation = null
        navigate(Route.MiseAuPoint(WorkRef.Saved(id)))
    }

    fun cancelCreation() {
        generationJob?.cancel()
        generationJob = null
        generation = null
    }

    private fun estimateDepth(photo: Bitmap): RawDepth {
        val spec = ModelCatalog.DEFAULT
        DepthEstimator.create(spec, modelStore.file(spec), Backend.ORT_CPU, inputSizeFor(photo.width, photo.height)).use { e ->
            e.setInput(photo.scale(e.inputSize.width, e.inputSize.height))
            e.run()
            return RawDepth(e.inputSize.width, e.inputSize.height, e.readOutput())
        }
    }

    /** Taille d'entrée au format de la photo, ~518² pixels, côtés multiples de 14 (patchs du modèle). */
    private fun inputSizeFor(width: Int, height: Int): InputSize {
        val aspect = width.toFloat() / height
        fun snap(v: Float) = ((v / 14f).roundToInt() * 14).coerceIn(14 * 14, 14 * 74)
        return InputSize(snap(sqrt(INPUT_BUDGET * aspect)), snap(sqrt(INPUT_BUDGET / aspect)))
    }

    private fun saveCreation(photo: Bitmap, depth: DepthMap): WorkInfo {
        val now = System.currentTimeMillis()
        val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(now))
        val info = WorkInfo(
            id = WorkLibrary.newId(),
            title = context.getString(R.string.work_untitled, date),
            artist = context.getString(R.string.artist_you),
            medium = context.getString(R.string.medium_photo),
            license = context.getString(R.string.license_personal),
            origin = WorkOrigin.CREATED,
            createdAt = now,
            width = photo.width,
            height = photo.height,
        )
        return library.save(info, photo, depth, defaultParams(subjectFocus(depth)), scaleToWidth(photo, THUMB_WIDTH))
    }

    /** Plan immobile par défaut : la profondeur médiane du centre de l'image, là où se trouve souvent le sujet. */
    private fun subjectFocus(depth: DepthMap): Float {
        val samples = ArrayList<Float>(400)
        for (j in 0 until 20) for (i in 0 until 20) {
            samples += depth.sample(0.25f + 0.5f * (i + 0.5f) / 20f, 0.3f + 0.5f * (j + 0.5f) / 20f)
        }
        samples.sort()
        return samples[samples.size / 2]
    }

    private fun scaleToWidth(bitmap: Bitmap, width: Int): Bitmap {
        if (bitmap.width <= width) return bitmap
        val height = max(1, (bitmap.height * width.toFloat() / bitmap.width).roundToInt())
        return bitmap.scale(width, height)
    }

    // ── Modèle d'IA ───────────────────────────────────────────────────────────

    fun downloadModel() {
        if (modelState is ModelState.Downloading) return
        viewModelScope.launch {
            runCatching { downloadDefaultModel { } }
        }
    }

    fun deleteModel() {
        modelStore.delete(ModelCatalog.DEFAULT)
        modelState = ModelState.Absent
    }

    private suspend fun downloadDefaultModel(onProgress: (Float) -> Unit) {
        val spec = ModelCatalog.DEFAULT
        if (modelStore.isDownloaded(spec)) {
            modelState = ModelState.Ready
            return
        }
        modelState = ModelState.Downloading(0f)
        try {
            withContext(Dispatchers.IO) {
                modelStore.download(spec) { received, total ->
                    val fraction = (received.toFloat() / total).coerceIn(0f, 1f)
                    viewModelScope.launch {
                        modelState = ModelState.Downloading(fraction)
                        onProgress(fraction)
                    }
                }
            }
            modelState = ModelState.Ready
        } catch (e: CancellationException) {
            modelState = ModelState.Absent
            throw e
        } catch (t: Throwable) {
            modelState = ModelState.Failed(t.message ?: t.javaClass.simpleName)
            throw t
        }
    }

    // ── Préférences ───────────────────────────────────────────────────────────

    fun updateSettings(transform: (WallpaperSettings) -> WallpaperSettings) {
        val next = transform(settings)
        settings = next
        viewModelScope.launch(Dispatchers.IO) { wallpaperStore.saveSettings(next) }
    }

    override fun onCleared() {
        mlDispatcher.close()
    }

    companion object {
        private const val KEY_ONBOARDED = "onboarded"
        private const val SCENE_PREFIX = "scene-"
        private const val SCENE_YEAR = 2026
        private const val THUMB_WIDTH = 720
        private const val THUMB_HEIGHT = 1600
        private const val PREVIEW_THUMB_WIDTH = 540
        private const val PREVIEW_THUMB_HEIGHT = 1200
        private const val FRAME_WIDTH = 600
        private const val FRAME_HEIGHT = 770
        private const val SCENE_WIDTH = 1080
        private const val SCENE_HEIGHT = 2400
        private const val SCENE_DEPTH_WIDTH = 720
        private const val SCENE_DEPTH_HEIGHT = 1600
        private const val MAX_PHOTO_SIDE = 2400
        private const val INPUT_BUDGET = 518f * 518f
        private val CONTOUR_LEVELS = floatArrayOf(0.2f, 0.35f, 0.5f, 0.65f, 0.8f)
    }
}
