package app.glimmerview.data

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Outils de retouche de la profondeur. */
enum class SketchTool { NEARER, FARTHER, SMOOTH, FLATTEN, ERASE }

/** Rectangle de pixels, bornes droite et basse exclues. */
data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val isEmpty: Boolean get() = right <= left || bottom <= top

    fun union(other: PixelRect?): PixelRect = if (other == null || other.isEmpty) this else PixelRect(
        min(left, other.left),
        min(top, other.top),
        max(right, other.right),
        max(bottom, other.bottom),
    )
}

/**
 * Retouche d'une carte de profondeur (disparité : 0 = loin, 1 = près), sans dépendance Android.
 *
 * Deux façons de corriger, pensées pour tout le monde :
 * - au pinceau ([dab], [stroke]) : avancer, reculer, lisser, aplatir ou gommer là où l'on peint ;
 * - par zone ([select] puis [applyToSelection]) : on touche une personne ou un objet, la zone
 *   s'étend tant que la profondeur reste continue, et une action s'applique à toute la zone.
 *
 * [depth] est modifiée sur place ; [original] est la carte d'origine, vers laquelle « Gommer » revient.
 */
class DepthSketch(
    val width: Int,
    val height: Int,
    val depth: FloatArray,
    private val original: FloatArray,
) {

    init {
        require(width > 0 && height > 0) { "Dimensions invalides : ${width}×$height" }
        require(depth.size == width * height && original.size == width * height) { "Tailles incohérentes" }
    }

    /** Poids de la zone sélectionnée (0..1, bords adoucis), ou null. */
    var selection: FloatArray? = null
        private set

    /** Rectangle qui englobe la sélection. */
    var selectionBounds: PixelRect? = null
        private set

    private val undoStack = ArrayDeque<ShortArray>()
    private val redoStack = ArrayDeque<ShortArray>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Vrai dès qu'une retouche a eu lieu. */
    var edited: Boolean = false
        private set

    val bounds: PixelRect get() = PixelRect(0, 0, width, height)

    /** Mémorise l'état avant un geste (trait de pinceau ou action sur la zone). */
    fun checkpoint() {
        undoStack.addLast(pack(depth))
        if (undoStack.size > MAX_UNDO) undoStack.removeFirst()
        redoStack.clear()
        edited = true
    }

    fun undo(): Boolean {
        val state = undoStack.removeLastOrNull() ?: return false
        redoStack.addLast(pack(depth))
        unpack(state, depth)
        return true
    }

    fun redo(): Boolean {
        val state = redoStack.removeLastOrNull() ?: return false
        undoStack.addLast(pack(depth))
        unpack(state, depth)
        return true
    }

    fun valueAt(x: Float, y: Float): Float = depth[index(x, y)]

    /**
     * Un coup de pinceau centré en ([x], [y]), en pixels de la carte, de rayon [radius].
     * [flattenTarget] : profondeur visée par « Aplatir » (celle du début du trait).
     * Renvoie la zone modifiée.
     */
    fun dab(x: Float, y: Float, radius: Float, tool: SketchTool, flattenTarget: Float): PixelRect {
        val r = max(radius, 1f)
        val x0 = max(0, floor(x - r).toInt())
        val x1 = min(width - 1, ceil(x + r).toInt())
        val y0 = max(0, floor(y - r).toInt())
        val y1 = min(height - 1, ceil(y + r).toInt())
        if (x1 < x0 || y1 < y0) return PixelRect(0, 0, 0, 0)
        val r2 = r * r
        // « Lisser » lit les voisins : on travaille sur une copie de la zone pour ne pas lisser deux fois.
        val source = if (tool == SketchTool.SMOOTH) regionCopy(x0 - 1, y0 - 1, x1 + 1, y1 + 1) else null
        for (j in y0..y1) {
            for (i in x0..x1) {
                val dx = i + 0.5f - x
                val dy = j + 0.5f - y
                val d2 = (dx * dx + dy * dy) / r2
                if (d2 >= 1f) continue
                val w = (1f - d2) * (1f - d2)
                val k = j * width + i
                depth[k] = when (tool) {
                    SketchTool.NEARER -> min(1f, depth[k] + PAINT_STEP * w)
                    SketchTool.FARTHER -> max(0f, depth[k] - PAINT_STEP * w)
                    SketchTool.SMOOTH -> lerp(depth[k], localMean(source!!, i, j, x0 - 1, y0 - 1, x1 + 1, y1 + 1), 0.6f * w)
                    SketchTool.FLATTEN -> lerp(depth[k], flattenTarget, 0.35f * w)
                    SketchTool.ERASE -> lerp(depth[k], original[k], 0.5f * w)
                }
            }
        }
        return PixelRect(x0, y0, x1 + 1, y1 + 1)
    }

    /** Trait de pinceau de A à B : des coups espacés d'un tiers de rayon, pour un trait continu. */
    fun stroke(ax: Float, ay: Float, bx: Float, by: Float, radius: Float, tool: SketchTool, flattenTarget: Float): PixelRect {
        val distance = hypot(bx - ax, by - ay)
        val steps = max(1, ceil(distance / max(1f, radius / 3f)).toInt())
        var dirty: PixelRect? = null
        for (s in 1..steps) {
            val t = s.toFloat() / steps
            val rect = dab(ax + (bx - ax) * t, ay + (by - ay) * t, radius, tool, flattenTarget)
            dirty = rect.union(dirty)
        }
        return dirty ?: PixelRect(0, 0, 0, 0)
    }

    /** Vrai si le point ([x], [y]) est franchement à l'intérieur de la zone sélectionnée. */
    fun isSelected(x: Float, y: Float): Boolean = (selection?.get(index(x, y)) ?: 0f) > 0.5f

    /**
     * Sélectionne la zone touchée en ([x], [y]) : elle grandit de proche en proche tant que la
     * profondeur ne fait pas de saut (bord d'un objet) et reste proche de celle du point touché.
     * [tightness] (0 à 3) resserre la zone : chaque cran divise par deux le saut toléré.
     * Renvoie faux si la zone est trop petite pour être utile (la sélection est alors effacée).
     */
    fun select(x: Float, y: Float, tightness: Int = 0): Boolean {
        val level = tightness.coerceIn(0, MAX_TIGHTNESS)
        val edgeStep = EDGE_STEP / (1 shl level)
        val seedRange = SEED_RANGE / (1 + level)
        val size = width * height
        val start = index(x, y)
        val seed = depth[start]
        val inside = BooleanArray(size)
        val queue = IntArray(size)
        var head = 0
        var tail = 0
        inside[start] = true
        queue[tail++] = start
        var minX = start % width
        var maxX = minX
        var minY = start / width
        var maxY = minY
        while (head < tail) {
            val p = queue[head++]
            val px = p % width
            val py = p / width
            val dp = depth[p]
            for (n in 0 until 4) {
                val qx = px + NEIGHBOR_X[n]
                val qy = py + NEIGHBOR_Y[n]
                if (qx < 0 || qy < 0 || qx >= width || qy >= height) continue
                val q = qy * width + qx
                if (inside[q]) continue
                val dq = depth[q]
                if (abs(dq - dp) > edgeStep || abs(dq - seed) > seedRange) continue
                inside[q] = true
                queue[tail++] = q
                if (qx < minX) minX = qx
                if (qx > maxX) maxX = qx
                if (qy < minY) minY = qy
                if (qy > maxY) maxY = qy
            }
        }
        if (tail < MIN_REGION) {
            clearSelection()
            return false
        }
        val rect = PixelRect(
            max(0, minX - FEATHER),
            max(0, minY - FEATHER),
            min(width, maxX + 1 + FEATHER),
            min(height, maxY + 1 + FEATHER),
        )
        selection = feather(inside, rect)
        selectionBounds = rect
        return true
    }

    fun clearSelection() {
        selection = null
        selectionBounds = null
    }

    /** Applique [tool] à toute la zone sélectionnée, en un seul geste. Renvoie la zone modifiée. */
    fun applyToSelection(tool: SketchTool): PixelRect? {
        val weights = selection ?: return null
        val rect = selectionBounds ?: return null
        checkpoint()
        when (tool) {
            SketchTool.NEARER -> forEachIn(rect) { k -> depth[k] = min(1f, depth[k] + SELECTION_STEP * weights[k]) }
            SketchTool.FARTHER -> forEachIn(rect) { k -> depth[k] = max(0f, depth[k] - SELECTION_STEP * weights[k]) }
            SketchTool.ERASE -> forEachIn(rect) { k -> depth[k] = lerp(depth[k], original[k], weights[k]) }
            SketchTool.FLATTEN -> {
                var sum = 0.0
                var total = 0.0
                forEachIn(rect) { k ->
                    sum += depth[k] * weights[k]
                    total += weights[k]
                }
                val mean = if (total > 0) (sum / total).toFloat() else return rect
                forEachIn(rect) { k -> depth[k] = lerp(depth[k], mean, weights[k]) }
            }
            SketchTool.SMOOTH -> {
                val blurred = boxBlur(rect, SMOOTH_RADIUS)
                forEachIn(rect) { k ->
                    val local = (k / width - rect.top) * (rect.right - rect.left) + (k % width - rect.left)
                    depth[k] = lerp(depth[k], blurred[local], weights[k])
                }
            }
        }
        return rect
    }

    fun result(): FloatArray = depth.copyOf()

    // ── Outils internes ───────────────────────────────────────────────────────

    private fun index(x: Float, y: Float): Int {
        val i = x.toInt().coerceIn(0, width - 1)
        val j = y.toInt().coerceIn(0, height - 1)
        return j * width + i
    }

    private inline fun forEachIn(rect: PixelRect, action: (Int) -> Unit) {
        for (j in rect.top until rect.bottom) {
            val row = j * width
            for (i in rect.left until rect.right) action(row + i)
        }
    }

    /** Copie de la zone [x0..x1]×[y0..y1] (bornes incluses), bords répétés. */
    private fun regionCopy(x0: Int, y0: Int, x1: Int, y1: Int): FloatArray {
        val w = x1 - x0 + 1
        val out = FloatArray(w * (y1 - y0 + 1))
        for (j in y0..y1) {
            val sj = j.coerceIn(0, height - 1)
            for (i in x0..x1) out[(j - y0) * w + (i - x0)] = depth[sj * width + i.coerceIn(0, width - 1)]
        }
        return out
    }

    private fun localMean(region: FloatArray, i: Int, j: Int, x0: Int, y0: Int, x1: Int, y1: Int): Float {
        val w = x1 - x0 + 1
        var sum = 0f
        for (dy in -1..1) for (dx in -1..1) sum += region[(j + dy - y0) * w + (i + dx - x0)]
        return sum / 9f
    }

    /** Masque binaire → poids adoucis par un flou carré de rayon [FEATHER], limité à [rect]. */
    private fun feather(inside: BooleanArray, rect: PixelRect): FloatArray {
        val out = FloatArray(width * height)
        val side = 2 * FEATHER + 1
        val norm = 1f / (side * side)
        for (j in rect.top until rect.bottom) {
            for (i in rect.left until rect.right) {
                var count = 0
                for (dy in -FEATHER..FEATHER) {
                    val y = j + dy
                    if (y < 0 || y >= height) continue
                    for (dx in -FEATHER..FEATHER) {
                        val x = i + dx
                        if (x >= 0 && x < width && inside[y * width + x]) count++
                    }
                }
                out[j * width + i] = count * norm
            }
        }
        return out
    }

    /** Flou carré séparable de la zone [rect], renvoyé à la taille de la zone. */
    private fun boxBlur(rect: PixelRect, radius: Int): FloatArray {
        val w = rect.right - rect.left
        val h = rect.bottom - rect.top
        val horizontal = FloatArray(w * h)
        for (j in 0 until h) {
            val row = (rect.top + j) * width
            for (i in 0 until w) {
                var sum = 0f
                var n = 0
                for (d in -radius..radius) {
                    val x = rect.left + i + d
                    if (x < 0 || x >= width) continue
                    sum += depth[row + x]
                    n++
                }
                horizontal[j * w + i] = sum / n
            }
        }
        val out = FloatArray(w * h)
        for (i in 0 until w) {
            for (j in 0 until h) {
                var sum = 0f
                var n = 0
                for (d in -radius..radius) {
                    val y = j + d
                    if (y < 0 || y >= h) continue
                    sum += horizontal[y * w + i]
                    n++
                }
                out[j * w + i] = sum / n
            }
        }
        return out
    }

    companion object {
        /** Effet d'un coup de pinceau au centre (s'additionne tant qu'on peint). */
        const val PAINT_STEP = 0.04f

        /** Effet d'« Avancer » ou « Reculer » sur une zone sélectionnée. */
        const val SELECTION_STEP = 0.08f

        /** Saut de profondeur entre deux pixels voisins qui marque le bord d'un objet. */
        const val EDGE_STEP = 0.012f

        /** Écart maximal avec la profondeur du point touché. */
        const val SEED_RANGE = 0.2f

        /** En dessous (en pixels), la zone touchée n'est pas retenue. */
        const val MIN_REGION = 24

        /** Nombre de crans de resserrement d'une zone (touchers successifs). */
        const val MAX_TIGHTNESS = 3

        const val FEATHER = 2
        const val SMOOTH_RADIUS = 4
        const val MAX_UNDO = 10

        private val NEIGHBOR_X = intArrayOf(1, -1, 0, 0)
        private val NEIGHBOR_Y = intArrayOf(0, 0, 1, -1)

        private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

        /** Historique compact : 16 bits par pixel au lieu de 32. */
        private fun pack(values: FloatArray) = ShortArray(values.size) { (values[it].coerceIn(0f, 1f) * 65535f + 0.5f).toInt().toShort() }

        private fun unpack(packed: ShortArray, into: FloatArray) {
            for (i in packed.indices) into[i] = (packed[i].toInt() and 0xFFFF) / 65535f
        }
    }
}
