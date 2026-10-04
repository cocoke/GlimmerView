package app.glimmerview.format

import app.glimmerview.core.DepthMap
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import kotlin.math.abs

/**
 * PNG en niveaux de gris pour les cartes de profondeur, écrit à la main avec java.util.zip :
 * Android ne sait pas produire de PNG 16 bits en niveaux de gris.
 *
 * - Écriture : gris 16 bits (0 = loin, 65535 = proche), filtre choisi ligne par ligne.
 * - Lecture : PNG non entrelacé en 8 ou 16 bits, gris, gris + alpha, RVB ou RVBA ;
 *   seul le premier canal compte (une carte enregistrée en RVB a trois canaux égaux).
 */
object GrayPng {

    fun encode(depth: DepthMap): ByteArray {
        val width = depth.width
        val rowBytes = width * BYTES_16
        var previous = ByteArray(rowBytes)
        var current = ByteArray(rowBytes)
        val filtered = Array(FILTER_TYPES) { ByteArray(rowBytes) }
        val pixels = ByteArrayOutputStream()
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION)
        try {
            DeflaterOutputStream(pixels, deflater, BUFFER).use { zlib ->
                for (y in 0 until depth.height) {
                    val offset = y * width
                    for (x in 0 until width) {
                        val value = (depth.values[offset + x].coerceIn(0f, 1f) * 65535f + 0.5f).toInt()
                        current[x * 2] = (value ushr 8).toByte()
                        current[x * 2 + 1] = value.toByte()
                    }
                    val type = bestFilter(current, previous, filtered)
                    zlib.write(type)
                    zlib.write(filtered[type])
                    val swap = previous
                    previous = current
                    current = swap
                }
            }
        } finally {
            deflater.end()
        }

        val header = ByteArrayOutputStream(HEADER_LENGTH).apply {
            writeInt(width)
            writeInt(depth.height)
            write(16) // profondeur de bits
            write(COLOR_GRAY)
            write(0) // compression : deflate
            write(0) // filtres : méthode standard
            write(0) // pas d'entrelacement
        }
        return ByteArrayOutputStream().apply {
            write(SIGNATURE)
            writeChunk("IHDR", header.toByteArray())
            writeChunk("IDAT", pixels.toByteArray())
            writeChunk("IEND", ByteArray(0))
        }.toByteArray()
    }

    /**
     * Décode une carte de profondeur ; [maxSide] et [maxPixels] bornent la mémoire avant
     * toute décompression.
     */
    fun decode(bytes: ByteArray, maxSide: Int = 8192, maxPixels: Int = 12_000_000): DepthMap {
        if (bytes.size < SIGNATURE.size || SIGNATURE.indices.any { bytes[it] != SIGNATURE[it] }) {
            corrupt("La carte de profondeur n'est pas un PNG")
        }
        var width = 0
        var height = 0
        var bitDepth = 0
        var colorType = -1
        val compressed = ByteArrayOutputStream()
        var position = SIGNATURE.size
        while (position + 12 <= bytes.size) {
            val length = readInt(bytes, position)
            val type = String(bytes, position + 4, 4, Charsets.US_ASCII)
            val dataStart = position + 8
            if (length < 0 || length > bytes.size - dataStart - 4) corrupt("Bloc PNG « $type » tronqué")
            val crc = CRC32().apply { update(bytes, position + 4, 4 + length) }
            if (crc.value.toInt() != readInt(bytes, dataStart + length)) corrupt("Somme de contrôle fausse dans le bloc « $type »")
            when (type) {
                "IHDR" -> {
                    if (length != HEADER_LENGTH) corrupt("En-tête PNG invalide")
                    width = readInt(bytes, dataStart)
                    height = readInt(bytes, dataStart + 4)
                    bitDepth = bytes[dataStart + 8].toInt() and 0xFF
                    colorType = bytes[dataStart + 9].toInt() and 0xFF
                    val interlaced = bytes[dataStart + 12].toInt() != 0
                    if (width <= 0 || height <= 0) corrupt("Dimensions PNG invalides : ${width}×$height")
                    if (width > maxSide || height > maxSide || width.toLong() * height > maxPixels) {
                        throw DwpException(DwpException.Reason.TOO_LARGE, "Carte de profondeur de ${width}×$height")
                    }
                    if (colorType !in CHANNELS.keys) corrupt("Type de couleur PNG non pris en charge : $colorType")
                    if (bitDepth != 8 && bitDepth != 16) corrupt("Profondeur de $bitDepth bits non prise en charge (8 ou 16)")
                    if (interlaced) corrupt("PNG entrelacé non pris en charge")
                }
                "IDAT" -> {
                    if (colorType < 0) corrupt("Données PNG avant l'en-tête")
                    compressed.write(bytes, dataStart, length)
                }
                "IEND" -> break
                // Les blocs auxiliaires (initiale minuscule) sont ignorés ; la palette suggérée d'un PNG RVB aussi.
                "PLTE" -> Unit
                else -> if (type[0].isUpperCase()) corrupt("Bloc PNG critique inconnu : $type")
            }
            position = dataStart + length + 4
        }
        if (colorType < 0 || compressed.size() == 0) corrupt("PNG sans image")

        val bytesPerPixel = CHANNELS.getValue(colorType) * bitDepth / 8
        val rowBytes = width * bytesPerPixel
        val rows = inflate(compressed.toByteArray(), height.toLong() * (rowBytes + 1))
        val values = FloatArray(width * height)
        var previousRow = -1
        for (y in 0 until height) {
            val filterAt = y * (rowBytes + 1)
            val row = filterAt + 1
            unfilter(rows, row, previousRow, rowBytes, bytesPerPixel, rows[filterAt].toInt() and 0xFF)
            val offset = y * width
            for (x in 0 until width) {
                val p = row + x * bytesPerPixel
                values[offset + x] = if (bitDepth == 16) {
                    (((rows[p].toInt() and 0xFF) shl 8) or (rows[p + 1].toInt() and 0xFF)) / 65535f
                } else {
                    (rows[p].toInt() and 0xFF) / 255f
                }
            }
            previousRow = row
        }
        return DepthMap(width, height, values)
    }

    /** Heuristique classique : le filtre dont la somme des écarts (signés) est la plus petite. */
    private fun bestFilter(row: ByteArray, prior: ByteArray, out: Array<ByteArray>): Int {
        var best = 0
        var bestScore = Long.MAX_VALUE
        for (type in 0 until FILTER_TYPES) {
            val target = out[type]
            var score = 0L
            for (i in row.indices) {
                val a = if (i >= BYTES_16) row[i - BYTES_16].toInt() and 0xFF else 0
                val b = prior[i].toInt() and 0xFF
                val c = if (i >= BYTES_16) prior[i - BYTES_16].toInt() and 0xFF else 0
                val value = ((row[i].toInt() and 0xFF) - predict(type, a, b, c)).toByte()
                target[i] = value
                score += abs(value.toInt())
            }
            if (score < bestScore) {
                bestScore = score
                best = type
            }
        }
        return best
    }

    /** Annule le filtre d'une ligne, sur place ; la ligne précédente est déjà reconstruite. */
    private fun unfilter(data: ByteArray, row: Int, previousRow: Int, rowBytes: Int, bytesPerPixel: Int, type: Int) {
        if (type >= FILTER_TYPES) corrupt("Filtre PNG inconnu : $type")
        if (type == 0) return
        for (i in 0 until rowBytes) {
            val a = if (i >= bytesPerPixel) data[row + i - bytesPerPixel].toInt() and 0xFF else 0
            val b = if (previousRow >= 0) data[previousRow + i].toInt() and 0xFF else 0
            val c = if (previousRow >= 0 && i >= bytesPerPixel) data[previousRow + i - bytesPerPixel].toInt() and 0xFF else 0
            data[row + i] = ((data[row + i].toInt() and 0xFF) + predict(type, a, b, c)).toByte()
        }
    }

    private fun predict(type: Int, a: Int, b: Int, c: Int): Int = when (type) {
        1 -> a
        2 -> b
        3 -> (a + b) ushr 1
        4 -> paeth(a, b, c)
        else -> 0
    }

    private fun paeth(a: Int, b: Int, c: Int): Int {
        val p = a + b - c
        val pa = abs(p - a)
        val pb = abs(p - b)
        val pc = abs(p - c)
        return if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
    }

    /** Décompresse exactement [expected] octets : ni plus (bombe), ni moins (image tronquée). */
    private fun inflate(data: ByteArray, expected: Long): ByteArray {
        if (expected > Int.MAX_VALUE) throw DwpException(DwpException.Reason.TOO_LARGE, "Carte de profondeur trop grande")
        val out = ByteArray(expected.toInt())
        val inflater = Inflater()
        try {
            inflater.setInput(data)
            var filled = 0
            while (filled < out.size) {
                val n = inflater.inflate(out, filled, out.size - filled)
                if (n == 0 && (inflater.finished() || inflater.needsInput() || inflater.needsDictionary())) break
                filled += n
            }
            if (filled < out.size) corrupt("Carte de profondeur tronquée")
        } catch (e: DataFormatException) {
            throw DwpException(DwpException.Reason.CORRUPT, "Données PNG illisibles", e)
        } finally {
            inflater.end()
        }
        return out
    }

    private fun readInt(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() and 0xFF) shl 24) or ((bytes[at + 1].toInt() and 0xFF) shl 16) or
            ((bytes[at + 2].toInt() and 0xFF) shl 8) or (bytes[at + 3].toInt() and 0xFF)

    private fun ByteArrayOutputStream.writeInt(value: Int) {
        write(value ushr 24)
        write(value ushr 16)
        write(value ushr 8)
        write(value)
    }

    private fun ByteArrayOutputStream.writeChunk(type: String, data: ByteArray) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        writeInt(data.size)
        write(typeBytes)
        write(data)
        writeInt(CRC32().apply { update(typeBytes); update(data) }.value.toInt())
    }

    private fun corrupt(message: String): Nothing = throw DwpException(DwpException.Reason.CORRUPT, message)

    private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    /** Nombre de canaux selon le type de couleur PNG (la palette, type 3, n'a pas de sens ici). */
    private val CHANNELS = mapOf(0 to 1, 2 to 3, 4 to 2, 6 to 4)

    private const val COLOR_GRAY = 0
    private const val BYTES_16 = 2
    private const val FILTER_TYPES = 5
    private const val HEADER_LENGTH = 13
    private const val BUFFER = 64 * 1024
}
