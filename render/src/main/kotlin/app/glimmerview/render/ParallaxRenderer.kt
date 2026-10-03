package app.glimmerview.render

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLUtils
import android.util.Half
import app.glimmerview.core.DepthMap
import app.glimmerview.core.ParallaxParams
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

/**
 * Rendu de la parallaxe en OpenGL ES 3.0, partagé par l'aperçu dans l'app et le fond d'écran.
 *
 * Toutes les méthodes s'appellent sur le thread qui possède le contexte GL, sauf
 * [setContent], [params] et [mode] qui peuvent être modifiés depuis n'importe quel thread.
 */
class ParallaxRenderer {

    @Volatile
    var params: ParallaxParams = ParallaxParams()

    /** 0 = image, 1 = visualisation de la carte de profondeur. */
    @Volatile
    var mode: Int = 0

    private var program = 0
    private var colorTexture = 0
    private var depthTexture = 0
    private var texturesReady = false
    private var viewWidth = 1
    private var viewHeight = 1

    private val lock = Any()
    private var contentColor: Bitmap? = null
    private var contentDepth: DepthMap? = null
    private var needsUpload = false

    private var uUvScale = -1
    private var uUvOffset = -1
    private var uShift = -1
    private var uFocus = -1
    private var uSteps = -1
    private var uMode = -1
    private var uColor = -1
    private var uDepth = -1

    private val scale = FloatArray(2)

    /** À appeler à chaque création de contexte GL (les textures précédentes sont perdues). */
    fun onSurfaceCreated() {
        program = GlUtil.createProgram(Shaders.VERTEX, Shaders.FRAGMENT)
        uUvScale = GLES30.glGetUniformLocation(program, "uUvScale")
        uUvOffset = GLES30.glGetUniformLocation(program, "uUvOffset")
        uShift = GLES30.glGetUniformLocation(program, "uShift")
        uFocus = GLES30.glGetUniformLocation(program, "uFocus")
        uSteps = GLES30.glGetUniformLocation(program, "uSteps")
        uMode = GLES30.glGetUniformLocation(program, "uMode")
        uColor = GLES30.glGetUniformLocation(program, "uColor")
        uDepth = GLES30.glGetUniformLocation(program, "uDepth")

        val textures = IntArray(2)
        GLES30.glGenTextures(2, textures, 0)
        colorTexture = textures[0]
        depthTexture = textures[1]
        for (texture in textures) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        }
        texturesReady = false
        synchronized(lock) { needsUpload = contentColor != null && contentDepth != null }
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        viewWidth = max(1, width)
        viewHeight = max(1, height)
        GLES30.glViewport(0, 0, viewWidth, viewHeight)
    }

    /** Remplace l'image et sa carte de profondeur (thread-safe, envoyées au GPU au prochain rendu). */
    fun setContent(color: Bitmap, depth: DepthMap) {
        synchronized(lock) {
            contentColor = color
            contentDepth = depth
            needsUpload = true
        }
    }

    /**
     * Dessine une image. [tiltX] et [tiltY] : inclinaison filtrée dans [-1, 1].
     * [pageOffset] : position de défilement des pages du launcher (0..1, 0,5 = centre).
     */
    fun draw(tiltX: Float, tiltY: Float, pageOffset: Float = 0.5f) {
        uploadIfNeeded()
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        if (!texturesReady) return

        val p = params
        val focusRange = max(p.focus, 1f - p.focus)
        val viewAspect = viewWidth.toFloat() / viewHeight
        computeUvScale(p, focusRange, viewAspect, scale)
        val sx = scale[0]
        val sy = scale[1]
        // Marge horizontale restante après la parallaxe, utilisée pour le défilement des pages
        val marginX = max(0f, 0.5f - sx * (0.5f + p.strength * focusRange))
        val offsetX = (pageOffset - 0.5f) * 2f * marginX
        val signX = if (p.invertX) -1f else 1f
        val signY = if (p.invertY) -1f else 1f

        GLES30.glUseProgram(program)
        GLES30.glUniform2f(uUvScale, sx, sy)
        GLES30.glUniform2f(uUvOffset, offsetX, 0f)
        // Intensité exprimée en fraction de la largeur de l'écran, convertie en UV sur chaque axe
        GLES30.glUniform2f(
            uShift,
            tiltX * signX * p.strength * sx,
            tiltY * signY * p.strength * viewAspect * sy,
        )
        GLES30.glUniform1f(uFocus, p.focus)
        GLES30.glUniform1i(uSteps, p.steps.coerceIn(0, 64))
        GLES30.glUniform1i(uMode, mode)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, colorTexture)
        GLES30.glUniform1i(uColor, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, depthTexture)
        GLES30.glUniform1i(uDepth, 1)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
    }

    fun release() {
        if (program != 0) GLES30.glDeleteProgram(program)
        if (colorTexture != 0) GLES30.glDeleteTextures(2, intArrayOf(colorTexture, depthTexture), 0)
        program = 0
        colorTexture = 0
        depthTexture = 0
        texturesReady = false
    }

    /**
     * Recadrage « cover » de l'image dans la vue, puis zoom minimal garantissant que
     * le décalage maximal ne fait jamais apparaître les bords de l'image.
     */
    private fun computeUvScale(p: ParallaxParams, focusRange: Float, viewAspect: Float, out: FloatArray) {
        val color = synchronized(lock) { contentColor }
        val imageAspect = if (color != null) color.width.toFloat() / color.height else viewAspect
        var sx = 1f
        var sy = 1f
        if (imageAspect > viewAspect) sx = viewAspect / imageAspect else sy = imageAspect / viewAspect
        val margin = 2f * p.strength * focusRange
        val minZoom = max(sx * (1f + margin), sy * (1f + margin * viewAspect))
        val zoom = max(max(p.zoom, minZoom), 1f)
        out[0] = sx / zoom
        out[1] = sy / zoom
    }

    private fun uploadIfNeeded() {
        val color: Bitmap
        val depth: DepthMap
        synchronized(lock) {
            if (!needsUpload) return
            color = contentColor ?: return
            depth = contentDepth ?: return
            needsUpload = false
        }
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, colorTexture)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, color, 0)

        // Disparité en demi-flottants (R16F) : filtrable linéairement en OpenGL ES 3.0
        val buffer = ByteBuffer.allocateDirect(depth.values.size * 2).order(ByteOrder.nativeOrder())
        val shorts = buffer.asShortBuffer()
        for (v in depth.values) shorts.put(Half.toHalf(v))
        buffer.position(0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, depthTexture)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R16F, depth.width, depth.height, 0,
            GLES30.GL_RED, GLES30.GL_HALF_FLOAT, buffer,
        )
        texturesReady = true
    }
}
