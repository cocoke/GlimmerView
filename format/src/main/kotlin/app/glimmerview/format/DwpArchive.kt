package app.glimmerview.format

import app.glimmerview.core.DepthMap
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Contenu d'un paquet lu et vérifié. */
class DwpPackage(
    val manifest: DwpManifest,
    /** Image principale telle qu'elle est stockée dans l'archive (WebP, PNG ou JPEG). */
    val color: ByteArray,
    val depth: DepthMap,
    /** Vignette, si le paquet en a une. */
    val thumbnail: ByteArray?,
)

/** Fiche et image principale d'un paquet, sans sa profondeur. */
class DwpImage(val manifest: DwpManifest, val color: ByteArray)

/** Lecture et écriture des archives `.dwp`. */
object DwpArchive {

    /**
     * Écrit un paquet à un seul calque : manifest.json (compressé), puis l'image ([color], en
     * WebP), la profondeur en PNG 16 bits et la vignette, stockées sans recompression.
     * Les noms des fichiers du manifest sont fixés ici. Le flux n'est pas fermé.
     */
    fun write(out: OutputStream, manifest: DwpManifest, color: ByteArray, depth: DepthMap, thumbnail: ByteArray? = null) {
        val complete = manifest.copy(
            layers = listOf(DwpLayer(DwpFormat.COLOR, DwpFormat.DEPTH)),
            thumbnail = thumbnail?.let { DwpFormat.THUMBNAIL },
        )
        val zip = ZipOutputStream(out)
        zip.putNextEntry(ZipEntry(DwpFormat.MANIFEST))
        zip.write(complete.toJson().toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        zip.putStored(DwpFormat.COLOR, color)
        zip.putStored(DwpFormat.DEPTH, GrayPng.encode(depth))
        thumbnail?.let { zip.putStored(DwpFormat.THUMBNAIL, it) }
        zip.finish()
    }

    /**
     * Lit et vérifie un paquet. Chaque fichier est lu en entier, dans les limites de [limits] :
     * une archive piégée est arrêtée au premier dépassement, avant d'occuper la mémoire.
     * Le flux n'est pas fermé.
     */
    fun read(input: InputStream, limits: DwpLimits = DwpLimits()): DwpPackage {
        val files = entries(input, limits)
        val manifest = manifestOf(files)
        val layer = manifest.mainLayer
        val color = files[layer.color] ?: missing(layer.color)
        val depthBytes = files[layer.depth] ?: missing(layer.depth)
        val depth = GrayPng.decode(depthBytes, limits.maxDepthSide, limits.maxDepthPixels)
        return DwpPackage(manifest, color, depth, manifest.thumbnail?.let(files::get))
    }

    /** Fiche et image principale seulement, sans décoder la profondeur : pour les vignettes. Le flux n'est pas fermé. */
    fun readImage(input: InputStream, limits: DwpLimits = DwpLimits()): DwpImage {
        val files = entries(input, limits)
        val manifest = manifestOf(files)
        return DwpImage(manifest, files[manifest.mainLayer.color] ?: missing(manifest.mainLayer.color))
    }

    /** Tous les fichiers de l'archive, lus dans les limites de [limits]. */
    private fun entries(input: InputStream, limits: DwpLimits): Map<String, ByteArray> {
        val source = BufferedInputStream(input)
        if (!looksLikeZip(source)) throw DwpException(DwpException.Reason.NOT_DWP, "Ce fichier n'est pas une archive ZIP")
        val files = HashMap<String, ByteArray>()
        try {
            val zip = ZipInputStream(LimitedInputStream(source, limits.maxArchiveBytes))
            var count = 0
            var total = 0L
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++count > limits.maxEntries) {
                    throw DwpException(DwpException.Reason.TOO_LARGE, "Plus de ${limits.maxEntries} fichiers dans l'archive")
                }
                if (entry.isDirectory) continue
                // Le plafond tient compte de ce qui a déjà été lu : on s'arrête dès que l'un des deux est atteint.
                val cap = minOf(limits.maxEntryBytes.toLong(), limits.maxTotalBytes - total).toInt()
                val bytes = readEntry(zip, cap) ?: throw DwpException(
                    DwpException.Reason.TOO_LARGE,
                    if (cap < limits.maxEntryBytes) {
                        "Archive de plus de ${limits.maxTotalBytes shr 20} Mo une fois décompressée"
                    } else {
                        "« ${entry.name} » dépasse ${limits.maxEntryBytes shr 20} Mo une fois décompressé"
                    },
                )
                total += bytes.size
                if (files.put(entry.name, bytes) != null) {
                    throw DwpException(DwpException.Reason.CORRUPT, "« ${entry.name} » figure deux fois dans l'archive")
                }
            }
        } catch (e: DwpException) {
            throw e
        } catch (e: IOException) {
            throw DwpException(DwpException.Reason.CORRUPT, "Archive ZIP illisible : ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            // Nom de fichier mal encodé dans l'archive.
            throw DwpException(DwpException.Reason.CORRUPT, "Archive ZIP illisible : ${e.message}", e)
        }
        return files
    }

    private fun manifestOf(files: Map<String, ByteArray>): DwpManifest {
        val bytes = files[DwpFormat.MANIFEST]
            ?: throw DwpException(DwpException.Reason.NOT_DWP, "${DwpFormat.MANIFEST} absent de l'archive")
        return DwpManifest.parse(bytes.toString(Charsets.UTF_8))
    }

    /** Vrai si le flux commence comme une archive ZIP (« PK\u0003\u0004 ») ; le flux est rembobiné. */
    fun looksLikeZip(input: BufferedInputStream): Boolean {
        input.mark(ZIP_MAGIC.size)
        val head = ByteArray(ZIP_MAGIC.size)
        var read = 0
        while (read < head.size) {
            val n = input.read(head, read, head.size - read)
            if (n < 0) break
            read += n
        }
        input.reset()
        return read == head.size && head.contentEquals(ZIP_MAGIC)
    }

    /** Lit le fichier courant de l'archive, ou renvoie null dès qu'il dépasse [maxBytes]. */
    private fun readEntry(zip: ZipInputStream, maxBytes: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER)
        while (true) {
            val n = zip.read(buffer)
            if (n < 0) break
            if (out.size() + n > maxBytes) return null
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    /** Fichier déjà compressé (WebP, PNG) : stocké tel quel, ce qui épargne une recompression inutile. */
    private fun ZipOutputStream.putStored(name: String, bytes: ByteArray) {
        val entry = ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            crc = CRC32().apply { update(bytes) }.value
        }
        putNextEntry(entry)
        write(bytes)
        closeEntry()
    }

    private fun missing(name: String): Nothing =
        throw DwpException(DwpException.Reason.MISSING_ENTRY, "« $name », annoncé par le manifest, est absent de l'archive")

    /** Compte les octets lus dans l'archive et s'arrête au-delà de [limit]. */
    private class LimitedInputStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
        private var count = 0L

        override fun read(): Int = super.read().also { if (it >= 0) count(1) }

        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count(it.toLong()) }

        override fun skip(n: Long): Long = super.skip(n).also { count(it) }

        override fun markSupported(): Boolean = false

        private fun count(n: Long) {
            count += n
            if (count > limit) throw DwpException(DwpException.Reason.TOO_LARGE, "Archive de plus de ${limit shr 20} Mo")
        }
    }

    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private const val BUFFER = 64 * 1024
}
