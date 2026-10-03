package app.glimmerview.lab

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import app.glimmerview.ml.BenchResult
import org.json.JSONObject
import java.util.Locale

internal object DeviceInfo {

    fun describe(context: Context): String {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val soc = if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else Build.HARDWARE
        return "${Build.MANUFACTURER} ${Build.MODEL} · SoC $soc · Android ${Build.VERSION.RELEASE} " +
            "(API ${Build.VERSION.SDK_INT}) · RAM ${"%.1f".format(Locale.FRANCE, memory.totalMem / 1e9)} Go · " +
            "ABI ${Build.SUPPORTED_ABIS.firstOrNull() ?: "?"}"
    }

    fun thermalStatus(context: Context): String {
        if (Build.VERSION.SDK_INT < 29) return "n/d"
        val status = context.getSystemService(PowerManager::class.java).currentThermalStatus
        return when (status) {
            PowerManager.THERMAL_STATUS_NONE -> "normal"
            PowerManager.THERMAL_STATUS_LIGHT -> "léger"
            PowerManager.THERMAL_STATUS_MODERATE -> "modéré"
            PowerManager.THERMAL_STATUS_SEVERE -> "sévère"
            else -> "critique ($status)"
        }
    }
}

/** Rapport texte (Markdown) à copier ou partager après les mesures. */
internal object LabReport {

    fun build(
        device: String,
        gpu: String,
        thermal: String,
        image: String?,
        runs: Int,
        results: List<BenchResult>,
        postProcess: String?,
        render: Map<Int, String>,
        wallpaper: String?,
    ): String = buildString {
        appendLine("## GlimmerView Lab · rapport Phase 0")
        appendLine("- Appareil : $device")
        appendLine("- GPU : $gpu")
        appendLine("- État thermique : $thermal")
        appendLine("- Image : ${image ?: "aucune"}")
        appendLine()
        appendLine("| Configuration | Init | 1re inférence | Moyenne ($runs) | Min | Max | Prétraitement | PSS |")
        appendLine("|---|---|---|---|---|---|---|---|")
        for (r in results) {
            if (r.ok) {
                appendLine(
                    "| ${r.config.label} | ${ms(r.initMs)} | ${ms(r.firstRunMs)} | ${ms(r.averageMs)} | " +
                        "${ms(r.minMs)} | ${ms(r.maxMs)} | ${ms(r.preprocessMs)} | ${"%.0f".format(r.pssMb)} Mo |",
                )
            } else {
                appendLine("| ${r.config.label} | échec : ${r.error} | | | | | | |")
            }
        }
        if (postProcess != null) {
            appendLine()
            appendLine("- Post-traitement (normalisation, redimensionnement, contours) : $postProcess")
        }
        if (render.isNotEmpty()) {
            appendLine("- Rendu de l'aperçu (avec glFinish) : " +
                render.toSortedMap().entries.joinToString(" · ") { (steps, value) -> "$steps pas → $value" })
        }
        if (wallpaper != null) appendLine("- Fond d'écran : $wallpaper")
    }

    fun formatWallpaperStats(json: String): String = runCatching {
        val o = JSONObject(json)
        val frames = o.optInt("frames")
        val seconds = o.optDouble("visibleSeconds")
        "$frames images dessinées en ${"%.0f".format(seconds)} s visibles " +
            "(${"%.1f".format(o.optDouble("framesPerVisibleSecond"))} i/s en moyenne) · " +
            "${"%.2f".format(o.optDouble("averageDrawMs"))} ms par image (CPU) · " +
            "capteur ${o.optString("sensor")} · GPU ${o.optString("gpu")}"
    }.getOrElse { "statistiques illisibles" }

    fun ms(value: Double): String = if (value >= 100) "%.0f ms".format(value) else "%.1f ms".format(value)
}
