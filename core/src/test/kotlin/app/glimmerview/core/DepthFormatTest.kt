package app.glimmerview.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream

class DepthFormatTest {

    @Test
    fun `write then read keeps dimensions and 16-bit precision`() {
        val values = FloatArray(12) { it / 11f }
        val depth = DepthMap(4, 3, values)
        val bytes = ByteArrayOutputStream().also { WallpaperStore.writeDepth(depth, it) }.toByteArray()

        val read = WallpaperStore.readDepth(DataInputStream(ByteArrayInputStream(bytes)))

        assertEquals(4, read.width)
        assertEquals(3, read.height)
        values.forEachIndexed { i, v -> assertEquals(v, read.values[i], 1f / 65535f) }
    }

    @Test
    fun `bilinear sample interpolates between pixel centers`() {
        val depth = DepthMap(2, 1, floatArrayOf(0f, 1f))
        assertEquals(0f, depth.sample(0.25f, 0.5f), 1e-6f)
        assertEquals(0.5f, depth.sample(0.5f, 0.5f), 1e-6f)
        assertEquals(1f, depth.sample(0.75f, 0.5f), 1e-6f)
        // Bornage aux bords
        assertEquals(1f, depth.sample(1.5f, 0.5f), 1e-6f)
    }

    @Test
    fun `scaled map averages each area and keeps the aspect ratio`() {
        // Colonnes alternées 0 / 1 : chaque pixel réduit (2×2) vaut la moyenne, 0,5.
        val depth = DepthMap(8, 4, FloatArray(32) { (it % 2).toFloat() })
        val small = depth.scaledToFit(4)
        assertEquals(4, small.width)
        assertEquals(2, small.height)
        small.values.forEach { assertEquals(0.5f, it, 1e-6f) }
        assertEquals(depth, depth.scaledToFit(8))
    }
}
