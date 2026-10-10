package app.glimmerview.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.StringRes
import androidx.core.graphics.scale
import app.glimmerview.R
import app.glimmerview.core.DepthMap
import app.glimmerview.format.DwpArchive
import app.glimmerview.format.DwpImage
import app.glimmerview.format.DwpPackage
import kotlin.math.roundToInt

/** Œuvre exposée dans la galerie : une scène dessinée en plans, ou une œuvre du pack de démarrage. */
sealed interface Exhibit {
    val id: String
    val room: Room

    @get:StringRes
    val title: Int

    /** Scène dessinée à la volée : profondeur exacte, sans IA (Collectif GlimmerView, CC0). */
    class Drawn(val scene: Scene) : Exhibit {
        override val id get() = scene.id
        override val room get() = scene.room
        override val title get() = scene.title
    }

    /**
     * Œuvre du domaine public livrée avec l'app, dans un paquet .dwp des assets
     * (`gallery/<id>.dwp`, construit par tools/pack/build_pack.py).
     */
    class Packaged(
        override val id: String,
        override val room: Room,
        @StringRes override val title: Int,
        val artist: String,
        @StringRes val medium: Int,
        /** Date affichée après le titre (« 1889 », ou « 1830–1832 » précédé de « vers »). */
        val year: String,
        val circa: Boolean = false,
        /** Œuvre d'art recadrée au format de l'écran : « (détail) » sur le cartel. */
        val detail: Boolean = false,
        val publicDomain: Boolean = false,
    ) : Exhibit {
        val asset: String get() = "gallery/$id.dwp"
    }
}

/** La galerie : d'abord le pack de démarrage (musées et NASA), puis les scènes dessinées. */
object Gallery {

    private val WOODBLOCK = R.string.medium_woodblock
    private val OIL = R.string.medium_oil
    private val PHOTO = R.string.medium_digital_photo

    private val packaged = listOf(
        Exhibit.Packaged("met-39799", Room.ART, R.string.exhibit_wave, "Katsushika Hokusai", WOODBLOCK, "1830–1832", circa = true, detail = true),
        Exhibit.Packaged("nasa-art002e021278", Room.SPACE, R.string.exhibit_earthset, "NASA", PHOTO, "2026", publicDomain = true),
        Exhibit.Packaged("met-437980", Room.NATURE, R.string.exhibit_cypresses, "Vincent van Gogh", OIL, "1889", detail = true),
        Exhibit.Packaged("met-437310", Room.CITIES, R.string.exhibit_montmartre, "Camille Pissarro", OIL, "1897", detail = true),
        Exhibit.Packaged("met-36539", Room.ART, R.string.exhibit_asakusa, "Utagawa Hiroshige", WOODBLOCK, "1857", detail = true),
        Exhibit.Packaged("nasa-iss073e0039722", Room.SPACE, R.string.exhibit_arrays, "NASA", PHOTO, "2025", publicDomain = true),
        Exhibit.Packaged("met-37096", Room.NATURE, R.string.exhibit_maples, "Utagawa Hiroshige", WOODBLOCK, "1857", detail = true),
        Exhibit.Packaged("met-435839", Room.CITIES, R.string.exhibit_san_marco, "Canaletto", OIL, "1725–1730", circa = true, detail = true),
        Exhibit.Packaged("met-55733", Room.NATURE, R.string.exhibit_eagle, "Utagawa Hiroshige", WOODBLOCK, "1856", detail = true),
        Exhibit.Packaged("met-438417", Room.ART, R.string.exhibit_moon, "Caspar David Friedrich", OIL, "1825–1830", circa = true, detail = true),
        Exhibit.Packaged("met-55433", Room.CITIES, R.string.exhibit_shower, "Utagawa Hiroshige", WOODBLOCK, "1857", detail = true),
        Exhibit.Packaged("met-10497", Room.NATURE, R.string.exhibit_oxbow, "Thomas Cole", OIL, "1836", detail = true),
    )

    val all: List<Exhibit> by lazy { packaged + SceneArt.all.map { Exhibit.Drawn(it) } }

    fun find(id: String): Exhibit? = all.firstOrNull { it.id == id }
}

/** Image et profondeur d'une œuvre de la galerie ; [packaged] : son paquet, si elle en vient. */
class GalleryContent(val color: Bitmap, val depth: DepthMap, val packaged: DwpPackage?)

/** Images des œuvres de la galerie : dessinées à la volée, ou lues dans les paquets des assets. */
class GallerySource(private val context: Context) {

    /** Image au format demandé, recadrée au centre (cadres de la galerie, vignettes plein écran). */
    fun picture(exhibit: Exhibit, width: Int, height: Int): Bitmap = when (exhibit) {
        is Exhibit.Drawn -> SceneArt.renderColor(exhibit.scene, width, height)
        is Exhibit.Packaged -> cover(decode(image(exhibit).color, width, height), width, height)
    }

    /** Fiche et image du paquet d'une œuvre du pack, sans décoder sa profondeur. */
    fun image(exhibit: Exhibit.Packaged): DwpImage = context.assets.open(exhibit.asset).use { DwpArchive.readImage(it) }

    /** L'œuvre en pleine résolution, pour l'aperçu 3D, l'accrochage ou l'export. */
    fun load(exhibit: Exhibit, width: Int, height: Int, depthWidth: Int, depthHeight: Int): GalleryContent = when (exhibit) {
        is Exhibit.Drawn -> GalleryContent(
            SceneArt.renderColor(exhibit.scene, width, height),
            SceneArt.renderDepth(exhibit.scene, depthWidth, depthHeight),
            null,
        )
        is Exhibit.Packaged -> {
            val pkg = context.assets.open(exhibit.asset).use { DwpArchive.read(it) }
            val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val color = checkNotNull(BitmapFactory.decodeByteArray(pkg.color, 0, pkg.color.size, options)) { "Image illisible" }
            GalleryContent(color, pkg.depth, pkg)
        }
    }

    /** Décode en sautant des pixels tant que l'image reste au moins aussi grande que le format visé. */
    private fun decode(bytes: ByteArray, width: Int, height: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)) { "Image illisible" }
    }

    /** Recadre au centre au format width × height, puis met à l'échelle. */
    private fun cover(source: Bitmap, width: Int, height: Int): Bitmap {
        val aspect = width.toFloat() / height
        val (cropWidth, cropHeight) = if (source.width.toFloat() / source.height > aspect) {
            (source.height * aspect).roundToInt() to source.height
        } else {
            source.width to (source.width / aspect).roundToInt()
        }
        val cropped = Bitmap.createBitmap(source, (source.width - cropWidth) / 2, (source.height - cropHeight) / 2, cropWidth, cropHeight)
        return cropped.scale(width, height)
    }
}
