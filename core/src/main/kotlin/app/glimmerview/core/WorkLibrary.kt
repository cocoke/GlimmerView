package app.glimmerview.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.DataInputStream
import java.io.File
import kotlin.random.Random

/** Provenance d'une œuvre de la collection. */
enum class WorkOrigin {
    /** Créée par l'utilisateur à partir d'une de ses photos. */
    CREATED,

    /** Œuvre de la galerie que l'utilisateur a accrochée ou réglée. */
    ACQUIRED,
}

/** Fiche d'une œuvre : ce qu'affiche le cartel. */
data class WorkInfo(
    val id: String,
    val title: String,
    val artist: String,
    val medium: String,
    val license: String,
    val origin: WorkOrigin,
    val createdAt: Long,
    val width: Int,
    val height: Int,
) {

    fun toJson(): String = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("artist", artist)
        .put("medium", medium)
        .put("license", license)
        .put("origin", origin.name)
        .put("createdAt", createdAt)
        .put("width", width)
        .put("height", height)
        .toString()

    companion object {
        fun fromJson(json: String): WorkInfo {
            val o = JSONObject(json)
            return WorkInfo(
                id = o.getString("id"),
                title = o.optString("title"),
                artist = o.optString("artist"),
                medium = o.optString("medium"),
                license = o.optString("license"),
                origin = runCatching { WorkOrigin.valueOf(o.optString("origin")) }.getOrDefault(WorkOrigin.CREATED),
                createdAt = o.optLong("createdAt"),
                width = o.optInt("width"),
                height = o.optInt("height"),
            )
        }
    }
}

class StoredWork(val info: WorkInfo, val color: Bitmap, val depth: DepthMap, val params: ParallaxParams)

/**
 * Collection personnelle : une œuvre par dossier (`works/<id>/`), avec image, profondeur,
 * réglages, vignette et fiche. La fiche est écrite en dernier : un dossier sans fiche est
 * une écriture interrompue et n'est jamais listé.
 */
class WorkLibrary(context: Context) {

    private val root = File(context.filesDir, "works")
    private val currentFile = File(root, "current")

    fun list(): List<WorkInfo> =
        root.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir -> runCatching { WorkInfo.fromJson(File(dir, META).readText()) }.getOrNull() }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()

    fun contains(id: String): Boolean = File(dir(id), META).exists()

    fun info(id: String): WorkInfo? = runCatching { WorkInfo.fromJson(File(dir(id), META).readText()) }.getOrNull()

    fun params(id: String): ParallaxParams =
        runCatching { ParallaxParams.fromJson(File(dir(id), PARAMS).readText()) }.getOrDefault(ParallaxParams())

    fun thumbnail(id: String): Bitmap? = BitmapFactory.decodeFile(File(dir(id), THUMB).path)

    fun load(id: String): StoredWork? = runCatching {
        val dir = dir(id)
        val info = WorkInfo.fromJson(File(dir, META).readText())
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val color = checkNotNull(BitmapFactory.decodeFile(File(dir, COLOR).path, options)) { "Image illisible" }
        val depth = DataInputStream(File(dir, DEPTH).inputStream().buffered()).use(WallpaperStore::readDepth)
        StoredWork(info, color, depth, params(id))
    }.getOrNull()

    @Synchronized
    fun save(info: WorkInfo, color: Bitmap, depth: DepthMap, params: ParallaxParams, thumbnail: Bitmap): WorkInfo {
        val dir = dir(info.id).apply { mkdirs() }
        File(dir, META).delete()
        writeAtomically(File(dir, COLOR)) { color.compress(webpFormat(), 95, it) }
        writeAtomically(File(dir, DEPTH)) { WallpaperStore.writeDepth(depth, it) }
        writeAtomically(File(dir, PARAMS)) { it.write(params.toJson().toByteArray()) }
        writeAtomically(File(dir, THUMB)) { thumbnail.compress(webpFormat(), 90, it) }
        writeAtomically(File(dir, META)) { it.write(info.toJson().toByteArray()) }
        return info
    }

    @Synchronized
    fun saveParams(id: String, params: ParallaxParams) {
        val dir = dir(id)
        if (!dir.exists()) return
        writeAtomically(File(dir, PARAMS)) { it.write(params.toJson().toByteArray()) }
    }

    @Synchronized
    fun rename(id: String, title: String) {
        val info = info(id) ?: return
        writeAtomically(File(dir(id), META)) { it.write(info.copy(title = title).toJson().toByteArray()) }
    }

    @Synchronized
    fun delete(id: String) {
        dir(id).deleteRecursively()
        if (currentId() == id) setCurrent(null)
    }

    fun currentId(): String? = currentFile.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }

    @Synchronized
    fun setCurrent(id: String?) {
        root.mkdirs()
        if (id == null) currentFile.delete() else writeAtomically(currentFile) { it.write(id.toByteArray()) }
    }

    private fun dir(id: String): File {
        require(id.matches(ID_PATTERN)) { "Identifiant invalide : $id" }
        return File(root, id)
    }

    companion object {
        private const val META = "meta.json"
        private const val COLOR = "color.webp"
        private const val DEPTH = "depth.r16"
        private const val PARAMS = "params.json"
        private const val THUMB = "thumb.webp"
        private val ID_PATTERN = Regex("[a-z0-9-]{1,64}")

        fun newId(): String =
            "w" + System.currentTimeMillis().toString(36) + "-" + Random.nextInt(0, 36 * 36 * 36).toString(36)
    }
}
