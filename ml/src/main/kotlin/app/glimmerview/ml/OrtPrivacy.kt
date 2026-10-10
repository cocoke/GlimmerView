package app.glimmerview.ml

import ai.onnxruntime.OrtEnvironment
import android.system.Os

/**
 * ONNX Runtime 1.30 embarque la télémétrie Microsoft (1DS, mobile.events.data.microsoft.com), qui
 * lit notamment l'identifiant Android de l'appareil. GlimmerView n'envoie rien : son lanceur
 * automatique est retiré du manifeste de l'app, et la télémétrie est coupée ici avant tout calcul,
 * par la variable d'environnement que lit la bibliothèque native puis par l'API Java.
 */
internal object OrtPrivacy {

    private const val DISABLE_TELEMETRY = "ORT_DISABLE_TELEMETRY"

    fun environment(): OrtEnvironment {
        // Avant le premier chargement de la bibliothèque native (fait par OrtEnvironment).
        runCatching { Os.setenv(DISABLE_TELEMETRY, "1", true) }
        return OrtEnvironment.getEnvironment().also { runCatching { it.setTelemetry(false) } }
    }
}
