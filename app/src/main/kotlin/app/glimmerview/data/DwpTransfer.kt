package app.glimmerview.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.Build
import androidx.core.graphics.scale
import app.glimmerview.BuildConfig
import app.glimmerview.R
import app.glimmerview.core.DepthMap
import app.glimmerview.core.ParallaxParams
import app.glimmerview.core.WorkInfo
import app.glimmerview.core.WorkLibrary
import app.glimmerview.core.WorkOrigin
import app.glimmerview.format.DwpArchive
import app.glimmerview.format.DwpAuthor
import app.glimmerview.format.DwpDefaults
import app.glimmerview.format.DwpException
import app.glimmerview.format.DwpFormat
import app.glimmerview.format.DwpGenerator
import app.glimmerview.format.DwpManifest
import app.glimmerview.ui.components.MAX_TITLE_LENGTH
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Échange des œuvres au format ouvert .dwp : ce que contient le paquet à l'export, et comment
 * un paquet reçu devient une œuvre de la collection (dimensions bornées, attribution conservée).
 */
class DwpTransfer(private val context: Context, private val library: WorkLibrary) {

    /** Paquet prêt à écrire. */
    class Content(val manifest: DwpManifest, val color: ByteArray, val depth: DepthMap, val thumbnail: ByteArray)

    /**
     * Œuvre de la collection, avec son titre, ses réglages et sa profondeur (retouchée ou non).
     * [scene] : la scène de la galerie dont elle est la copie, le cas échéant.
     */
    fun ofSaved(id: String, scene: Scene?): Content {
        val stored = checkNotNull(library.load(id)) { "Œuvre introuvable" }
        // Œuvre importée : on repart de son manifest d'origine (auteur, licence, source).
        val base = library.source(id)?.let { runCatching { DwpManifest.parse(it) }.getOrNull() }
            ?: scene?.let(::sceneManifest)
            ?: DwpManifest(
                medium = stored.info.medium,
                language = languageTag(),
                generator = DwpGenerator(app = APP, depthModel = DEPTH_MODEL),
            )
        val manifest = base.copy(
            id = base.id ?: id,
            title = stored.info.title,
            created = base.created ?: isoDate(stored.info.createdAt),
            width = stored.color.width,
            height = stored.color.height,
            defaults = defaultsOf(stored.params),
        )
        val color = library.colorBytes(id) ?: webp(stored.color, COLOR_QUALITY)
        return Content(manifest, color, stored.depth, thumbnail(stored.color))
    }

    /**
     * Œuvre de la galerie qui n'est pas dans la collection, telle qu'elle est affichée ([title] et
     * [medium] dans la langue de l'app). Une œuvre du pack garde son image et son attribution.
     */
    fun ofGallery(exhibit: Exhibit, title: String, medium: String, content: GalleryContent, params: ParallaxParams): Content {
        val packaged = content.packaged
        val base = packaged?.manifest ?: sceneManifest((exhibit as Exhibit.Drawn).scene)
        val manifest = base.copy(
            title = title,
            medium = medium,
            language = languageTag(),
            width = content.color.width,
            height = content.color.height,
            defaults = defaultsOf(params),
        )
        val color = packaged?.color ?: webp(content.color, COLOR_QUALITY)
        return Content(manifest, color, content.depth, thumbnail(content.color))
    }

    /** Écrit le paquet dans le cache, d'où il est partagé ; les exports précédents sont effacés. */
    fun writeToCache(content: Content): File {
        val dir = File(context.cacheDir, EXPORT_DIR)
        dir.deleteRecursively()
        dir.mkdirs()
        val file = File(dir, fileName(content.manifest.title))
        file.outputStream().buffered().use { write(content, it) }
        return file
    }

    fun write(content: Content, out: OutputStream) {
        DwpArchive.write(out, content.manifest, content.color, content.depth, content.thumbnail)
    }

    /**
     * Ajoute un paquet à la collection et renvoie sa fiche. L'image est ramenée au format des
     * créations ; [defaults] fournit les réglages que le paquet ne donne pas.
     */
    fun import(input: InputStream, thumbnailWidth: Int, defaults: (DepthMap) -> ParallaxParams): WorkInfo {
        val pkg = DwpArchive.read(input)
        val decoded = decodeColor(pkg.color)
        val color = decoded.bitmap
        val depth = pkg.depth.scaledToFit(MAX_DEPTH_SIDE)
        val manifest = pkg.manifest
        val fallback = defaults(depth)
        val suggested = manifest.defaults
        val params = fallback.copy(
            strength = suggested?.strength?.coerceIn(MIN_STRENGTH, MAX_STRENGTH) ?: fallback.strength,
            focus = suggested?.focus?.coerceIn(0f, 1f) ?: fallback.focus,
            zoom = suggested?.zoom?.coerceIn(1f, MAX_ZOOM) ?: fallback.zoom,
        )
        val author = manifest.author?.name?.let(::clean)?.take(MAX_TEXT)
        val info = WorkInfo(
            id = WorkLibrary.newId(),
            title = manifest.title?.let(::clean)?.take(MAX_TITLE_LENGTH)?.trim()?.ifEmpty { null }
                ?: context.getString(R.string.work_untitled_plain),
            artist = when (author) {
                null, "" -> context.getString(R.string.artist_anonymous)
                COLLECTIVE -> context.getString(R.string.artist_collective)
                else -> author
            },
            medium = manifest.medium?.let(::clean)?.take(MAX_TEXT)?.ifEmpty { null }
                ?: context.getString(R.string.medium_imported),
            license = manifest.license?.let(::licenseLabel) ?: context.getString(R.string.license_personal),
            origin = WorkOrigin.IMPORTED,
            createdAt = System.currentTimeMillis(),
            width = color.width,
            height = color.height,
        )
        val thumbnail = if (color.width > thumbnailWidth) {
            color.scale(thumbnailWidth, max(1, (color.height * thumbnailWidth.toFloat() / color.width).roundToInt()))
        } else {
            color
        }
        // Une image WebP qui n'a pas été réduite est gardée telle quelle : pas de perte à chaque aller-retour.
        val original = pkg.color.takeIf { !decoded.resized && isWebp(it) }
        return library.save(info, color, depth, params, thumbnail, source = manifest.toJson(), colorBytes = original)
    }

    private fun sceneManifest(scene: Scene) = DwpManifest(
        id = SCENE_ID_PREFIX + scene.id,
        author = DwpAuthor(COLLECTIVE),
        license = CC0,
        aiGenerated = false,
        medium = context.resources.getQuantityString(R.plurals.medium_scene, scene.planes, scene.planes),
        language = languageTag(),
        generator = DwpGenerator(app = APP),
        tags = listOf(scene.room.name.lowercase(Locale.ROOT)),
    )

    private fun defaultsOf(params: ParallaxParams) =
        DwpDefaults(strength = params.strength, focus = params.focus, zoom = params.zoom, motion = "gyro")

    private class Decoded(val bitmap: Bitmap, val resized: Boolean)

    /** Image principale d'un paquet : dimensions vérifiées avant de décoder, puis réduites au besoin. */
    private fun decodeColor(bytes: ByteArray): Decoded {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) throw DwpException(DwpException.Reason.CORRUPT, "Image principale illisible")
        if (width > MAX_SOURCE_SIDE || height > MAX_SOURCE_SIDE || width.toLong() * height > MAX_SOURCE_PIXELS) {
            throw DwpException(DwpException.Reason.TOO_LARGE, "Image principale de ${width}×$height")
        }
        var sample = 1
        while (max(width, height) / (sample * 2) >= MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: throw DwpException(DwpException.Reason.CORRUPT, "Image principale illisible")
        val longSide = max(bitmap.width, bitmap.height)
        if (longSide <= MAX_SIDE) return Decoded(bitmap, resized = sample > 1)
        val scaled = bitmap.scale(
            max(1, (bitmap.width.toLong() * MAX_SIDE / longSide).toInt()),
            max(1, (bitmap.height.toLong() * MAX_SIDE / longSide).toInt()),
        )
        return Decoded(scaled, resized = true)
    }

    private fun isWebp(bytes: ByteArray): Boolean =
        bytes.size > 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"

    /** Vignette du paquet : l'œuvre recadrée au format d'un écran de téléphone. */
    private fun thumbnail(color: Bitmap): ByteArray {
        val width = DwpFormat.THUMBNAIL_WIDTH
        val height = DwpFormat.THUMBNAIL_HEIGHT
        val aspect = width.toFloat() / height
        val crop = if (color.width.toFloat() / color.height > aspect) {
            val w = (color.height * aspect).roundToInt()
            Rect((color.width - w) / 2, 0, (color.width + w) / 2, color.height)
        } else {
            val h = (color.width / aspect).roundToInt()
            Rect(0, (color.height - h) / 2, color.width, (color.height + h) / 2)
        }
        var source = Bitmap.createBitmap(color, crop.left, crop.top, crop.width(), crop.height())
        // Réduction par moitiés : un seul grand saut bilinéaire crénelerait la vignette.
        while (source.width >= width * 2) source = source.scale(source.width / 2, source.height / 2)
        return webp(source.scale(width, height), THUMBNAIL_QUALITY)
    }

    private fun webp(bitmap: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().also { bitmap.compress(webpFormat(), quality, it) }.toByteArray()

    @Suppress("DEPRECATION")
    private fun webpFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP

    private fun languageTag(): String = context.resources.configuration.locales[0].toLanguageTag()

    private fun isoDate(millis: Long): String = Instant.ofEpochMilli(millis).truncatedTo(ChronoUnit.SECONDS).toString()

    /** « CC-BY-SA-4.0 » devient « CC BY-SA 4.0 », comme sur les cartels. */
    private fun licenseLabel(spdx: String): String {
        val id = clean(spdx).take(MAX_LICENSE)
        val version = id.lastIndexOf('-')
        return when {
            id.equals(CC0, ignoreCase = true) -> context.getString(R.string.license_cc0)
            id.equals(PUBLIC_DOMAIN, ignoreCase = true) -> context.getString(R.string.license_public_domain)
            id.startsWith("CC-", ignoreCase = true) && version > 3 -> "CC " + id.substring(3, version) + " " + id.substring(version + 1)
            else -> id
        }
    }

    /** Texte venu d'un fichier : une seule ligne, sans caractère de contrôle. */
    private fun clean(text: String): String = text.replace(Regex("[\\p{Cntrl}\\s]+"), " ").trim()

    companion object {
        /** Nom de fichier lisible tiré du titre (« Crêtes à l'aube.dwp »). */
        fun fileName(title: String?): String {
            val name = (title ?: "")
                .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]+"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .trimStart('.')
                .take(MAX_TITLE_LENGTH)
                .trim()
            return name.ifEmpty { "GlimmerView" } + "." + DwpFormat.EXTENSION
        }

        private val APP = "GlimmerView ${BuildConfig.VERSION_NAME}"
        private const val DEPTH_MODEL = "depth-anything-v2-small"
        private const val COLLECTIVE = "GlimmerView Collective"
        private const val CC0 = "CC0-1.0"
        private const val PUBLIC_DOMAIN = "public-domain"
        private const val SCENE_ID_PREFIX = "glimmerview-"
        private const val EXPORT_DIR = "exports"
        private const val COLOR_QUALITY = 95
        private const val THUMBNAIL_QUALITY = 85

        /** Mêmes bornes que les photos des créations. */
        private const val MAX_SIDE = 2400
        private const val MAX_DEPTH_SIDE = 1600
        private const val MAX_SOURCE_SIDE = 16_384
        private const val MAX_SOURCE_PIXELS = 100_000_000L

        private const val MIN_STRENGTH = 0.005f
        private const val MAX_STRENGTH = 0.1f
        private const val MAX_ZOOM = 1.5f
        private const val MAX_TEXT = 120
        private const val MAX_LICENSE = 40
    }
}
