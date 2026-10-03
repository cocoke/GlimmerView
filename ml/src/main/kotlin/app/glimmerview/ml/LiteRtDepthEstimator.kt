package app.glimmerview.ml

import android.content.Context
import android.graphics.Bitmap
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
import com.google.ai.edge.litert.TensorBuffer
import java.io.File

/** Inférence avec LiteRT (API CompiledModel) sur CPU, GPU ou NPU. */
internal class LiteRtDepthEstimator(
    context: Context,
    modelFile: File,
    accelerator: Accelerator,
    private val spec: ModelSpec,
    override val inputSize: InputSize,
) : DepthEstimator {

    // L'environnement a besoin du contexte pour charger les bibliothèques NPU des constructeurs.
    private val environment: Environment =
        if (accelerator == Accelerator.NPU) Environment.create(context) else Environment.create()

    private val model: CompiledModel = try {
        val options = CompiledModel.Options(accelerator).apply {
            // Même parallélisme que les autres chemins CPU (4 cœurs « performance »)
            cpuOptions = CompiledModel.CpuOptions(numThreads = CPU_THREADS)
        }
        CompiledModel.create(modelFile.absolutePath, options, environment)
    } catch (t: Throwable) {
        environment.close()
        throw t
    }

    private val inputs: List<TensorBuffer> = model.createInputBuffers()
    private val outputs: List<TensorBuffer> = model.createOutputBuffers()
    private val input = FloatArray(inputSize.width * inputSize.height * 3)

    override fun setInput(scaled: Bitmap) {
        InputEncoder.encode(scaled, spec.layout, spec.normalization, input)
        inputs[0].writeFloat(input)
    }

    override fun run() {
        model.run(inputs, outputs)
    }

    override fun readOutput(): FloatArray = outputs[0].readFloat()

    override fun close() {
        inputs.forEach { it.close() }
        outputs.forEach { it.close() }
        model.close()
        environment.close()
    }

    private companion object {
        const val CPU_THREADS = 4
    }
}
