package app.glimmerview.format

import app.glimmerview.core.DepthMap
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DwpArchiveTest {

    private val depth = DepthMap(40, 30, FloatArray(40 * 30) { (it % 40) / 39f })
    private val color = "RIFF....WEBPVP8 image".toByteArray()
    private val thumbnail = "RIFF....WEBPVP8 vignette".toByteArray()

    private fun read(bytes: ByteArray, limits: DwpLimits = DwpLimits()) = DwpArchive.read(ByteArrayInputStream(bytes), limits)

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun writtenPackageReadsBack() {
        val manifest = DwpManifest(
            id = "wmusl7990-27e",
            title = "Crêtes à l'aube",
            author = DwpAuthor("Jane Doe", "https://example.org/jane"),
            license = "CC-BY-4.0",
            medium = "Photographie",
            language = "fr-FR",
            width = 1080,
            height = 2400,
            defaults = DwpDefaults(strength = 0.035f, focus = 0.62f, zoom = 1f),
            tags = listOf("nature"),
        )
        val out = ByteArrayOutputStream()
        DwpArchive.write(out, manifest, color, depth, thumbnail)

        val pkg = read(out.toByteArray())
        assertEquals("Crêtes à l'aube", pkg.manifest.title)
        assertEquals(DwpAuthor("Jane Doe", "https://example.org/jane"), pkg.manifest.author)
        assertEquals("CC-BY-4.0", pkg.manifest.license)
        assertEquals(0.035f, pkg.manifest.defaults!!.strength!!, 1e-6f)
        assertEquals(0.62f, pkg.manifest.defaults!!.focus!!, 1e-6f)
        assertEquals(listOf(DwpLayer(DwpFormat.COLOR, DwpFormat.DEPTH)), pkg.manifest.layers)
        assertEquals(DwpFormat.THUMBNAIL, pkg.manifest.thumbnail)
        assertEquals(listOf("nature"), pkg.manifest.tags)
        assertArrayEquals(color, pkg.color)
        assertArrayEquals(thumbnail, pkg.thumbnail)
        assertEquals(depth.width, pkg.depth.width)
        for (i in depth.values.indices) assertEquals(depth.values[i], pkg.depth.values[i], 1f / 65535f)
    }

    @Test
    fun unknownFieldsAreIgnoredAndTheLastLayerIsTheMainImage() {
        val manifest = """
            {
              "format": "dwp", "formatVersion": 1, "futureField": {"x": [1, 2]},
              "author": "Anonyme du XIXe",
              "layers": [
                {"color": "background.webp", "depth": "background_depth.png"},
                {"color": "photo.jpg", "depth": "photo depth.png", "mask": "mask.png"}
              ]
            }
        """.trimIndent()
        val pkg = read(
            zip(
                "manifest.json" to manifest.toByteArray(),
                "background.webp" to "fond".toByteArray(),
                "background_depth.png" to GrayPng.encode(depth),
                "photo.jpg" to color,
                "photo depth.png" to GrayPng.encode(depth),
            ),
        )
        assertArrayEquals(color, pkg.color)
        assertEquals(DwpAuthor("Anonyme du XIXe"), pkg.manifest.author)
        assertEquals("mask.png", pkg.manifest.mainLayer.mask)
        assertNull(pkg.thumbnail)
        assertNull(pkg.manifest.defaults)
    }

    @Test
    fun newerVersionsAreRefused() {
        val manifest = """{"format": "dwp", "formatVersion": 2}"""
        assertReason(DwpException.Reason.UNSUPPORTED_VERSION) {
            read(zip("manifest.json" to manifest.toByteArray(), "color.webp" to color, "depth.png" to GrayPng.encode(depth)))
        }
    }

    @Test
    fun otherFilesAreNotPackages() {
        assertReason(DwpException.Reason.NOT_DWP) { read(GrayPng.encode(depth)) }
        assertReason(DwpException.Reason.NOT_DWP) { read(ByteArray(0)) }
        assertReason(DwpException.Reason.NOT_DWP) { read(zip("readme.txt" to "bonjour".toByteArray())) }
        assertReason(DwpException.Reason.NOT_DWP) { read(zip("manifest.json" to """{"format": "epub"}""".toByteArray())) }
    }

    @Test
    fun missingFilesAreReported() {
        val manifest = """{"format": "dwp", "formatVersion": 1}"""
        assertReason(DwpException.Reason.MISSING_ENTRY) { read(zip("manifest.json" to manifest.toByteArray(), "color.webp" to color)) }
    }

    @Test
    fun zipBombsAreStoppedAtTheFirstOversizedFile() {
        // 64 Mo de zéros tiennent en quelques dizaines de kilo-octets une fois compressés.
        val bomb = zip("manifest.json" to """{"format": "dwp"}""".toByteArray(), "depth.png" to ByteArray(64 shl 20))
        assertTrue(bomb.size < 1 shl 20)
        val start = System.nanoTime()
        assertReason(DwpException.Reason.TOO_LARGE) { read(bomb, DwpLimits(maxEntryBytes = 1 shl 20)) }
        assertReason(DwpException.Reason.TOO_LARGE) { read(bomb, DwpLimits(maxTotalBytes = 2L shl 20)) }
        assertTrue("l'arrêt doit être immédiat", System.nanoTime() - start < 2_000_000_000L)
    }

    @Test
    fun tooManyFilesAreRefused() {
        val entries = (0 until 70).map { "f$it.txt" to ByteArray(1) }.toTypedArray()
        assertReason(DwpException.Reason.TOO_LARGE) { read(zip(*entries)) }
    }

    @Test
    fun manifestKeepsEveryField() {
        val manifest = DwpManifest(
            id = "exemple",
            title = "Montagne",
            author = DwpAuthor("Jane"),
            license = "CC0-1.0",
            sourceUrl = "https://example.org/photo/123",
            aiGenerated = false,
            medium = "Illustration numérique, 5 plans",
            language = "fr",
            created = "2026-10-04T08:00:00Z",
            generator = DwpGenerator(app = "GlimmerView 0.1.0", depthModel = "depth-anything-v2-small"),
            width = 1440,
            height = 3200,
            layers = listOf(DwpLayer("a.webp", "a.png"), DwpLayer("b.webp", "b.png", "m.png")),
            defaults = DwpDefaults(strength = 0.05f, focus = 0.5f, zoom = 1.06f, motion = "gyro"),
            thumbnail = "t.webp",
            tags = listOf("nature", "montagne"),
        )
        assertEquals(manifest, DwpManifest.parse(manifest.toJson()))
    }
}
