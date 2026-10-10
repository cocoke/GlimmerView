package app.glimmerview

import android.app.Application
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.util.Log
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
import app.glimmerview.data.DepthSketch
import app.glimmerview.data.DwpTransfer
import app.glimmerview.data.Exhibit
import app.glimmerview.data.Gallery
import app.glimmerview.data.GalleryContent
import app.glimmerview.data.GallerySource
import app.glimmerview.data.ImageLoading
import app.glimmerview.data.SceneArt
import app.glimmerview.format.DwpArchive
import app.glimmerview.format.DwpException
import app.glimmerview.ml.Backend
import app.glimmerview.ml.DepthEstimator
import app.glimmerview.ml.DepthPostProcessor
import app.glimmerview.ml.InputSize
import app.glimmerview.ml.ModelCatalog
import app.glimmerview.ml.ModelStore
import app.glimmerview.ml.RawDepth
import app.glimmerview.ui.components.MAX_TITLE_LENGTH
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
import java.io.BufferedInputStream
import java.io.File
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
    /** Date affichée après le titre (œuvres de la galerie), null pour les créations datées. */
    val year: String?,
    /** Null pour une œuvre de la galerie qui n'est pas (encore) dans la collection. */
    val origin: WorkOrigin?,
)

/** Œuvre chargée en pleine résolution pour l'aperçu 3D. */
class LoadedWork(val ref: WorkRef, val content: PreviewContent)

/** Retouche de la profondeur en cours (écran Esquisse). */
class SketchSession(val ref: WorkRef.Saved, val color: Bitmap, val sketch: DepthSketch)

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
    /** Titre donné par défaut à l'œuvre (« Sans titre, 4 oct. 2026 »), proposé en indice. */
    val defaultTitle: String? = null,
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

    val exhibits: List<Exhibit> = Gallery.all
    private val gallerySource = GallerySource(application)

    /** Vignettes plein écran (format 9:20), comme l'œuvre accrochée. */
    val galleryThumbs = mutableStateMapOf<String, ImageBitmap>()

    /** Vignettes au format des cadres : une scène est recomposée (tous ses plans restent visibles), une œuvre du pack recadrée. */
    val galleryFrames = mutableStateMapOf<String, ImageBitmap>()

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
            exhibits.map { exhibit ->
                async(Dispatchers.Default) { exhibit.id to picture(exhibit, FRAME_WIDTH, FRAME_HEIGHT) }
            }.awaitAll().forEach { (id, frame) -> frame?.let { galleryFrames[id] = it.asImageBitmap() } }
            exhibits.map { exhibit ->
                async(Dispatchers.Default) { exhibit.id to picture(exhibit, PREVIEW_THUMB_WIDTH, PREVIEW_THUMB_HEIGHT) }
            }.awaitAll().forEach { (id, thumb) -> thumb?.let { galleryThumbs[id] = it.asImageBitmap() } }
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
        if (popped is Route.Esquisse) sketch = null
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
        is WorkRef.Gallery -> Gallery.find(ref.id)?.let(::galleryCard)
        is WorkRef.Saved -> works.firstOrNull { it.id == ref.id }?.let(::savedCard)
    }

    fun thumbnail(ref: WorkRef): ImageBitmap? = when (ref) {
        is WorkRef.Gallery -> galleryThumbs[ref.id]
        is WorkRef.Saved -> workThumbs[ref.id] ?: exhibitIdOf(ref.id)?.let { galleryThumbs[it] }
    }

    /** Image d'un cadre : les scènes au format du cadre, les photos recadrées au centre. */
    fun frameImage(ref: WorkRef): ImageBitmap? = when (ref) {
        is WorkRef.Gallery -> galleryFrames[ref.id]
        is WorkRef.Saved -> exhibitIdOf(ref.id)?.let { galleryFrames[it] } ?: workThumbs[ref.id]
    }

    fun galleryCard(exhibit: Exhibit) = WorkCard(
        ref = WorkRef.Gallery(exhibit.id),
        title = context.getString(exhibit.title).let {
            if ((exhibit as? Exhibit.Packaged)?.detail == true) context.getString(R.string.exhibit_detail, it) else it
        },
        artist = when (exhibit) {
            is Exhibit.Drawn -> context.getString(R.string.artist_collective)
            is Exhibit.Packaged -> exhibit.artist
        },
        medium = when (exhibit) {
            is Exhibit.Drawn -> context.resources.getQuantityString(R.plurals.medium_scene, exhibit.scene.planes, exhibit.scene.planes)
            is Exhibit.Packaged -> context.getString(exhibit.medium)
        },
        license = when {
            exhibit !is Exhibit.Packaged -> context.getString(R.string.license_cc0)
            exhibit.publicDomain -> context.getString(R.string.license_public_domain)
            else -> exhibit.license
        },
        year = yearOf(exhibit),
        origin = if (isCollected(exhibit.id)) WorkOrigin.ACQUIRED else null,
    )

    private fun yearOf(exhibit: Exhibit): String = when (exhibit) {
        is Exhibit.Drawn -> SCENE_YEAR.toString()
        is Exhibit.Packaged -> if (exhibit.circa) context.getString(R.string.year_circa, exhibit.year) else exhibit.year
    }

    /** Image d'une œuvre de la galerie au format demandé ; null si son paquet est illisible. */
    private fun picture(exhibit: Exhibit, width: Int, height: Int): Bitmap? =
        runCatching { gallerySource.picture(exhibit, width, height) }
            .onFailure { Log.w(TAG, "Œuvre de la galerie illisible : ${exhibit.id}", it) }
            .getOrNull()

    /** Réglages par défaut d'une œuvre de la galerie : pour le pack, le plan immobile conseillé par son paquet. */
    private fun galleryParams(id: String): ParallaxParams {
        val exhibit = Gallery.find(id) as? Exhibit.Packaged
        val focus = exhibit?.let { runCatching { gallerySource.image(it).manifest.defaults?.focus }.getOrNull() }
        return defaultParams(focus ?: FocusScale.focus(3))
    }

    private fun savedCard(info: WorkInfo) = WorkCard(
        ref = WorkRef.Saved(info.id),
        title = info.title,
        artist = info.artist,
        medium = info.medium,
        license = info.license,
        year = exhibitIdOf(info.id)?.let(Gallery::find)?.let(::yearOf),
        origin = info.origin,
    )

    fun isCollected(exhibitId: String): Boolean = works.any { it.id == GALLERY_PREFIX + exhibitId }

    /** Vrai si l'œuvre (ou sa copie dans la collection) est accrochée en fond d'écran. */
    fun isOnDisplay(ref: WorkRef): Boolean = currentWorkId != null && currentWorkId == when (ref) {
        is WorkRef.Saved -> ref.id
        is WorkRef.Gallery -> GALLERY_PREFIX + ref.id
    }

    private fun exhibitIdOf(savedId: String): String? = savedId.removePrefix(GALLERY_PREFIX).takeIf { savedId.startsWith(GALLERY_PREFIX) }

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
        is WorkRef.Gallery -> withContext(Dispatchers.Default) {
            val exhibit = checkNotNull(Gallery.find(ref.id)) { "Œuvre inconnue : ${ref.id}" }
            val content = loadExhibit(exhibit)
            val focus = content.packaged?.manifest?.defaults?.focus ?: FocusScale.focus(3)
            LoadedWork(ref, PreviewContent("gallery:${exhibit.id}", content.color, content.depth)) to defaultParams(focus)
        }
        is WorkRef.Saved -> withContext(Dispatchers.IO) {
            val stored = checkNotNull(library.load(ref.id)) { "Œuvre introuvable" }
            LoadedWork(ref, PreviewContent("work:${ref.id}", stored.color, stored.depth)) to stored.params
        }
    }

    private fun loadExhibit(exhibit: Exhibit): GalleryContent =
        gallerySource.load(exhibit, SCENE_WIDTH, SCENE_HEIGHT, SCENE_DEPTH_WIDTH, SCENE_DEPTH_HEIGHT)

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
    fun toggleCollected(ref: WorkRef.Gallery) {
        viewModelScope.launch {
            val id = GALLERY_PREFIX + ref.id
            if (isCollected(ref.id)) {
                withContext(Dispatchers.IO) { library.delete(id) }
                message = context.getString(R.string.message_removed)
            } else {
                acquire(ref, if (preview?.ref == ref) previewParams else withContext(Dispatchers.IO) { galleryParams(ref.id) })
                message = context.getString(R.string.message_collected)
            }
            refreshLibrary()
        }
    }

    // ── Esquisse : retouche de la profondeur ──────────────────────────────────

    var sketch by mutableStateOf<SketchSession?>(null)
        private set

    /**
     * Ouvre la retouche. Une œuvre de la galerie est d'abord copiée dans la collection : c'est
     * cette copie qui est retouchée, et la mise au point d'où l'on vient bascule dessus.
     */
    fun retouch(ref: WorkRef) {
        viewModelScope.launch {
            val params = if (preview?.ref == ref) previewParams else withContext(Dispatchers.IO) { savedParamsOrDefault(ref) }
            val id = runCatching { acquire(ref, params) }.getOrElse {
                message = it.message ?: it.javaClass.simpleName
                return@launch
            }
            val saved = WorkRef.Saved(id)
            if (ref is WorkRef.Gallery) {
                refreshLibrary()
                val top = backStack.lastOrNull()
                if (top is Route.MiseAuPoint && top.ref == ref) backStack[backStack.lastIndex] = Route.MiseAuPoint(saved)
            }
            sketch = null
            navigate(Route.Esquisse(saved))
            sketch = withContext(Dispatchers.IO) {
                val stored = checkNotNull(library.load(id)) { "Œuvre introuvable" }
                val original = library.originalDepth(id)
                    ?.takeIf { it.width == stored.depth.width && it.height == stored.depth.height }
                    ?: stored.depth
                SketchSession(saved, stored.color, DepthSketch(stored.depth.width, stored.depth.height, stored.depth.values.copyOf(), original.values))
            }
        }
    }

    /** Enregistre la profondeur retouchée ; l'œuvre accrochée se met à jour sur l'écran d'accueil. */
    fun saveSketch() {
        val session = sketch ?: return
        val id = session.ref.id
        viewModelScope.launch {
            val depth = DepthMap(session.sketch.width, session.sketch.height, session.sketch.result())
            val params = withContext(Dispatchers.IO) {
                library.saveDepth(id, depth)
                val params = library.params(id)
                if (library.currentId() == id) wallpaperStore.save(session.color, depth, params)
                params
            }
            // Nouveau contenu (nouvelle clé) : l'aperçu 3D recharge la profondeur retouchée.
            preview = LoadedWork(session.ref, PreviewContent("work:$id:${System.nanoTime()}", session.color, depth))
            previewParams = params
            sketch = null
            message = context.getString(R.string.message_sketch_saved)
            back()
        }
    }

    fun discardSketch() {
        sketch = null
        back()
    }

    /** Œuvre dont le panneau « Renommer » est ouvert (par-dessus l'écran courant). */
    var renaming by mutableStateOf<String?>(null)
        private set

    fun startRename(id: String) {
        renaming = id
    }

    fun cancelRename() {
        renaming = null
    }

    /** Renomme une œuvre de la collection : affiché tout de suite, enregistré en arrière-plan. */
    fun renameWork(id: String, title: String) {
        val clean = title.trim().replace(Regex("\\s+"), " ").take(MAX_TITLE_LENGTH)
        if (clean.isEmpty()) return
        works = works.map { if (it.id == id) it.copy(title = clean) else it }
        viewModelScope.launch(Dispatchers.IO) { library.rename(id, clean) }
    }

    /**
     * Retire une œuvre de la collection (une création est supprimée, une acquisition rendue à la
     * galerie). [leave] referme l'écran de l'œuvre d'où l'on vient ; le menu d'appui long reste sur place.
     */
    fun deleteWork(id: String, leave: Boolean = true) {
        val acquired = works.firstOrNull { it.id == id }?.origin == WorkOrigin.ACQUIRED
        viewModelScope.launch {
            withContext(Dispatchers.IO) { library.delete(id) }
            if (preview?.ref == WorkRef.Saved(id)) preview = null
            refreshLibrary()
            message = context.getString(if (acquired) R.string.message_removed else R.string.message_deleted)
            if (leave) back()
        }
    }

    // ── Menu d'une œuvre (appui long sur un cadre) ───────────────────────────

    /** Œuvre dont le menu d'actions est ouvert, par-dessus la salle courante. */
    var actionsFor by mutableStateOf<WorkRef?>(null)
        private set

    fun showActions(ref: WorkRef) {
        actionsFor = ref
    }

    fun hideActions() {
        actionsFor = null
    }

    // ── Échange au format .dwp ────────────────────────────────────────────────

    private val transfer = DwpTransfer(application, library)

    /** Œuvre dont le panneau « Exporter » est ouvert. */
    var exporting by mutableStateOf<WorkRef?>(null)
        private set

    /** Un fichier reçu est en cours de lecture. */
    var importing by mutableStateOf(false)
        private set

    fun startExport(ref: WorkRef) {
        exporting = ref
    }

    fun cancelExport() {
        exporting = null
    }

    /** Nom proposé pour le fichier (« Crêtes à l'aube.dwp »). */
    fun exportFileName(ref: WorkRef): String = DwpTransfer.fileName(card(ref)?.title)

    /** Écrit le paquet dans le cache pour le partager ; null (et un message) en cas d'échec. */
    suspend fun prepareShare(ref: WorkRef): File? {
        val content = exportContent(ref)
        return try {
            withContext(Dispatchers.IO) { transfer.writeToCache(content()) }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            exportFailed(t)
            null
        }
    }

    /** « Enregistrer sur le téléphone » : le paquet est écrit dans le document créé par l'utilisateur. */
    fun saveExport(ref: WorkRef, uri: Uri) {
        val content = exportContent(ref)
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val out = checkNotNull(context.contentResolver.openOutputStream(uri)) { "Écriture impossible" }
                    out.use { transfer.write(content(), it) }
                }
            }
                .onSuccess { message = context.getString(R.string.message_export_saved) }
                .onFailure(::exportFailed)
        }
    }

    /** Lit l'état de l'aperçu ici (thread principal) ; le paquet est assemblé plus tard, en arrière-plan. */
    private fun exportContent(ref: WorkRef): () -> DwpTransfer.Content = when (ref) {
        is WorkRef.Saved -> {
            val scene = (exhibitIdOf(ref.id)?.let(Gallery::find) as? Exhibit.Drawn)?.scene
            val build: () -> DwpTransfer.Content = { transfer.ofSaved(ref.id, scene) }
            build
        }
        is WorkRef.Gallery -> {
            val loaded = preview?.takeIf { it.ref == ref }
            val current = previewParams
            val build: () -> DwpTransfer.Content = {
                val exhibit = checkNotNull(Gallery.find(ref.id)) { "Œuvre inconnue : ${ref.id}" }
                val card = galleryCard(exhibit)
                // Une œuvre du pack repart de son paquet (image sans réencodage, attribution d'origine).
                val content = if (exhibit is Exhibit.Drawn && loaded != null) {
                    GalleryContent(loaded.content.color, loaded.content.depth, null)
                } else {
                    loadExhibit(exhibit)
                }
                val params = if (loaded != null) current else galleryParams(exhibit.id)
                transfer.ofGallery(exhibit, card.title, card.medium, content, params)
            }
            build
        }
    }

    private fun exportFailed(t: Throwable) {
        Log.w(TAG, "Export .dwp impossible", t)
        message = context.getString(R.string.message_export_failed, t.message ?: t.javaClass.simpleName)
    }

    /**
     * Fichier reçu (« Ouvrir avec », partage, atelier) : un paquet .dwp rejoint la collection et
     * s'ouvre ; une image part au tirage ; le reste est refusé avec une explication.
     */
    fun openFile(uri: Uri) {
        if (importing) return
        importing = true
        viewModelScope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    val input = checkNotNull(context.contentResolver.openInputStream(uri)) { "Fichier illisible" }
                    BufferedInputStream(input).use { stream ->
                        if (DwpArchive.looksLikeZip(stream)) {
                            transfer.import(stream, THUMB_WIDTH) { depth -> defaultParams(subjectFocus(depth)) }
                        } else {
                            null
                        }
                    }
                }
            }
            importing = false
            outcome
                .onSuccess { info ->
                    when {
                        info != null -> {
                            prefs.edit { putBoolean(KEY_ONBOARDED, true) }
                            refreshLibrary()
                            selectTab(Route.Collection)
                            openWork(WorkRef.Saved(info.id))
                            message = context.getString(R.string.message_imported)
                        }
                        isImage(uri) -> startSharedCreation(uri)
                        else -> message = context.getString(R.string.import_error_not_dwp)
                    }
                }
                .onFailure {
                    Log.w(TAG, "Import .dwp impossible", it)
                    message = context.getString(importError(it))
                }
        }
    }

    private suspend fun isImage(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        if (context.contentResolver.getType(uri)?.startsWith("image/") == true) return@withContext true
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } }
        bounds.outWidth > 0
    }

    private fun importError(t: Throwable): Int = when ((t as? DwpException)?.reason) {
        DwpException.Reason.NOT_DWP -> R.string.import_error_not_dwp
        DwpException.Reason.UNSUPPORTED_VERSION -> R.string.import_error_version
        DwpException.Reason.TOO_LARGE -> R.string.import_error_too_large
        DwpException.Reason.MISSING_ENTRY, DwpException.Reason.CORRUPT -> R.string.import_error_corrupt
        null -> R.string.import_error_unreadable
    }

    /** Copie une œuvre de la galerie dans la collection (identifiant stable) et renvoie son identifiant. */
    private suspend fun acquire(ref: WorkRef, params: ParallaxParams): String = when (ref) {
        is WorkRef.Saved -> ref.id
        is WorkRef.Gallery -> withContext(Dispatchers.Default) {
            val id = GALLERY_PREFIX + ref.id
            if (library.contains(id)) {
                library.saveParams(id, params)
            } else {
                val exhibit = checkNotNull(Gallery.find(ref.id)) { "Œuvre inconnue : ${ref.id}" }
                val loaded = preview?.takeIf { it.ref == ref }?.content
                // Une œuvre du pack est copiée depuis son paquet : image d'origine, attribution conservée pour l'export.
                val content = if (exhibit is Exhibit.Drawn && loaded != null) {
                    GalleryContent(loaded.color, loaded.depth, null)
                } else {
                    loadExhibit(exhibit)
                }
                val color = content.color
                val depth = content.depth
                val card = galleryCard(exhibit)
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
                val thumbnail = when (exhibit) {
                    is Exhibit.Drawn -> SceneArt.renderColor(exhibit.scene, THUMB_WIDTH, THUMB_HEIGHT)
                    is Exhibit.Packaged -> scaleToWidth(color, THUMB_WIDTH)
                }
                val pkg = content.packaged
                library.save(info, color, depth, params, thumbnail, source = pkg?.manifest?.toJson(), colorBytes = pkg?.color)
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
        is WorkRef.Gallery -> galleryParams(ref.id)
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

    /**
     * Photo reçue d'une autre app : on passe le vernissage s'il n'a pas eu lieu et on ouvre
     * le tirage depuis l'atelier (le retour y ramène).
     */
    fun startSharedCreation(uri: Uri) {
        prefs.edit { putBoolean(KEY_ONBOARDED, true) }
        selectTab(Route.Atelier)
        startCreation(uri)
    }

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
                update { copy(stage = GenStage.DONE, resultId = info.id, defaultTitle = info.title) }
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

    /** Fin du tirage : l'œuvre reçoit son titre, la mise au point remplace l'écran de tirage dans la pile. */
    fun openCreation(title: String = "") {
        val id = generation?.resultId ?: return
        if (title.isNotBlank()) renameWork(id, title)
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
        private const val TAG = "GlimmerView"
        private const val KEY_ONBOARDED = "onboarded"
        private const val GALLERY_PREFIX = "scene-"
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
