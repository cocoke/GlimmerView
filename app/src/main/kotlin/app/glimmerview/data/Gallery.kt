package app.glimmerview.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import app.glimmerview.core.DepthMap
import app.glimmerview.format.DwpArchive
import app.glimmerview.format.DwpPackage
import kotlin.math.roundToInt

/** Œuvre exposée dans la galerie : une scène dessinée en plans, ou une œuvre de la bibliothèque. */
sealed interface Exhibit {
    val id: String
    val room: Room

    /** Scène dessinée à la volée : profondeur exacte, sans IA (Collectif GlimmerView, CC0). */
    class Drawn(val scene: Scene) : Exhibit {
        override val id get() = scene.id
        override val room get() = scene.room
    }

    /** Œuvre de la bibliothèque : un paquet .dwp livré avec l'app ou téléchargé à la demande. */
    class Packaged(val entry: LibraryEntry) : Exhibit {
        override val id get() = entry.id
        override val room get() = entry.room
    }
}

/** La galerie : les œuvres de la bibliothèque dans l'ordre de l'index, puis les scènes dessinées. */
object Gallery {
    fun exhibits(index: LibraryIndex): List<Exhibit> =
        index.works.map { Exhibit.Packaged(it) } + SceneArt.all.map { Exhibit.Drawn(it) }
}

/** Image et profondeur d'une œuvre de la galerie ; [packaged] : son paquet, si elle en vient. */
class GalleryContent(val color: Bitmap, val depth: DepthMap, val packaged: DwpPackage?)

/** Images des œuvres de la galerie : dessinées à la volée, ou tirées de la bibliothèque. */
class GallerySource(private val library: Library) {

    /**
     * Image au format demandé, recadrée au centre (cadres de la galerie, vignettes plein écran).
     * Une œuvre de la bibliothèque part de son aperçu, téléchargé s'il n'est pas encore là.
     */
    fun picture(exhibit: Exhibit, width: Int, height: Int): Bitmap = when (exhibit) {
        is Exhibit.Drawn -> SceneArt.renderColor(exhibit.scene, width, height)
        is Exhibit.Packaged -> cover(decode(library.preview(exhibit.entry), width, height), width, height)
    }

    /**
     * L'œuvre en pleine résolution, pour l'aperçu 3D, l'accrochage ou l'export. Le paquet d'une
     * œuvre de la bibliothèque est d'abord téléchargé s'il manque ([onProgress] : 0..1).
     */
    fun load(
        exhibit: Exhibit,
        width: Int,
        height: Int,
        depthWidth: Int,
        depthHeight: Int,
        onProgress: (Float) -> Unit = {},
    ): GalleryContent = when (exhibit) {
        is Exhibit.Drawn -> GalleryContent(
            SceneArt.renderColor(exhibit.scene, width, height),
            SceneArt.renderDepth(exhibit.scene, depthWidth, depthHeight),
            null,
        )
        is Exhibit.Packaged -> {
            library.download(exhibit.entry, onProgress)
            val pkg = library.open(exhibit.entry).use { DwpArchive.read(it) }
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
