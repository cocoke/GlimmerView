package app.glimmerview.format

import java.io.IOException

/**
 * Format ouvert `.dwp` : une image, sa carte de profondeur et ses réglages dans une archive ZIP.
 * Spécification de la version 1 : `docs/format.md` (CC0).
 */
object DwpFormat {
    /** Version du format écrite par ce module ; un paquet plus récent est refusé. */
    const val VERSION = 1

    const val EXTENSION = "dwp"

    /** Type MIME utilisé pour partager un paquet (non enregistré auprès de l'IANA). */
    const val MIME_TYPE = "application/x-dwp+zip"

    const val MANIFEST = "manifest.json"
    const val COLOR = "color.webp"
    const val DEPTH = "depth.png"
    const val THUMBNAIL = "thumbnail.webp"

    /** Dimensions conseillées de la vignette (format d'un écran de téléphone). */
    const val THUMBNAIL_WIDTH = 360
    const val THUMBNAIL_HEIGHT = 800

    internal const val NAME = "dwp"
}

/** Paquet refusé. La raison permet à l'app d'expliquer le problème dans la langue de l'utilisateur. */
class DwpException(val reason: Reason, message: String, cause: Throwable? = null) : IOException(message, cause) {

    enum class Reason {
        /** Pas une archive ZIP, ou pas de manifest.json au format dwp. */
        NOT_DWP,

        /** Version du format plus récente que celle que ce module sait lire. */
        UNSUPPORTED_VERSION,

        /** Fichier annoncé par le manifest mais absent de l'archive. */
        MISSING_ENTRY,

        /** Archive, fichier ou image au-delà des limites (protection contre les « zip bombs »). */
        TOO_LARGE,

        /** Contenu abîmé : ZIP ou PNG invalide, somme de contrôle fausse, données tronquées. */
        CORRUPT,
    }
}

/** Garde-fous de lecture : un paquet qui les dépasse est refusé avant d'occuper la mémoire. */
data class DwpLimits(
    /** Octets lus dans le fichier source (archive compressée). */
    val maxArchiveBytes: Long = 128L * MEGABYTE,
    val maxEntries: Int = 64,
    /** Octets d'un fichier de l'archive, une fois décompressé. */
    val maxEntryBytes: Int = 48 * MEGABYTE,
    /** Octets de toute l'archive, une fois décompressée. */
    val maxTotalBytes: Long = 128L * MEGABYTE,
    /** Côté maximal de la carte de profondeur, en pixels. */
    val maxDepthSide: Int = 8192,
    /** Nombre maximal de pixels de la carte de profondeur (4 octets chacun une fois décodée). */
    val maxDepthPixels: Int = 12_000_000,
)

private const val MEGABYTE = 1 shl 20
