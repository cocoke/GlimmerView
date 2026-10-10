package app.glimmerview.render

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import app.glimmerview.core.DepthMap
import app.glimmerview.core.ParallaxParams
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Statistiques de rendu de l'aperçu, publiées environ deux fois par seconde. */
data class PreviewStats(
    /** Durée moyenne d'une image (CPU + GPU si [ParallaxGlView.measureGpu]), en ms. */
    val frameMs: Float,
    val fps: Float,
    val width: Int,
    val height: Int,
)

/** Aperçu interactif de la parallaxe dans l'app (même moteur que le fond d'écran). */
class ParallaxGlView(context: Context) : GLSurfaceView(context) {

    val renderer = ParallaxRenderer()
    val tiltSensor = TiltSensor(context) { x, y ->
        tiltX = x
        tiltY = y
        requestRender()
    }

    /** Attend la fin du travail GPU à chaque image (glFinish) pour mesurer son coût réel. */
    @Volatile
    var measureGpu = true

    @Volatile
    var glRenderer: String = ""
        private set

    /** Appelé sur le thread principal. */
    var onStats: ((PreviewStats) -> Unit)? = null

    /** Appelé sur le thread principal quand l'image transmise par [setContent] a été dessinée une première fois. */
    var onContentShown: ((Any) -> Unit)? = null

    @Volatile
    private var pendingShown: Any? = null

    @Volatile
    private var tiltX = 0f

    @Volatile
    private var tiltY = 0f

    private var frames = 0
    private var frameNanos = 0L
    private var windowStart = 0L
    private var surfaceWidth = 0
    private var surfaceHeight = 0

    init {
        setEGLContextClientVersion(3)
        preserveEGLContextOnPause = true
        setRenderer(object : Renderer {
            override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
                renderer.onSurfaceCreated()
                glRenderer = GLES30.glGetString(GLES30.GL_RENDERER) ?: ""
            }

            override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
                surfaceWidth = width
                surfaceHeight = height
                renderer.onSurfaceChanged(width, height)
            }

            override fun onDrawFrame(gl: GL10?) {
                val start = System.nanoTime()
                renderer.draw(tiltX, tiltY)
                if (measureGpu) GLES30.glFinish()
                val shown = pendingShown
                if (shown != null && renderer.showsLatestContent) {
                    pendingShown = null
                    post { onContentShown?.invoke(shown) }
                }
                recordFrame(start, System.nanoTime())
            }
        })
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    /** [token] est rendu à [onContentShown] quand l'image apparaît à l'écran. */
    fun setContent(color: Bitmap, depth: DepthMap, token: Any = color) {
        renderer.setContent(color, depth)
        pendingShown = token
        requestRender()
    }

    fun setParams(params: ParallaxParams) {
        renderer.params = params
        requestRender()
    }

    fun setDepthView(showDepth: Boolean) {
        renderer.mode = if (showDepth) 1 else 0
        requestRender()
    }

    /** Rendu en continu (pour mesurer le coût à fréquence maximale) ou seulement si ça bouge. */
    fun setContinuous(continuous: Boolean) {
        renderMode = if (continuous) RENDERMODE_CONTINUOUSLY else RENDERMODE_WHEN_DIRTY
    }

    fun start() {
        onResume()
        tiltSensor.start()
    }

    fun stop() {
        tiltSensor.stop()
        onPause()
    }

    private fun recordFrame(start: Long, end: Long) {
        if (windowStart == 0L) windowStart = start
        frames++
        frameNanos += end - start
        val elapsed = end - windowStart
        if (elapsed >= 500_000_000L) {
            val stats = PreviewStats(
                frameMs = frameNanos / 1e6f / frames,
                fps = frames * 1e9f / elapsed,
                width = surfaceWidth,
                height = surfaceHeight,
            )
            frames = 0
            frameNanos = 0L
            windowStart = 0L
            post { onStats?.invoke(stats) }
        }
    }
}
