package app.glimmerview.core

import android.graphics.Bitmap
import android.os.Build
import java.io.File
import java.io.OutputStream

/** Écrit dans un fichier temporaire puis renomme : un lecteur ne voit jamais de fichier à moitié écrit. */
internal fun writeAtomically(target: File, block: (OutputStream) -> Unit) {
    val tmp = File(target.parentFile, target.name + ".tmp")
    tmp.outputStream().buffered().use(block)
    if (!tmp.renameTo(target)) {
        target.delete()
        check(tmp.renameTo(target)) { "Renommage impossible : $target" }
    }
}

@Suppress("DEPRECATION")
internal fun webpFormat(): Bitmap.CompressFormat =
    if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
