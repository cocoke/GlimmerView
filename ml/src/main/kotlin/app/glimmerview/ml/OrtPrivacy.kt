package app.glimmerview.ml

import ai.onnxruntime.OrtEnvironment
import android.system.Os

/**
 * Le paquet officiel d'ONNX Runtime embarque la télémétrie Microsoft (1DS, mobile.events.data.microsoft.com),
 * qui lit notamment l'identifiant Android de l'appareil. L'ONNX Runtime de GlimmerView est compilé sans
 * elle (tools/onnxruntime) ; ces garde-fous restent au cas où l'on reviendrait au paquet officiel :
 * variable d'environnement lue par la bibliothèque native, puis API Java (le lanceur automatique est,
 * lui, retiré du manifeste de l'app).
 */
internal object OrtPrivacy {

    private const val DISABLE_TELEMETRY = "ORT_DISABLE_TELEMETRY"

    fun environment(): OrtEnvironment {
        // Avant le premier chargement de la bibliothèque native (fait par OrtEnvironment).
        runCatching { Os.setenv(DISABLE_TELEMETRY, "1", true) }
        return OrtEnvironment.getEnvironment().also { runCatching { it.setTelemetry(false) } }
    }
}
