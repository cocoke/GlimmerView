package app.glimmerview.format

import app.glimmerview.core.DepthMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class GrayPngTest {

    init {
        System.setProperty("java.awt.headless", "true")
    }

    /** Valeurs variées : tous les octets possibles apparaissent, et les filtres ont du travail. */
    private fun sample(width: Int = 61, height: Int = 37): DepthMap =
        DepthMap(width, height, FloatArray(width * height) { i -> ((i % width) * 1031 + (i / width) * 7919) % 65536 / 65535f })

    private fun level(value: Float): Int = (value * 65535f + 0.5f).toInt()

    @Test
    fun roundTripKeepsEverySixteenBitLevel() {
        val depth = sample()
        val decoded = GrayPng.decode(GrayPng.encode(depth))
        assertEquals(depth.width, decoded.width)
        assertEquals(depth.height, decoded.height)
        for (i in depth.values.indices) assertEquals(level(depth.values[i]), level(decoded.values[i]))
    }

    @Test
    fun standardDecodersReadOurFiles() {
        val depth = sample()
        val image = ImageIO.read(ByteArrayInputStream(GrayPng.encode(depth)))
        assertNotNull("ImageIO doit lire le fichier", image)
        assertEquals(BufferedImage.TYPE_USHORT_GRAY, image.type)
        for (y in 0 until depth.height) for (x in 0 until depth.width) {
            assertEquals(level(depth.values[y * depth.width + x]), image.raster.getSample(x, y, 0))
        }
    }

    @Test
    fun readsSixteenBitGrayFromAnotherEncoder() {
        val image = BufferedImage(53, 29, BufferedImage.TYPE_USHORT_GRAY)
        for (y in 0 until image.height) for (x in 0 until image.width) image.raster.setSample(x, y, 0, (x * 977 + y * 4241) % 65536)
        val decoded = GrayPng.decode(png(image))
        for (y in 0 until image.height) for (x in 0 until image.width) {
            assertEquals(image.raster.getSample(x, y, 0), level(decoded.values[y * image.width + x]))
        }
    }

    @Test
    fun readsEightBitGrayAndRgb() {
        for (type in intArrayOf(BufferedImage.TYPE_BYTE_GRAY, BufferedImage.TYPE_3BYTE_BGR)) {
            val image = BufferedImage(47, 31, type)
            for (y in 0 until image.height) for (x in 0 until image.width) {
                val value = (x * 13 + y * 7) % 256
                for (band in 0 until image.raster.numBands) image.raster.setSample(x, y, band, value)
            }
            val decoded = GrayPng.decode(png(image))
            for (y in 0 until image.height) for (x in 0 until image.width) {
                assertEquals(image.raster.getSample(x, y, 0) / 255f, decoded.values[y * image.width + x], 1e-6f)
            }
        }
    }

    @Test
    fun damagedFilesAreRefused() {
        val bytes = GrayPng.encode(sample())
        assertReason(DwpException.Reason.CORRUPT) { GrayPng.decode(bytes.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }) }
        assertReason(DwpException.Reason.CORRUPT) { GrayPng.decode(bytes.copyOf(bytes.size / 2)) }
        assertReason(DwpException.Reason.CORRUPT) { GrayPng.decode("pas une image".toByteArray()) }
    }

    @Test
    fun oversizedMapsAreRefusedBeforeDecoding() {
        val bytes = GrayPng.encode(sample(100, 40))
        assertReason(DwpException.Reason.TOO_LARGE) { GrayPng.decode(bytes, maxSide = 64) }
        assertReason(DwpException.Reason.TOO_LARGE) { GrayPng.decode(bytes, maxPixels = 3_000) }
    }

    private fun png(image: BufferedImage): ByteArray =
        ByteArrayOutputStream().also { check(ImageIO.write(image, "png", it)) }.toByteArray()
}

/** Vérifie qu'un bloc lève une [DwpException] pour la raison attendue. */
internal fun assertReason(reason: DwpException.Reason, block: () -> Unit) {
    try {
        block()
        fail("DwpException ($reason) attendue")
    } catch (e: DwpException) {
        assertEquals(e.message, reason, e.reason)
    }
}
