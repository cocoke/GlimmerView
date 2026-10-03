package app.glimmerview.core

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * Fond d'écran actif, partagé entre l'app et le process « :wallpaper » au moyen de fichiers.
 * Chaque fichier est écrit dans un fichier temporaire puis renommé ; le fichier `version`
 * est écrit en dernier et sert de signal de changement pour le moteur du fond d'écran.
 */
class WallpaperStore(context: Context) {

    private val appContext = context.applicationContext
    private val root = File(context.filesDir, "wallpaper")
    private val currentDir = File(root, "current")
    private val colorFile = File(currentDir, "color.webp")
    private val depthFile = File(currentDir, "depth.r16")
    private val paramsFile = File(currentDir, "params.json")
    private val versionFile = File(currentDir, "version")
    private val statsFile = File(root, "stats.json")
    private val settingsFile = File(root, "settings.json")

    fun version(): Long =
        versionFile.takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull() ?: 0L

    @Synchronized
    fun save(color: Bitmap, depth: DepthMap, params: ParallaxParams) {
        currentDir.mkdirs()
        writeAtomically(colorFile) { color.compress(webpFormat(), 95, it) }
        writeAtomically(depthFile) { writeDepth(depth, it) }
        writeAtomically(paramsFile) { it.write(params.toJson().toByteArray()) }
        bumpVersion()
    }

    @Synchronized
    fun saveParams(params: ParallaxParams) {
        if (!colorFile.exists()) return
        writeAtomically(paramsFile) { it.write(params.toJson().toByteArray()) }
        bumpVersion()
    }

    fun load(): StoredWallpaper? {
        if (!colorFile.exists() || !depthFile.exists()) return null
        return runCatching {
            val version = version()
            val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val color = checkNotNull(BitmapFactory.decodeFile(colorFile.path, options)) { "Image illisible" }
            val depth = DataInputStream(depthFile.inputStream().buffered()).use(::readDepth)
            val params = paramsFile.takeIf { it.exists() }?.readText()?.let(ParallaxParams::fromJson)
                ?: ParallaxParams()
            StoredWallpaper(color, depth, params, version)
        }.getOrNull()
    }

    fun saveSettings(settings: WallpaperSettings) {
        root.mkdirs()
        writeAtomically(settingsFile) { it.write(settings.toJson().toByteArray()) }
    }

    fun loadSettings(): WallpaperSettings =
        runCatching { WallpaperSettings.fromJson(settingsFile.readText()) }.getOrDefault(WallpaperSettings())

    fun writeStats(json: String) {
        root.mkdirs()
        writeAtomically(statsFile) { it.write(json.toByteArray()) }
    }

    fun readStats(): String? = statsFile.takeIf { it.exists() }?.readText()

    private fun bumpVersion() {
        writeAtomically(versionFile) { it.write(System.currentTimeMillis().toString().toByteArray()) }
        // Prévient le process du fond d'écran : il recharge sans attendre de redevenir visible.
        appContext.sendBroadcast(Intent(ACTION_CHANGED).setPackage(appContext.packageName))
    }

    companion object {
        /** Diffusion interne à l'app (process « :wallpaper ») après un changement du fond actif. */
        const val ACTION_CHANGED = "app.glimmerview.action.WALLPAPER_CHANGED"

        /** « GVD1 » : GlimmerView Depth, version 1. */
        private const val DEPTH_MAGIC = 0x47564431

        fun writeDepth(depth: DepthMap, out: OutputStream) {
            val data = DataOutputStream(out)
            data.writeInt(DEPTH_MAGIC)
            data.writeInt(depth.width)
            data.writeInt(depth.height)
            val bytes = ByteBuffer.allocate(depth.values.size * 2)
            for (v in depth.values) {
                bytes.putShort((v.coerceIn(0f, 1f) * 65535f + 0.5f).toInt().toShort())
            }
            data.write(bytes.array())
            data.flush()
        }

        fun readDepth(input: DataInputStream): DepthMap {
            check(input.readInt() == DEPTH_MAGIC) { "Format de profondeur inconnu" }
            val width = input.readInt()
            val height = input.readInt()
            val bytes = ByteArray(width * height * 2)
            input.readFully(bytes)
            val buffer = ByteBuffer.wrap(bytes)
            val values = FloatArray(width * height) { (buffer.short.toInt() and 0xFFFF) / 65535f }
            return DepthMap(width, height, values)
        }
    }
}

class StoredWallpaper(
    val color: Bitmap,
    val depth: DepthMap,
    val params: ParallaxParams,
    val version: Long,
)
