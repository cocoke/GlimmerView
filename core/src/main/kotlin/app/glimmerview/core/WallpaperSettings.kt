package app.glimmerview.core

import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToInt

/** Préférences globales, lues par le process du fond d'écran à chaque fois qu'il redevient visible. */
data class WallpaperSettings(
    /** Ouverture appliquée aux nouvelles œuvres (voir [ApertureScale]). */
    val apertureLevel: Int = ApertureScale.DEFAULT_LEVEL,
    /** L'œuvre se recale doucement quand on change de position. */
    val recenter: Boolean = true,
    /** Image fixe, sans capteur (accessibilité). */
    val reduceMotion: Boolean = false,
    /** Fréquence maximale de rendu, en images par seconde (30 ou 60). */
    val frameRate: Int = 60,
    /** Image fixe quand l'économiseur de batterie est actif. */
    val pauseInBatterySaver: Boolean = true,
) {

    fun toJson(): String = JSONObject()
        .put("apertureLevel", apertureLevel)
        .put("recenter", recenter)
        .put("reduceMotion", reduceMotion)
        .put("frameRate", frameRate)
        .put("pauseInBatterySaver", pauseInBatterySaver)
        .toString()

    companion object {
        fun fromJson(json: String): WallpaperSettings {
            val o = JSONObject(json)
            val d = WallpaperSettings()
            return WallpaperSettings(
                apertureLevel = o.optInt("apertureLevel", d.apertureLevel).coerceIn(0, ApertureScale.LEVELS - 1),
                recenter = o.optBoolean("recenter", d.recenter),
                reduceMotion = o.optBoolean("reduceMotion", d.reduceMotion),
                frameRate = o.optInt("frameRate", d.frameRate).coerceIn(15, 120),
                pauseInBatterySaver = o.optBoolean("pauseInBatterySaver", d.pauseInBatterySaver),
            )
        }
    }
}

/** Le « diaphragme » : l'intensité de l'effet, de f/16 (très discret) à f/4 (intense). */
object ApertureScale {
    /** Nombres d'ouverture affichés, du plus fermé au plus ouvert. */
    val fNumbers = doubleArrayOf(16.0, 11.0, 8.0, 5.6, 4.0)
    private val strengths = floatArrayOf(0.012f, 0.022f, 0.035f, 0.05f, 0.07f)
    val LEVELS = strengths.size
    const val DEFAULT_LEVEL = 2

    fun strength(level: Int): Float = strengths[level.coerceIn(0, LEVELS - 1)]

    fun level(strength: Float): Int = strengths.indices.minBy { abs(strengths[it] - strength) }
}

/** La bague de mise au point : le plan immobile, de ∞ (le fond) à 1 m (le premier plan). */
object FocusScale {
    const val MARKS = 7

    fun focus(index: Int): Float = index.coerceIn(0, MARKS - 1) / (MARKS - 1f)

    fun index(focus: Float): Int = (focus.coerceIn(0f, 1f) * (MARKS - 1)).roundToInt()
}
