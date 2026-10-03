package app.glimmerview.render

import kotlin.math.PI
import kotlin.math.abs

/**
 * Filtre « One Euro » (Casiez et al., 2012) : lisse fortement quand le signal bouge peu
 * (pas de tremblement à l'arrêt) et suit vite quand il bouge beaucoup (peu de latence).
 */
class OneEuroFilter(
    var minCutoff: Float = 1.0f,
    var beta: Float = 0.6f,
    var derivativeCutoff: Float = 1.0f,
) {
    private var previous = 0f
    private var previousDerivative = 0f
    private var initialized = false

    fun reset() {
        initialized = false
    }

    /** [dt] en secondes depuis l'échantillon précédent. */
    fun filter(value: Float, dt: Float): Float {
        if (!initialized || dt <= 0f) {
            initialized = true
            previous = value
            previousDerivative = 0f
            return value
        }
        val derivative = (value - previous) / dt
        val smoothedDerivative = lowPass(previousDerivative, derivative, alpha(derivativeCutoff, dt))
        previousDerivative = smoothedDerivative
        val cutoff = minCutoff + beta * abs(smoothedDerivative)
        val result = lowPass(previous, value, alpha(cutoff, dt))
        previous = result
        return result
    }

    private fun alpha(cutoff: Float, dt: Float): Float {
        val tau = 1f / (2f * PI.toFloat() * cutoff)
        return 1f / (1f + tau / dt)
    }

    private fun lowPass(previous: Float, value: Float, alpha: Float) = previous + alpha * (value - previous)
}
