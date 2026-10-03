package app.glimmerview.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import kotlin.math.max
import kotlin.math.roundToInt

object ImageLoading {

    /** Décode une image en ARGB_8888 logiciel, réduite pour que son grand côté ≤ [maxLongSide]. */
    fun decode(context: Context, uri: Uri, maxLongSide: Int): Bitmap {
        val bitmap = if (Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longSide = max(info.size.width, info.size.height)
                if (longSide > maxLongSide) {
                    val scale = maxLongSide.toFloat() / longSide
                    decoder.setTargetSize(
                        (info.size.width * scale).roundToInt().coerceAtLeast(1),
                        (info.size.height * scale).roundToInt().coerceAtLeast(1),
                    )
                }
            }
        } else {
            decodeLegacy(context, uri, maxLongSide)
        }
        return if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
    }

    /** Android 8.x : BitmapFactory + rotation EXIF manuelle. */
    private fun decodeLegacy(context: Context, uri: Uri, maxLongSide: Int): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= maxLongSide) sampleSize *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bitmap = checkNotNull(resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }) {
            "Image illisible"
        }
        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val longSide = max(bitmap.width, bitmap.height)
        val scale = if (longSide > maxLongSide) maxLongSide.toFloat() / longSide else 1f
        if (degrees != 0f || scale < 1f) {
            val matrix = Matrix().apply {
                postScale(scale, scale)
                postRotate(degrees)
            }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
        return bitmap
    }
}
