package app.glimmerview.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import java.io.File
import java.nio.FloatBuffer

/** Inférence avec ONNX Runtime sur CPU (noyaux MLAS par défaut, ou fournisseur XNNPACK). */
internal class OrtDepthEstimator(
    modelFile: File,
    xnnpack: Boolean,
    private val spec: ModelSpec,
    override val inputSize: InputSize,
) : DepthEstimator {

    private val environment = OrtPrivacy.environment()

    private val options = OrtSession.SessionOptions().apply {
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        setIntraOpNumThreads(CPU_THREADS)
        if (xnnpack) {
            // Recommandation ONNX Runtime : XNNPACK gère son propre pool de threads.
            setIntraOpNumThreads(1)
            addConfigEntry("session.intra_op.allow_spinning", "0")
            addXnnpack(mapOf("intra_op_num_threads" to CPU_THREADS.toString()))
        }
    }

    private val session: OrtSession = try {
        environment.createSession(modelFile.absolutePath, options)
    } catch (t: Throwable) {
        options.close()
        throw t
    }

    private val inputName = session.inputNames.first()
    private val input = FloatArray(3 * inputSize.width * inputSize.height)
    private val shape = longArrayOf(1, 3, inputSize.height.toLong(), inputSize.width.toLong())
    private var result: OrtSession.Result? = null

    override fun setInput(scaled: Bitmap) {
        InputEncoder.encode(scaled, spec.layout, spec.normalization, input)
    }

    override fun run() {
        result?.close()
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(input), shape).use { tensor ->
            result = session.run(mapOf(inputName to tensor))
        }
    }

    override fun readOutput(): FloatArray {
        val output = checkNotNull(result) { "run() n'a pas été appelé" }[0] as OnnxTensor
        val buffer = output.floatBuffer
        return FloatArray(buffer.remaining()).also { buffer.get(it) }
    }

    override fun close() {
        result?.close()
        session.close()
        options.close()
    }

    private companion object {
        const val CPU_THREADS = 4
    }
}
