package app.glimmerview.ml

import app.glimmerview.core.DepthMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DepthPostProcessorTest {

    @Test
    fun `normalize maps robust range to 0-1 and clamps outliers`() {
        val values = FloatArray(1000) { it.toFloat() }
        values[0] = -1e6f // valeur aberrante
        values[999] = 1e6f
        val depth = DepthPostProcessor.normalize(RawDepth(100, 10, values))

        assertEquals(0f, depth.values[0], 0f)
        assertEquals(1f, depth.values[999], 0f)
        assertTrue(depth.values.all { it in 0f..1f })
        assertEquals(0.5f, depth.values[500], 0.02f)
    }

    @Test
    fun `normalize of a flat map gives zeros`() {
        val depth = DepthPostProcessor.normalize(RawDepth(4, 4, FloatArray(16) { 3f }))
        assertTrue(depth.values.all { it == 0f })
    }

    @Test
    fun `invert flips near and far`() {
        val raw = RawDepth(3, 1, floatArrayOf(0f, 5f, 10f))
        val depth = DepthPostProcessor.normalize(raw, invert = true)
        assertEquals(1f, depth.values[0], 1e-6f)
        assertEquals(0f, depth.values[2], 1e-6f)
    }

    @Test
    fun `resize keeps a constant map constant`() {
        val source = DepthMap(5, 7, FloatArray(35) { 0.42f })
        val resized = DepthPostProcessor.resize(source, 13, 3)
        assertEquals(13, resized.width)
        assertEquals(3, resized.height)
        assertTrue(resized.values.all { kotlin.math.abs(it - 0.42f) < 1e-6f })
    }

    @Test
    fun `dilate grows the foreground by the radius`() {
        val values = FloatArray(9 * 9)
        values[4 * 9 + 4] = 1f // un pixel proche au centre
        val dilated = DepthPostProcessor.dilate(DepthMap(9, 9, values), radius = 2)
        assertEquals(1f, dilated.values[2 * 9 + 2], 0f) // coin du carré 5×5
        assertEquals(1f, dilated.values[6 * 9 + 6], 0f)
        assertEquals(0f, dilated.values[1 * 9 + 4], 0f) // hors du rayon
    }
}
