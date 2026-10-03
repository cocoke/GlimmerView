package app.glimmerview.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OneEuroFilterTest {

    private val dt = 1f / 50f // capteur à 50 Hz

    @Test
    fun `converges to a constant input`() {
        val filter = OneEuroFilter()
        filter.filter(0f, dt)
        var out = 0f
        repeat(200) { out = filter.filter(1f, dt) }
        assertEquals(1f, out, 1e-3f)
    }

    @Test
    fun `reduces jitter around a still position`() {
        val filter = OneEuroFilter()
        val noise = floatArrayOf(0.02f, -0.02f)
        var maxDeviation = 0f
        repeat(500) { i ->
            val out = filter.filter(0.5f + noise[i % 2], dt)
            if (i > 50) maxDeviation = maxOf(maxDeviation, abs(out - 0.5f))
        }
        assertTrue("écart résiduel $maxDeviation", maxDeviation < 0.01f)
    }
}
