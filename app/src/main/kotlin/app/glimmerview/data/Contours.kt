package app.glimmerview.data

import app.glimmerview.core.DepthMap
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Courbes de niveau de la profondeur (marching squares), pour l'animation du tirage :
 * les plans de l'image se dessinent comme sur une carte topographique.
 */
object Contours {

    /**
     * Pour chaque niveau, les segments `x0, y0, x1, y1` en coordonnées normalisées (0..1),
     * triés de gauche à droite pour être tracés d'un seul geste.
     */
    fun trace(depth: DepthMap, levels: FloatArray, columns: Int = 64): List<FloatArray> {
        val rows = max(2, (columns * depth.height.toFloat() / depth.width).roundToInt())
        val stride = columns + 1
        val grid = FloatArray(stride * (rows + 1)) { i ->
            depth.sample((i % stride).toFloat() / columns, (i / stride).toFloat() / rows)
        }
        return levels.map { level -> traceLevel(grid, stride, columns, rows, level) }
    }

    private fun traceLevel(grid: FloatArray, stride: Int, columns: Int, rows: Int, level: Float): FloatArray {
        val segments = ArrayList<FloatArray>()
        fun t(v0: Float, v1: Float) = if (v1 == v0) 0.5f else ((level - v0) / (v1 - v0)).coerceIn(0f, 1f)
        for (cy in 0 until rows) {
            for (cx in 0 until columns) {
                val a = grid[cy * stride + cx]
                val b = grid[cy * stride + cx + 1]
                val c = grid[(cy + 1) * stride + cx + 1]
                val d = grid[(cy + 1) * stride + cx]
                var case = 0
                if (a >= level) case = case or 8
                if (b >= level) case = case or 4
                if (c >= level) case = case or 2
                if (d >= level) case = case or 1
                if (case == 0 || case == 15) continue
                val top = floatArrayOf((cx + t(a, b)) / columns, cy.toFloat() / rows)
                val right = floatArrayOf((cx + 1f) / columns, (cy + t(b, c)) / rows)
                val bottom = floatArrayOf((cx + t(d, c)) / columns, (cy + 1f) / rows)
                val left = floatArrayOf(cx.toFloat() / columns, (cy + t(a, d)) / rows)
                fun add(p: FloatArray, q: FloatArray) {
                    segments += floatArrayOf(p[0], p[1], q[0], q[1])
                }
                when (case) {
                    1, 14 -> add(left, bottom)
                    2, 13 -> add(bottom, right)
                    3, 12 -> add(left, right)
                    4, 11 -> add(top, right)
                    6, 9 -> add(top, bottom)
                    7, 8 -> add(left, top)
                    5 -> {
                        add(top, right)
                        add(left, bottom)
                    }
                    10 -> {
                        add(left, top)
                        add(bottom, right)
                    }
                }
            }
        }
        segments.sortBy { minOf(it[0], it[2]) }
        val out = FloatArray(segments.size * 4)
        segments.forEachIndexed { i, s -> s.copyInto(out, i * 4) }
        return out
    }
}
