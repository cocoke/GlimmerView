package app.glimmerview.data

import app.glimmerview.format.DwpArchive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.Locale

class LibraryTest {

    private val root = File("src/main/assets/library")
    private val index = LibraryIndex.parse(File(root, "index.json").readText())

    @Test
    fun bundledIndexDescribesEveryWork() {
        assertTrue("au moins une œuvre", index.works.isNotEmpty())
        assertEquals("identifiants uniques", index.works.size, index.works.map { it.id }.toSet().size)
        for (entry in index.works) {
            assertTrue("aperçu manquant : ${entry.previewPath}", File(root, entry.previewPath).isFile)
            assertTrue("${entry.id} : titre en français", entry.title(Locale.FRENCH).isNotBlank())
            assertTrue("${entry.id} : titre en anglais", entry.title(Locale.ENGLISH).isNotBlank())
        }
    }

    @Test
    fun bundledPackagesMatchTheirIndexEntry() {
        val bundled = File(root, "works").listFiles().orEmpty()
        assertTrue("des œuvres livrées avec l'app", bundled.isNotEmpty())
        val entries = index.works.associateBy { it.packagePath }
        for (file in bundled) {
            val entry = entries["works/${file.name}"] ?: error("paquet absent de l'index : ${file.name}")
            val bytes = file.readBytes()
            assertEquals(entry.packageSize, bytes.size.toLong())
            assertEquals(entry.sha256, MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
            assertEquals(entry.id, bytes.inputStream().use { DwpArchive.readImage(it) }.manifest.id)
        }
    }

    @Test
    fun suspiciousEntriesAreSkipped() {
        val good = """{"id": "ok", "room": "water", "title": {"en": "Ok"}, "author": "A", "medium": {"en": "Photo"},
            "package": {"path": "works/ok.dwp", "size": 10, "sha256": "${"a".repeat(64)}"}, "preview": "previews/ok.webp"}"""
        val traversal = good.replace("works/ok.dwp", "../../secret").replace("\"ok\"", "\"bad\"")
        val unknownRoom = good.replace("water", "moon").replace("\"ok\"", "\"room\"")
        val parsed = LibraryIndex.parse("""{"format": "glimmerview-library", "version": 1, "revision": 3, "works": [$good, $traversal, $unknownRoom]}""")
        assertEquals(3, parsed.revision)
        assertEquals(listOf("ok"), parsed.works.map { it.id })
        assertEquals("Ok", parsed.works.single().title(Locale.FRENCH))
    }
}
