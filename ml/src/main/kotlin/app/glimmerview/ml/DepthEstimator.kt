package app.glimmerview.ml

import android.content.Context
import android.graphics.Bitmap
import com.google.ai.edge.litert.Accelerator
import java.io.File

/** Estimation de profondeur monoculaire, indépendante du runtime utilisé. */
interface DepthEstimator : AutoCloseable {

    val inputSize: InputSize

    /** Convertit l'image, déjà redimensionnée à [inputSize], en tenseur d'entrée. */
    fun setInput(scaled: Bitmap)

    /** Exécute le modèle sur la dernière entrée. */
    fun run()

    /** Sortie brute du dernier [run] : disparité relative, `inputSize.width × inputSize.height` valeurs. */
    fun readOutput(): FloatArray

    companion object {
        fun create(
            context: Context,
            spec: ModelSpec,
            modelFile: File,
            backend: Backend,
            requestedSize: InputSize,
        ): DepthEstimator {
            require(backend.format == spec.format) { "${backend.label} ne lit pas le format ${spec.format}" }
            val size = spec.fixedInputSize ?: requestedSize
            return when (backend) {
                Backend.LITERT_GPU -> LiteRtDepthEstimator(context, modelFile, Accelerator.GPU, spec, size)
                Backend.LITERT_CPU -> LiteRtDepthEstimator(context, modelFile, Accelerator.CPU, spec, size)
                Backend.LITERT_NPU -> LiteRtDepthEstimator(context, modelFile, Accelerator.NPU, spec, size)
                Backend.ORT_CPU -> OrtDepthEstimator(modelFile, xnnpack = false, spec, size)
                Backend.ORT_XNNPACK -> OrtDepthEstimator(modelFile, xnnpack = true, spec, size)
            }
        }
    }
}

internal object InputEncoder {

    private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)

    fun encode(bitmap: Bitmap, layout: InputLayout, normalization: InputNormalization, out: FloatArray) {
        val width = bitmap.width
        val height = bitmap.height
        val count = width * height
        require(out.size == count * 3) { "Tampon d'entrée de ${out.size} valeurs pour une image ${width}×$height" }
        val pixels = IntArray(count)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val imagenet = normalization == InputNormalization.IMAGENET
        for (i in 0 until count) {
            val color = pixels[i]
            var r = ((color shr 16) and 0xFF) / 255f
            var g = ((color shr 8) and 0xFF) / 255f
            var b = (color and 0xFF) / 255f
            if (imagenet) {
                r = (r - MEAN[0]) / STD[0]
                g = (g - MEAN[1]) / STD[1]
                b = (b - MEAN[2]) / STD[2]
            }
            when (layout) {
                InputLayout.NHWC -> {
                    out[i * 3] = r
                    out[i * 3 + 1] = g
                    out[i * 3 + 2] = b
                }
                InputLayout.NCHW -> {
                    out[i] = r
                    out[count + i] = g
                    out[2 * count + i] = b
                }
            }
        }
    }
}
