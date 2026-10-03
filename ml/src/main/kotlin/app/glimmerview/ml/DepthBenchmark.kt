package app.glimmerview.ml

import android.graphics.Bitmap
import android.os.Debug

/** Une combinaison modèle × runtime × taille d'entrée à mesurer. */
data class BenchConfig(val model: ModelSpec, val backend: Backend, val inputSize: InputSize) {
    val key: String get() = "${model.id}|${backend.name}|$inputSize"
    val label: String get() = "${backend.label} · ${model.label.substringAfter("· ")} · $inputSize"
}

data class BenchResult(
    val config: BenchConfig,
    val error: String? = null,
    /** Chargement et compilation du modèle. */
    val initMs: Double = 0.0,
    /** Redimensionnement + conversion de l'image en tenseur. */
    val preprocessMs: Double = 0.0,
    /** Première inférence (inclut l'échauffement : compilation des noyaux GPU, caches…). */
    val firstRunMs: Double = 0.0,
    /** Inférences suivantes. */
    val runsMs: List<Double> = emptyList(),
    val readMs: Double = 0.0,
    /** Mémoire du process (PSS) juste après les inférences, en Mo. */
    val pssMb: Double = 0.0,
    val output: RawDepth? = null,
) {
    val ok: Boolean get() = error == null
    val averageMs: Double get() = if (runsMs.isEmpty()) firstRunMs else runsMs.average()
    val minMs: Double get() = runsMs.minOrNull() ?: firstRunMs
    val maxMs: Double get() = runsMs.maxOrNull() ?: firstRunMs
}

object DepthBenchmark {

    /** Mesure une configuration. Bloquant : à exécuter sur un thread dédié. */
    fun run(store: ModelStore, config: BenchConfig, image: Bitmap, runs: Int): BenchResult {
        if (!store.isDownloaded(config.model)) return BenchResult(config, error = "Modèle non téléchargé")
        var estimator: DepthEstimator? = null
        return try {
            val t0 = System.nanoTime()
            val e = DepthEstimator.create(config.model, store.file(config.model), config.backend, config.inputSize)
            estimator = e
            val initMs = elapsedMs(t0)

            val t1 = System.nanoTime()
            val scaled = Bitmap.createScaledBitmap(image, e.inputSize.width, e.inputSize.height, true)
            e.setInput(scaled)
            val preprocessMs = elapsedMs(t1)

            // Chaque mesure inclut la lecture de la sortie : sur GPU, run() est asynchrone et
            // rend la main avant la fin du calcul ; seule la lecture attend le résultat.
            val t2 = System.nanoTime()
            e.run()
            e.readOutput()
            val firstRunMs = elapsedMs(t2)

            val runsMs = List(runs) {
                val t = System.nanoTime()
                e.run()
                e.readOutput()
                elapsedMs(t)
            }

            val t3 = System.nanoTime()
            val output = e.readOutput()
            val readMs = elapsedMs(t3)
            check(output.size == e.inputSize.width * e.inputSize.height) {
                "Sortie inattendue : ${output.size} valeurs pour ${e.inputSize}"
            }
            val memory = Debug.MemoryInfo().also(Debug::getMemoryInfo)
            BenchResult(
                config = config,
                initMs = initMs,
                preprocessMs = preprocessMs,
                firstRunMs = firstRunMs,
                runsMs = runsMs,
                readMs = readMs,
                pssMb = memory.totalPss / 1024.0,
                output = RawDepth(e.inputSize.width, e.inputSize.height, output),
            )
        } catch (t: Throwable) {
            BenchResult(config, error = "${t.javaClass.simpleName} : ${t.message}")
        } finally {
            runCatching { estimator?.close() }
        }
    }

    private fun elapsedMs(start: Long) = (System.nanoTime() - start) / 1e6
}
