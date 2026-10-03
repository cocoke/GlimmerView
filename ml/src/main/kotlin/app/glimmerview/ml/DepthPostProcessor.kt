package app.glimmerview.ml

import android.graphics.Bitmap
import app.glimmerview.core.DepthMap
import kotlin.math.max
import kotlin.math.roundToInt

/** Sortie brute d'un modèle de profondeur (disparité relative, échelle arbitraire). */
class RawDepth(val width: Int, val height: Int, val values: FloatArray)

/** Durées du post-traitement, en millisecondes. */
data class PostProcessTimings(val normalizeMs: Double, val resizeMs: Double, val refineMs: Double) {
    val totalMs get() = normalizeMs + resizeMs + refineMs
}

class RefinedDepth(val depth: DepthMap, val timings: PostProcessTimings)

object DepthPostProcessor {

    /**
     * Chaîne complète : normalisation → redimensionnement au format de l'image →
     * (option) filtre guidé par l'image + dilatation du premier plan contre les halos.
     */
    fun refine(raw: RawDepth, image: Bitmap, targetLongSide: Int = 1280, edgeAware: Boolean = true): RefinedDepth {
        val t0 = System.nanoTime()
        val normalized = normalize(raw)
        val t1 = System.nanoTime()
        val scale = targetLongSide.toFloat() / max(image.width, image.height)
        val width = max(1, (image.width * minOf(1f, scale)).roundToInt())
        val height = max(1, (image.height * minOf(1f, scale)).roundToInt())
        var depth = resize(normalized, width, height)
        val t2 = System.nanoTime()
        if (edgeAware) {
            val guide = Bitmap.createScaledBitmap(image, width, height, true)
            val radius = max(2, width / 80)
            depth = guidedFilter(depth, guide, radius, eps = 2e-3f)
            depth = dilate(depth, radius = 2)
        }
        val t3 = System.nanoTime()
        return RefinedDepth(depth, PostProcessTimings(ms(t0, t1), ms(t1, t2), ms(t2, t3)))
    }

    /** Ramène la sortie brute dans [0, 1] à partir des percentiles 1 % et 99 % (robuste aux valeurs aberrantes). */
    fun normalize(raw: RawDepth, invert: Boolean = false): DepthMap {
        val values = raw.values
        val stride = max(1, values.size / 8192)
        val samples = FloatArray((values.size + stride - 1) / stride) { values[it * stride] }
        samples.sort()
        val low = samples[((samples.size - 1) * 0.01f).toInt()]
        val high = samples[((samples.size - 1) * 0.99f).toInt()]
        val range = high - low
        val out = FloatArray(values.size)
        if (range > 1e-6f) {
            for (i in values.indices) {
                val v = ((values[i] - low) / range).coerceIn(0f, 1f)
                out[i] = if (invert) 1f - v else v
            }
        }
        return DepthMap(raw.width, raw.height, out)
    }

    /** Redimensionnement bilinéaire. */
    fun resize(source: DepthMap, width: Int, height: Int): DepthMap {
        if (source.width == width && source.height == height) return source
        val out = FloatArray(width * height)
        for (y in 0 until height) {
            val v = (y + 0.5f) / height
            val row = y * width
            for (x in 0 until width) out[row + x] = source.sample((x + 0.5f) / width, v)
        }
        return DepthMap(width, height, out)
    }

    /**
     * Filtre guidé (He et al., 2010) avec la luminance de l'image comme guide :
     * les contours de la profondeur se calent sur ceux de l'image.
     */
    fun guidedFilter(depth: DepthMap, guide: Bitmap, radius: Int, eps: Float): DepthMap {
        val width = depth.width
        val height = depth.height
        require(guide.width == width && guide.height == height) { "Guide et profondeur de tailles différentes" }
        val count = width * height
        val pixels = IntArray(count)
        guide.getPixels(pixels, 0, width, 0, 0, width, height)
        val luma = FloatArray(count) {
            val c = pixels[it]
            (0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)) / 255f
        }
        val p = depth.values
        val meanI = boxMean(luma, width, height, radius)
        val meanP = boxMean(p, width, height, radius)
        val corrIp = boxMean(FloatArray(count) { luma[it] * p[it] }, width, height, radius)
        val corrII = boxMean(FloatArray(count) { luma[it] * luma[it] }, width, height, radius)
        val a = FloatArray(count)
        val b = FloatArray(count)
        for (i in 0 until count) {
            val varianceI = corrII[i] - meanI[i] * meanI[i]
            val covarianceIp = corrIp[i] - meanI[i] * meanP[i]
            a[i] = covarianceIp / (varianceI + eps)
            b[i] = meanP[i] - a[i] * meanI[i]
        }
        val meanA = boxMean(a, width, height, radius)
        val meanB = boxMean(b, width, height, radius)
        return DepthMap(width, height, FloatArray(count) { (meanA[it] * luma[it] + meanB[it]).coerceIn(0f, 1f) })
    }

    /** Dilatation du premier plan (maximum local) : repousse les halos du côté du fond. */
    fun dilate(source: DepthMap, radius: Int): DepthMap {
        val width = source.width
        val height = source.height
        val input = source.values
        val horizontal = FloatArray(input.size)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                var m = input[row + x]
                for (k in maxOf(0, x - radius)..minOf(width - 1, x + radius)) m = max(m, input[row + k])
                horizontal[row + x] = m
            }
        }
        val out = FloatArray(input.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var m = horizontal[y * width + x]
                for (k in maxOf(0, y - radius)..minOf(height - 1, y + radius)) m = max(m, horizontal[k * width + x])
                out[y * width + x] = m
            }
        }
        return DepthMap(width, height, out)
    }

    /** Moyenne sur une fenêtre carrée de côté 2r + 1, en O(n) (sommes glissantes séparables). */
    private fun boxMean(source: FloatArray, width: Int, height: Int, r: Int): FloatArray {
        val tmp = FloatArray(source.size)
        for (y in 0 until height) {
            val row = y * width
            var sum = 0.0
            for (x in 0..minOf(r, width - 1)) sum += source[row + x]
            for (x in 0 until width) {
                val count = minOf(x + r, width - 1) - maxOf(x - r, 0) + 1
                tmp[row + x] = (sum / count).toFloat()
                if (x + r + 1 < width) sum += source[row + x + r + 1]
                if (x - r >= 0) sum -= source[row + x - r]
            }
        }
        val out = FloatArray(source.size)
        for (x in 0 until width) {
            var sum = 0.0
            for (y in 0..minOf(r, height - 1)) sum += tmp[y * width + x]
            for (y in 0 until height) {
                val count = minOf(y + r, height - 1) - maxOf(y - r, 0) + 1
                out[y * width + x] = (sum / count).toFloat()
                if (y + r + 1 < height) sum += tmp[(y + r + 1) * width + x]
                if (y - r >= 0) sum -= tmp[(y - r) * width + x]
            }
        }
        return out
    }

    /** Vignette en niveaux de gris de la sortie brute (pour l'affichage). */
    fun preview(raw: RawDepth, maxSide: Int = 256): Bitmap {
        val normalized = normalize(raw)
        val scale = minOf(1f, maxSide.toFloat() / max(raw.width, raw.height))
        val small = resize(normalized, max(1, (raw.width * scale).roundToInt()), max(1, (raw.height * scale).roundToInt()))
        val pixels = IntArray(small.values.size) {
            val g = (small.values[it] * 255f).roundToInt()
            (0xFF shl 24) or (g shl 16) or (g shl 8) or g
        }
        return Bitmap.createBitmap(pixels, small.width, small.height, Bitmap.Config.ARGB_8888)
    }

    private fun ms(start: Long, end: Long) = (end - start) / 1e6
}
