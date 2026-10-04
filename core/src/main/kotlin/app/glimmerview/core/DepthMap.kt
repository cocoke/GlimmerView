package app.glimmerview.core

import kotlin.math.floor

/**
 * Carte de disparité normalisée : 0 = le plus loin, 1 = le plus proche.
 * Les valeurs sont rangées ligne par ligne, la première ligne correspondant au haut de l'image.
 */
class DepthMap(val width: Int, val height: Int, val values: FloatArray) {

    init {
        require(width > 0 && height > 0) { "Dimensions invalides : ${width}×$height" }
        require(values.size == width * height) { "Taille incohérente : ${values.size} ≠ ${width}×$height" }
    }

    /** Échantillonnage bilinéaire en coordonnées normalisées (0..1), borné aux bords. */
    fun sample(u: Float, v: Float): Float {
        val x = (u * width - 0.5f).coerceIn(0f, (width - 1).toFloat())
        val y = (v * height - 0.5f).coerceIn(0f, (height - 1).toFloat())
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val x1 = minOf(x0 + 1, width - 1)
        val y1 = minOf(y0 + 1, height - 1)
        val fx = x - x0
        val fy = y - y0
        val top = values[y0 * width + x0] * (1 - fx) + values[y0 * width + x1] * fx
        val bottom = values[y1 * width + x0] * (1 - fx) + values[y1 * width + x1] * fx
        return top * (1 - fy) + bottom * fy
    }

    /** Carte réduite (moyenne de chaque zone) dont le grand côté ne dépasse pas [maxSide] ; la carte elle-même si elle tient. */
    fun scaledToFit(maxSide: Int): DepthMap {
        val longSide = maxOf(width, height)
        if (longSide <= maxSide) return this
        val w = maxOf(1, (width.toLong() * maxSide / longSide).toInt())
        val h = maxOf(1, (height.toLong() * maxSide / longSide).toInt())
        val out = FloatArray(w * h)
        for (y in 0 until h) {
            val y0 = y * height / h
            val y1 = maxOf(y0 + 1, (y + 1) * height / h)
            for (x in 0 until w) {
                val x0 = x * width / w
                val x1 = maxOf(x0 + 1, (x + 1) * width / w)
                var sum = 0f
                for (sy in y0 until y1) {
                    val row = sy * width
                    for (sx in x0 until x1) sum += values[row + sx]
                }
                out[y * w + x] = sum / ((y1 - y0) * (x1 - x0))
            }
        }
        return DepthMap(w, h, out)
    }
}
