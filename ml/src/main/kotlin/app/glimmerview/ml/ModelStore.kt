package app.glimmerview.ml

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/** Modèles téléchargés à la demande, rangés dans le stockage privé de l'app. */
class ModelStore(context: Context) {

    private val directory = File(context.filesDir, "models").apply { mkdirs() }

    fun file(spec: ModelSpec): File = File(directory, spec.fileName)

    fun isDownloaded(spec: ModelSpec): Boolean = file(spec).let { it.exists() && it.length() > 0 }

    fun delete(spec: ModelSpec) {
        file(spec).delete()
    }

    /** Supprime les fichiers qui ne correspondent plus à aucun modèle du catalogue (anciens essais). */
    fun pruneUnknown(known: List<ModelSpec> = ModelCatalog.all) {
        val keep = known.flatMap { listOf(it.fileName, it.fileName + ".part") }.toSet()
        directory.listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
    }

    /**
     * Télécharge le modèle (et l'extrait si c'est une archive). Bloquant : à appeler hors du
     * thread principal. [onProgress] reçoit (octets reçus, octets attendus).
     * Un téléchargement interrompu reprend là où il s'était arrêté (requête HTTP Range).
     */
    fun download(spec: ModelSpec, onProgress: (Long, Long) -> Unit) {
        val part = File(directory, spec.fileName + ".part")
        var attempt = 0
        while (true) {
            try {
                fetch(spec, part, onProgress)
                break
            } catch (e: IOException) {
                if (++attempt >= MAX_ATTEMPTS) throw e
                Thread.sleep(2_000L * attempt)
            }
        }
        unpack(spec, part)
    }

    private fun fetch(spec: ModelSpec, part: File, onProgress: (Long, Long) -> Unit) {
        val existing = if (part.exists()) part.length() else 0L
        val connection = URL(spec.url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            if (existing > 0) connection.setRequestProperty("Range", "bytes=$existing-")
            connection.connect()
            val code = connection.responseCode
            if (code == 416) return // déjà complet
            check(code in 200..299) { "HTTP $code pour ${spec.url}" }
            val resumed = code == 206
            val offset = if (resumed) existing else 0L
            val total = connection.contentLengthLong.takeIf { it > 0 }?.plus(offset) ?: spec.approxDownloadBytes
            connection.inputStream.use { input ->
                FileOutputStream(part, resumed).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    var received = offset
                    var lastReport = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        received += read
                        if (received - lastReport >= 1_000_000) {
                            lastReport = received
                            onProgress(received, total)
                        }
                    }
                    onProgress(received, total)
                    if (connection.contentLengthLong > 0 && received < total) {
                        throw IOException("Téléchargement incomplet : $received / $total octets")
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun unpack(spec: ModelSpec, part: File) {
        val target = file(spec)
        val entryName = spec.zipEntry
        val candidate = if (entryName == null) {
            part
        } else {
            val tmp = File(directory, spec.fileName + ".tmp")
            ZipInputStream(part.inputStream().buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null && entry.name != entryName) entry = zip.nextEntry
                checkNotNull(entry) { "« $entryName » absent de l'archive" }
                tmp.outputStream().use { zip.copyTo(it) }
            }
            part.delete()
            tmp
        }
        // Un fichier corrompu ou modifié en route n'est jamais chargé par le runtime.
        val actual = sha256(candidate)
        if (!actual.equals(spec.sha256, ignoreCase = true)) {
            candidate.delete()
            throw IOException("Empreinte SHA-256 inattendue pour ${spec.fileName} : $actual")
        }
        check(candidate.renameTo(target)) { "Renommage impossible : $target" }
    }

    private companion object {
        const val MAX_ATTEMPTS = 4

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
