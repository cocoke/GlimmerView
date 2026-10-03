package app.glimmerview.core

import org.json.JSONObject

/** Réglages de l'effet de parallaxe, partagés par l'aperçu dans l'app et le fond d'écran. */
data class ParallaxParams(
    /** Décalage maximal, en fraction de la largeur de l'écran. */
    val strength: Float = 0.035f,
    /** Disparité du plan qui reste immobile (0 = fond, 1 = premier plan). */
    val focus: Float = 0.5f,
    /** Zoom demandé ; le moteur l'augmente si besoin pour ne jamais montrer les bords. */
    val zoom: Float = 1.0f,
    /** Nombre de pas du ray-marching ; 0 = simple décalage (palier « bas »). */
    val steps: Int = 16,
    val invertX: Boolean = false,
    val invertY: Boolean = false,
) {

    fun toJson(): String = JSONObject()
        .put("strength", strength.toDouble())
        .put("focus", focus.toDouble())
        .put("zoom", zoom.toDouble())
        .put("steps", steps)
        .put("invertX", invertX)
        .put("invertY", invertY)
        .toString()

    companion object {
        fun fromJson(json: String): ParallaxParams {
            val o = JSONObject(json)
            val d = ParallaxParams()
            return ParallaxParams(
                strength = o.optDouble("strength", d.strength.toDouble()).toFloat(),
                focus = o.optDouble("focus", d.focus.toDouble()).toFloat(),
                zoom = o.optDouble("zoom", d.zoom.toDouble()).toFloat(),
                steps = o.optInt("steps", d.steps),
                invertX = o.optBoolean("invertX", d.invertX),
                invertY = o.optBoolean("invertY", d.invertY),
            )
        }
    }
}
