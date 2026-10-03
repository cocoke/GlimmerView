package app.glimmerview.render

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.view.Display
import android.view.Surface
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Inclinaison du téléphone, filtrée et normalisée dans [-1, 1] sur chaque axe de l'écran.
 *
 * L'inclinaison est mesurée par rapport à une orientation de référence qui suit lentement
 * l'orientation courante : l'image se recentre d'elle-même quand on change de position.
 * Capteurs, par ordre de préférence : rotation « jeu » (gyroscope + accéléromètre, sans
 * boussole), rotation complète, puis accéléromètre seul (téléphones sans gyroscope).
 */
class TiltSensor(
    context: Context,
    private val handler: Handler? = null,
    private val listener: (x: Float, y: Float) -> Unit,
) : SensorEventListener {

    enum class Source { GAME_ROTATION_VECTOR, ROTATION_VECTOR, ACCELEROMETER, NONE }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    val source: Source
    private val sensor: Sensor?

    /** Angle (radians) correspondant à une inclinaison « pleine ». */
    var maxAngle = 0.30f

    /** Constante de temps du recentrage automatique, en secondes. */
    var recenterSeconds = 3f

    private val quaternion = FloatArray(4)
    private val reference = FloatArray(4)
    private val relative = FloatArray(4)
    private val gravity = FloatArray(3)
    private val gravityReference = FloatArray(3)
    private var hasReference = false
    private var lastTimestamp = 0L
    private var rawX = 0f
    private var rawY = 0f
    private val filterX = OneEuroFilter()
    private val filterY = OneEuroFilter()

    init {
        val game = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        val rotation = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        when {
            game != null -> { sensor = game; source = Source.GAME_ROTATION_VECTOR }
            rotation != null -> { sensor = rotation; source = Source.ROTATION_VECTOR }
            accelerometer != null -> { sensor = accelerometer; source = Source.ACCELEROMETER }
            else -> { sensor = null; source = Source.NONE }
        }
        if (source == Source.ACCELEROMETER) {
            // Signal plus bruité : on lisse davantage
            filterX.minCutoff = 0.5f
            filterY.minCutoff = 0.5f
        }
    }

    fun start() {
        val s = sensor ?: return
        hasReference = false
        lastTimestamp = 0L
        filterX.reset()
        filterY.reset()
        sensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME, handler)
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        val dt = if (lastTimestamp == 0L) 0f else ((event.timestamp - lastTimestamp) / 1e9f).coerceIn(0f, 0.1f)
        lastTimestamp = event.timestamp
        val follow = if (dt > 0f) 1f - exp(-dt / recenterSeconds) else 0f
        if (source == Source.ACCELEROMETER) fromGravity(event.values, follow) else fromRotationVector(event.values, follow)

        // Axes de l'appareil → axes de l'écran selon l'orientation de l'affichage
        val (screenX, screenY) = when (displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.rotation) {
            Surface.ROTATION_90 -> -rawY to rawX
            Surface.ROTATION_180 -> -rawX to -rawY
            Surface.ROTATION_270 -> rawY to -rawX
            else -> rawX to rawY
        }
        val x = filterX.filter(tanh(screenX / maxAngle), dt)
        val y = filterY.filter(tanh(screenY / maxAngle), dt)
        listener(x, y)
    }

    private fun fromRotationVector(values: FloatArray, follow: Float) {
        SensorManager.getQuaternionFromVector(quaternion, values) // [w, x, y, z]
        if (!hasReference) {
            quaternion.copyInto(reference)
            hasReference = true
        } else {
            val sign = if (dot(reference, quaternion) < 0f) -1f else 1f
            for (i in 0..3) reference[i] += (sign * quaternion[i] - reference[i]) * follow
            normalize(reference)
        }
        // Rotation relative exprimée dans le repère de l'appareil : conj(référence) ⊗ courante
        multiplyConjugate(reference, quaternion, relative)
        if (relative[0] < 0f) for (i in 0..3) relative[i] = -relative[i]
        rawX = 2f * relative[2] // autour de l'axe Y de l'appareil → décalage horizontal
        rawY = 2f * relative[1] // autour de l'axe X de l'appareil → décalage vertical
    }

    private fun fromGravity(values: FloatArray, follow: Float) {
        val norm = sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2])
        if (norm < 1e-3f) return
        for (i in 0..2) gravity[i] = values[i] / norm
        if (!hasReference) {
            gravity.copyInto(gravityReference)
            hasReference = true
        } else {
            for (i in 0..2) gravityReference[i] += (gravity[i] - gravityReference[i]) * follow
            val n = sqrt(gravityReference.sumOf { (it * it).toDouble() }).toFloat()
            for (i in 0..2) gravityReference[i] /= n
        }
        // Petites rotations : variation de la gravité dans le repère de l'appareil
        rawX = gravity[0] - gravityReference[0]
        rawY = gravity[2] - gravityReference[2]
    }

    private fun dot(a: FloatArray, b: FloatArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3]

    private fun normalize(q: FloatArray) {
        val n = sqrt(dot(q, q))
        if (n > 0f) for (i in 0..3) q[i] /= n
    }

    /** out = conj(a) ⊗ b (quaternions [w, x, y, z]). */
    private fun multiplyConjugate(a: FloatArray, b: FloatArray, out: FloatArray) {
        val aw = a[0]; val ax = -a[1]; val ay = -a[2]; val az = -a[3]
        val bw = b[0]; val bx = b[1]; val by = b[2]; val bz = b[3]
        out[0] = aw * bw - ax * bx - ay * by - az * bz
        out[1] = aw * bx + ax * bw + ay * bz - az * by
        out[2] = aw * by - ax * bz + ay * bw + az * bx
        out[3] = aw * bz + ax * by - ay * bx + az * bw
    }
}
