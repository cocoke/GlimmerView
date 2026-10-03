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
}
