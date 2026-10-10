package app.glimmerview.data

import android.content.Context
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/** Une œuvre de la bibliothèque, telle que la décrit l'index (docs/library.md). */
class LibraryEntry(
    val id: String,
    val room: Room,
    /** Titre et technique par langue (« fr », « en »…). */
    private val titles: Map<String, String>,
    val author: String,
    private val mediums: Map<String, String>,
    val year: String,
    val circa: Boolean,
    val detail: Boolean,
    /** Identifiant SPDX, ou « public-domain ». */
    val license: String,
    val sourceUrl: String?,
    /** Plan immobile conseillé (disparité 0..1). */
    val focus: Float?,
    val packagePath: String,
    val packageSize: Long,
    val sha256: String,
    val previewPath: String,
) {
    fun title(locale: Locale): String = localized(titles, locale)

    fun medium(locale: Locale): String = localized(mediums, locale)

    private fun localized(texts: Map<String, String>, locale: Locale): String =
        texts[locale.language] ?: texts["en"] ?: texts.values.first()
}

/** L'index de la bibliothèque : la liste des œuvres, dans l'ordre de la galerie. */
class LibraryIndex(val revision: Int, val works: List<LibraryEntry>) {

    companion object {
        const val FORMAT = "glimmerview-library"
        const val VERSION = 1

        /** Lit un index ; les œuvres mal décrites sont ignorées, un index d'un autre format est refusé. */
        fun parse(json: String): LibraryIndex {
            val o = JSONObject(json)
            if (o.optString("format") != FORMAT) throw JSONException("Pas un index de bibliothèque GlimmerView")
            if (o.optInt("version", 1) > VERSION) throw JSONException("Index d'une version plus récente")
            val array = o.optJSONArray("works") ?: throw JSONException("Index sans œuvres")
            val works = (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let(::entry) }
            return LibraryIndex(o.optInt("revision", 0), works.distinctBy { it.id })
        }

        private fun entry(o: JSONObject): LibraryEntry? = runCatching {
            val pkg = o.getJSONObject("package")
            val id = o.getString("id")
            require(id.matches(ID)) { "Identifiant invalide : $id" }
            val sha256 = pkg.getString("sha256").lowercase(Locale.ROOT)
            require(sha256.matches(SHA256)) { "Empreinte invalide" }
            val size = pkg.getLong("size")
            require(size in 1..MAX_PACKAGE_BYTES) { "Taille invalide" }
            LibraryEntry(
                id = id,
                room = Room.valueOf(o.getString("room").uppercase(Locale.ROOT)),
                titles = texts(o.getJSONObject("title")),
                author = o.getString("author"),
                mediums = texts(o.getJSONObject("medium")),
                year = o.optString("year"),
                circa = o.optBoolean("circa"),
                detail = o.optBoolean("detail"),
                license = o.optString("license"),
                sourceUrl = o.optString("sourceUrl").takeIf { it.isNotEmpty() },
                focus = o.optDouble("focus", Double.NaN).toFloat().takeIf { it.isFinite() }?.coerceIn(0f, 1f),
                packagePath = path(pkg.getString("path")),
                packageSize = size,
                sha256 = sha256,
                previewPath = path(o.getString("preview")),
            )
        }.getOrNull()

        private fun texts(o: JSONObject): Map<String, String> {
            val map = o.keys().asSequence().associateWith { o.optString(it) }.filterValues { it.isNotBlank() }
            require(map.isNotEmpty()) { "Texte manquant" }
            return map
        }

        /** Chemin relatif à la racine de la bibliothèque, sans remontée possible. */
        private fun path(value: String): String {
            require(value.matches(PATH) && ".." !in value) { "Chemin invalide : $value" }
            return value
        }

        private val ID = Regex("[a-z0-9-]{1,48}")
        private val SHA256 = Regex("[0-9a-f]{64}")
        private val PATH = Regex("[a-z0-9][a-z0-9/_.-]{0,127}")
        private const val MAX_PACKAGE_BYTES = 64L shl 20
    }
}

/**
 * La bibliothèque de la galerie. L'APK livre un index, les aperçus de toutes les œuvres et
 * quelques paquets ; l'index est mis à jour depuis [baseUrl] et les autres paquets sont
 * téléchargés à la demande, vérifiés par leur empreinte SHA-256, puis gardés sur le téléphone.
 * Les appels réseau et disque sont bloquants : à faire hors du thread principal.
 */
class Library(context: Context, private val baseUrl: String) {

    private val assets = context.assets
    private val root = File(context.filesDir, "library")
    private val works = File(root, "works")
    private val previews = File(root, "previews")
    private val cachedIndex = File(root, "index.json")

    /** L'index le plus récent : celui de l'APK, ou le dernier téléchargé s'il est plus récent. */
    fun index(): LibraryIndex {
        val bundled = assets.open(ASSET_ROOT + INDEX).use { LibraryIndex.parse(it.readBytes().decodeToString()) }
        val cached = runCatching { LibraryIndex.parse(cachedIndex.readText()) }.getOrNull()
        return if (cached != null && cached.revision > bundled.revision) cached else bundled
    }

    /** Télécharge l'index en ligne ; le renvoie s'il est plus récent que [current], sinon null. */
    fun refresh(current: LibraryIndex): LibraryIndex? {
        val bytes = get(INDEX, MAX_INDEX_BYTES)
        val index = LibraryIndex.parse(bytes.decodeToString())
        if (index.revision <= current.revision) return null
        root.mkdirs()
        writeAtomically(cachedIndex, bytes)
        return index
    }

    /** Aperçu de l'œuvre (WebP 540 × 1200) : livré, déjà téléchargé, ou téléchargé maintenant. */
    fun preview(entry: LibraryEntry): ByteArray {
        asset(entry.previewPath)?.let { return it }
        val file = File(previews, "${entry.id}.webp")
        if (file.exists()) return file.readBytes()
        val bytes = get(entry.previewPath, MAX_PREVIEW_BYTES)
        previews.mkdirs()
        writeAtomically(file, bytes)
        return bytes
    }

    /** Vrai si le paquet est sur le téléphone (livré avec l'app ou déjà téléchargé). */
    fun isAvailable(entry: LibraryEntry): Boolean = hasAsset(entry.packagePath) || packageFile(entry).exists()

    /** Ouvre le paquet, sans le télécharger ; [FileNotFoundException] s'il n'est pas sur le téléphone. */
    fun open(entry: LibraryEntry): InputStream =
        if (hasAsset(entry.packagePath)) assets.open(ASSET_ROOT + entry.packagePath) else packageFile(entry).inputStream()

    /** Télécharge le paquet s'il manque. [onProgress] reçoit la fraction reçue (0..1). */
    fun download(entry: LibraryEntry, onProgress: (Float) -> Unit) {
        if (isAvailable(entry)) return
        works.mkdirs()
        val part = File(works, "${entry.id}.dwp.part")
        val digest = MessageDigest.getInstance("SHA-256")
        connect(entry.packagePath).use { connection ->
            connection.stream.use { input ->
                part.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var received = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        received += read
                        if (received > entry.packageSize) throw IOException("Paquet plus grand qu'annoncé")
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        onProgress(received.toFloat() / entry.packageSize)
                    }
                }
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != entry.sha256) {
            part.delete()
            throw IOException("Empreinte inattendue pour ${entry.id}")
        }
        if (!part.renameTo(packageFile(entry))) throw IOException("Enregistrement impossible : ${entry.id}")
    }

    /** Place occupée par les paquets et aperçus téléchargés, en octets. */
    fun downloadedBytes(): Long = listOf(works, previews).sumOf { dir -> dir.listFiles()?.sumOf { it.length() } ?: 0L }

    /** Supprime les paquets téléchargés (les aperçus, légers, restent pour l'affichage hors ligne). */
    fun clearDownloads() {
        works.listFiles()?.forEach { it.delete() }
    }

    private fun packageFile(entry: LibraryEntry) = File(works, "${entry.id}.dwp")

    private fun hasAsset(path: String): Boolean = runCatching { assets.open(ASSET_ROOT + path).close() }.isSuccess

    private fun asset(path: String): ByteArray? = runCatching { assets.open(ASSET_ROOT + path).use { it.readBytes() } }.getOrNull()

    private fun get(path: String, maxBytes: Int): ByteArray = connect(path).use { connection ->
        connection.stream.use { input ->
            val bytes = input.readNBytesCompat(maxBytes + 1)
            if (bytes.size > maxBytes) throw IOException("Fichier trop volumineux : $path")
            bytes
        }
    }

    private fun connect(path: String): Connection {
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", USER_AGENT)
        val code = connection.responseCode
        if (code !in 200..299) {
            connection.disconnect()
            throw IOException("HTTP $code pour $path")
        }
        return Connection(connection)
    }

    private class Connection(private val connection: HttpURLConnection) : AutoCloseable {
        val stream: InputStream get() = connection.inputStream

        override fun close() = connection.disconnect()
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) throw IOException("Enregistrement impossible : ${target.name}")
        }
    }

    private companion object {
        const val ASSET_ROOT = "library/"
        const val INDEX = "index.json"
        const val MAX_INDEX_BYTES = 1 shl 20
        const val MAX_PREVIEW_BYTES = 2 shl 20
        const val USER_AGENT = "GlimmerView (Android; https://github.com/cocoke/GlimmerView)"
    }
}

/** Lit au plus [limit] octets (InputStream.readNBytes n'existe qu'à partir d'Android 13). */
private fun InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    while (out.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
