package app.glimmerview.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.annotation.StringRes
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withRotation
import app.glimmerview.R
import app.glimmerview.core.DepthMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Salles de la galerie. */
enum class Room(@StringRes val label: Int) {
    NATURE(R.string.room_nature),
    SPACE(R.string.room_space),
    CITIES(R.string.room_cities),
    ART(R.string.room_art),
}

/**
 * Œuvre de la collection GlimmerView : une illustration en plans, dessinée à la volée.
 * Comme chaque plan a une profondeur connue, la carte de profondeur est exacte : la
 * parallaxe est parfaite, sans IA, sans téléchargement et sans question de droits (CC0).
 */
class Scene internal constructor(
    val id: String,
    @StringRes val title: Int,
    val room: Room,
    internal val sky: Int,
    internal val orb: Orb?,
    internal val ring: Ring? = null,
    internal val stars: List<Dot> = emptyList(),
    internal val windows: List<Dot> = emptyList(),
    internal val layers: List<Layer>,
) {
    /** Nombre de plans, pour le cartel. */
    val planes: Int get() = layers.size
}

/** Disque (soleil, lune, planète) : centre en fractions de l'image, diamètre en fraction de la largeur. */
internal class Orb(val x: Float, val y: Float, val diameter: Float, val color: Int)

/** Anneau elliptique : largeur en fraction de la largeur, hauteur en fraction de la hauteur. */
internal class Ring(val x: Float, val y: Float, val width: Float, val height: Float, val stroke: Float, val color: Int)

/** Petit point lumineux (étoile, fenêtre) : position en pourcentage, taille en px pour 390 de large. */
internal class Dot(val x: Float, val y: Float, val size: Float, val alpha: Float, val color: Int)

/**
 * Plan : sa boîte va de -8 % à 108 % en largeur et de [top] au bas de l'image ;
 * [points] est un polygone en pourcentages de cette boîte (x, y, x, y…).
 */
internal class Layer(val top: Float, val color: Int, val points: FloatArray)

object SceneArt {

    val all: List<Scene> by lazy { listOf(dawn(), forest(), dunes(), lagoon(), cosmos(), city(), domes()) }

    fun find(id: String): Scene? = all.firstOrNull { it.id == id }

    /** Image couleur de la scène. */
    fun renderColor(scene: Scene, width: Int, height: Int): Bitmap {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val unit = width / 390f
        canvas.drawColor(scene.sky)
        for (star in scene.stars) {
            paint.color = star.color
            paint.alpha = (star.alpha * 255).toInt()
            val r = star.size * unit / 2f
            canvas.drawCircle(star.x / 100f * width + r, star.y / 100f * height + r, r, paint)
        }
        paint.alpha = 255
        scene.orb?.let { drawOrb(canvas, it, width, height, paint, it.color) }
        scene.ring?.let { drawRing(canvas, it, width, height, unit, paint, it.color) }
        for (layer in scene.layers) {
            paint.color = layer.color
            canvas.drawPath(layerPath(layer, width, height), paint)
        }
        for (window in scene.windows) {
            paint.color = window.color
            paint.alpha = (window.alpha * 255).toInt()
            val x = window.x / 100f * width
            val y = window.y / 100f * height
            canvas.drawRoundRect(RectF(x, y, x + 4 * unit, y + 5 * unit), unit, unit, paint)
        }
        return bitmap
    }

    /** Carte de profondeur exacte : le ciel au fond (0), chaque plan un cran plus près, jusqu'à 1. */
    fun renderDepth(scene: Scene, width: Int, height: Int): DepthMap {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val unit = width / 390f
        canvas.drawColor(gray(0f))
        scene.orb?.let { drawOrb(canvas, it, width, height, paint, gray(ORB_DEPTH)) }
        scene.ring?.let { drawRing(canvas, it, width, height, unit, paint, gray(ORB_DEPTH)) }
        val n = scene.layers.size
        scene.layers.forEachIndexed { i, layer ->
            paint.color = gray(FIRST_PLANE + (1f - FIRST_PLANE) * (i + 1) / n)
            canvas.drawPath(layerPath(layer, width, height), paint)
        }
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()
        return DepthMap(width, height, FloatArray(pixels.size) { ((pixels[it] shr 16) and 0xFF) / 255f })
    }

    private fun gray(value: Float): Int {
        val g = (value.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        return (0xFF shl 24) or (g shl 16) or (g shl 8) or g
    }

    private fun drawOrb(canvas: Canvas, orb: Orb, width: Int, height: Int, paint: Paint, color: Int) {
        paint.color = color
        paint.style = Paint.Style.FILL
        canvas.drawCircle(orb.x * width, orb.y * height, orb.diameter * width / 2f, paint)
    }

    private fun drawRing(canvas: Canvas, ring: Ring, width: Int, height: Int, unit: Float, paint: Paint, color: Int) {
        val cx = ring.x * width
        val cy = ring.y * height
        val rx = ring.width * width / 2f
        val ry = ring.height * height / 2f
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = ring.stroke * unit
        canvas.withRotation(-14f, cx, cy) {
            drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun layerPath(layer: Layer, width: Int, height: Int): Path {
        val left = -0.08f * width
        val boxWidth = 1.16f * width
        val top = layer.top * height
        val boxHeight = height - top
        val path = Path()
        val p = layer.points
        var i = 0
        while (i < p.size) {
            val x = left + p[i] / 100f * boxWidth
            val y = top + p[i + 1] / 100f * boxHeight
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            i += 2
        }
        path.close()
        return path
    }

    // ── Scènes (mêmes compositions que les maquettes) ─────────────────────────

    private fun dawn() = Scene(
        id = "dawn",
        title = R.string.scene_dawn,
        room = Room.NATURE,
        sky = 0xFFEFE6D8.toInt(),
        orb = Orb(0.68f, 0.28f, 0.22f, 0xFFE4C9A1.toInt()),
        layers = listOf(
            Layer(0.46f, 0xFFC9CBC6.toInt(), poly("0 35, 12 22, 26 38, 41 14, 57 34, 72 18, 86 30, 100 20")),
            Layer(0.57f, 0xFFA3A9AB.toInt(), poly("0 30, 18 12, 33 34, 50 8, 66 30, 82 14, 100 32")),
            Layer(0.69f, 0xFF7D8689.toInt(), poly("0 20, 20 38, 38 10, 55 32, 74 6, 90 28, 100 18")),
            Layer(0.81f, 0xFF56605F.toInt(), poly("0 28, 16 6, 34 30, 60 12, 80 34, 100 10")),
            Layer(0.90f, 0xFF2E3432.toInt(), poly("0 44, 22 22, 46 46, 70 18, 100 40")),
        ),
    )

    private fun cosmos() = Scene(
        id = "cosmos",
        title = R.string.scene_cosmos,
        room = Room.SPACE,
        sky = 0xFF1F2433.toInt(),
        orb = Orb(0.58f, 0.36f, 0.34f, 0xFF8C9AB8.toInt()),
        ring = Ring(0.58f, 0.36f, 0.64f, 0.15f, 3f, 0xFFD9CBB0.toInt()),
        stars = stars(36, 7, 80f, 0xFFEDE8DE.toInt()),
        layers = listOf(
            Layer(0.74f, 0xFF2C3243.toInt(), poly("0 40, 14 22, 30 36, 48 14, 66 34, 84 18, 100 30")),
            Layer(0.86f, 0xFF15192A.toInt(), poly("0 26, 24 8, 46 30, 70 10, 100 28")),
        ),
    )

    private fun forest() = Scene(
        id = "forest",
        title = R.string.scene_forest,
        room = Room.NATURE,
        sky = 0xFFE3E5DD.toInt(),
        orb = Orb(0.26f, 0.20f, 0.15f, 0xFFF2F0E8.toInt()),
        layers = listOf(
            Layer(0.38f, 0xFFC2C8BB.toInt(), zig(26, 62f, 18f, 34f, 5)),
            Layer(0.50f, 0xFF9AA393.toInt(), zig(22, 60f, 14f, 30f, 9)),
            Layer(0.64f, 0xFF6E7A69.toInt(), zig(18, 58f, 10f, 26f, 13)),
            Layer(0.78f, 0xFF3E4A3F.toInt(), zig(14, 56f, 6f, 22f, 17)),
        ),
    )

    private fun city() = Scene(
        id = "city",
        title = R.string.scene_city,
        room = Room.CITIES,
        sky = 0xFF2A3040.toInt(),
        orb = Orb(0.76f, 0.17f, 0.13f, 0xFFE8DCC4.toInt()),
        stars = stars(20, 3, 40f, 0xFFE8E2D6.toInt()),
        windows = windows(26, 41, 0xFFD9C08A.toInt()),
        layers = listOf(
            Layer(0.44f, 0xFF4A5468.toInt(), skyline(9, 18f, 55f, 11)),
            Layer(0.58f, 0xFF3A4356.toInt(), skyline(7, 12f, 50f, 23)),
            Layer(0.74f, 0xFF1E2330.toInt(), skyline(6, 8f, 40f, 37)),
        ),
    )

    private fun dunes() = Scene(
        id = "dunes",
        title = R.string.scene_dunes,
        room = Room.NATURE,
        sky = 0xFFEADFCB.toInt(),
        orb = Orb(0.50f, 0.36f, 0.28f, 0xFFF5EEE2.toInt()),
        layers = listOf(
            Layer(0.56f, 0xFFDCC7A6.toInt(), wave(24, 34f, 12f, 0.4f, 1.2f)),
            Layer(0.66f, 0xFFC8AC84.toInt(), wave(24, 30f, 14f, 2.1f, 1.0f)),
            Layer(0.78f, 0xFFA88B66.toInt(), wave(24, 28f, 16f, 4.0f, 0.9f)),
            Layer(0.88f, 0xFF6F5A43.toInt(), wave(24, 30f, 14f, 1.2f, 1.1f)),
        ),
    )

    private fun lagoon() = Scene(
        id = "lagoon",
        title = R.string.scene_lagoon,
        room = Room.NATURE,
        sky = 0xFFE6E9E4.toInt(),
        orb = Orb(0.74f, 0.22f, 0.16f, 0xFFF1E9D6.toInt()),
        layers = listOf(
            Layer(0.50f, 0xFFC9D6D6.toInt(), wave(36, 22f, 5f, 0.0f, 5.0f)),
            Layer(0.62f, 0xFFA3BCC0.toInt(), wave(36, 22f, 6f, 1.4f, 4.4f)),
            Layer(0.44f, 0xFF2F3B3C.toInt(), floatArrayOf(44f, 40f, 47f, 30f, 50f, 33f, 53f, 22f, 57f, 31f, 60f, 38f, 62f, 100f, 42f, 100f)),
            Layer(0.75f, 0xFF6E8E96.toInt(), wave(36, 22f, 7f, 2.8f, 3.8f)),
            Layer(0.87f, 0xFF3F5E68.toInt(), wave(36, 22f, 8f, 4.1f, 3.2f)),
        ),
    )

    private fun domes() = Scene(
        id = "domes",
        title = R.string.scene_domes,
        room = Room.ART,
        sky = 0xFFEFE3D3.toInt(),
        orb = Orb(0.30f, 0.24f, 0.18f, 0xFFE7B98F.toInt()),
        layers = listOf(
            Layer(0.40f, 0xFFD8C3A5.toInt(), domes(3, 60f, 40f)),
            Layer(0.52f, 0xFFC4A27F.toInt(), domes(4, 62f, 38f)),
            Layer(0.64f, 0xFFA47F5E.toInt(), domes(5, 64f, 36f)),
            Layer(0.76f, 0xFF7A5C43.toInt(), domes(6, 66f, 34f)),
            Layer(0.87f, 0xFF4F3B2C.toInt(), domes(7, 68f, 32f)),
        ),
    )

    // ── Générateurs de silhouettes ────────────────────────────────────────────

    /** Polygone « x y, x y… » fermé par le bas de la boîte. */
    private fun poly(spec: String): FloatArray {
        val values = spec.split(',').flatMap { pair -> pair.trim().split(' ').map(String::toFloat) }
        return (values + listOf(100f, 100f, 0f, 100f)).toFloatArray()
    }

    /** Générateur pseudo-aléatoire déterministe (identique aux maquettes). */
    private class Lcg(seed: Int) {
        private var s = seed.toLong()
        fun next(): Float {
            s = (s * 9301 + 49297) % 233280
            return s / 233280f
        }
    }

    private fun zig(teeth: Int, valley: Float, peakLo: Float, peakHi: Float, seed: Int): FloatArray {
        val rnd = Lcg(seed)
        val n = teeth * 2
        val out = ArrayList<Float>()
        for (i in 0..n) {
            out += i * 100f / n
            out += if (i % 2 == 0) valley else peakLo + rnd.next() * (peakHi - peakLo)
        }
        out += listOf(100f, 100f, 0f, 100f)
        return out.toFloatArray()
    }

    private fun wave(n: Int, base: Float, amp: Float, phase: Float, freq: Float): FloatArray {
        val out = ArrayList<Float>()
        for (i in 0..n) {
            val x = i.toFloat() / n
            out += x * 100f
            out += base + amp * sin(x * PI.toFloat() * freq + phase)
        }
        out += listOf(100f, 100f, 0f, 100f)
        return out.toFloatArray()
    }

    private fun skyline(count: Int, lo: Float, hi: Float, seed: Int): FloatArray {
        val rnd = Lcg(seed)
        val out = ArrayList<Float>()
        for (i in 0 until count) {
            val top = lo + rnd.next() * (hi - lo)
            out += listOf(i * 100f / count, top, (i + 1) * 100f / count, top)
        }
        out += listOf(100f, 100f, 0f, 100f)
        return out.toFloatArray()
    }

    private fun domes(count: Int, base: Float, rise: Float): FloatArray {
        val out = ArrayList<Float>()
        val radius = 50f / count
        for (k in 0 until count) {
            val cx = (k + 0.5f) * 100f / count
            for (j in 0..12) {
                val a = PI * j / 12
                out += cx - radius * cos(a).toFloat()
                out += base - rise * sin(a).toFloat()
            }
        }
        out += listOf(100f, 100f, 0f, 100f)
        return out.toFloatArray()
    }

    private fun stars(count: Int, seed: Int, maxY: Float, color: Int): List<Dot> {
        val rnd = Lcg(seed)
        return List(count) {
            val x = rnd.next() * 100f
            val y = rnd.next() * maxY
            val size = if (rnd.next() < 0.2f) 3f else 2f
            val alpha = 0.4f + rnd.next() * 0.5f
            Dot(x, y, size, alpha, color)
        }
    }

    private fun windows(count: Int, seed: Int, color: Int): List<Dot> {
        val rnd = Lcg(seed)
        return List(count) {
            val x = 4f + rnd.next() * 92f
            val y = 82f + rnd.next() * 14f
            val alpha = 0.55f + rnd.next() * 0.4f
            Dot(x, y, 4f, alpha, color)
        }
    }

    private const val ORB_DEPTH = 0.06f
    private const val FIRST_PLANE = 0.2f
}
