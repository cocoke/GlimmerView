package app.glimmerview.data

import app.glimmerview.format.DwpArchive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GalleryTest {

    private val assets = File("src/main/assets")

    @Test
    fun everyPackagedWorkHasItsPackage() {
        assertTrue("au moins une œuvre au pack", Gallery.packaged.isNotEmpty())
        for (exhibit in Gallery.packaged) {
            val file = File(assets, exhibit.asset)
            assertTrue("paquet manquant : ${exhibit.asset}", file.isFile)
            val image = file.inputStream().use { DwpArchive.readImage(it) }
            assertEquals(exhibit.id, image.manifest.id)
            assertTrue("${exhibit.id} : image au format d'écran", image.manifest.height > image.manifest.width)
        }
    }

    @Test
    fun everyPackageIsInTheGallery() {
        val ids = Gallery.packaged.map { it.id }.toSet()
        val files = assets.resolve("gallery").listFiles().orEmpty().map { it.nameWithoutExtension }
        assertEquals(ids, files.toSet())
        assertEquals("identifiants en double", ids.size, Gallery.packaged.size)
    }
}
