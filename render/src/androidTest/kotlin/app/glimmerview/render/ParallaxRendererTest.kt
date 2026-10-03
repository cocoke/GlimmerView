package app.glimmerview.render

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.glimmerview.core.DepthMap
import app.glimmerview.core.ParallaxParams
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Vérifie la géométrie de la parallaxe par un rendu hors écran : l'image est un dégradé
 * horizontal (rouge = abscisse), donc un décalage horizontal se lit directement dans le rouge.
 */
@RunWith(AndroidJUnit4::class)
class ParallaxRendererTest {

    private lateinit var display: EGLDisplay
    private lateinit var context: EGLContext
    private lateinit var surface: EGLSurface
    private val renderer = ParallaxRenderer()

    @Before
    fun setUp() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(display, version, 0, version, 1)
        val config = EglWindow.chooseConfig(display, EGL14.EGL_PBUFFER_BIT)
        context = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0,
        )
        surface = EGL14.eglCreatePbufferSurface(
            display, config, intArrayOf(EGL14.EGL_WIDTH, SIZE, EGL14.EGL_HEIGHT, SIZE, EGL14.EGL_NONE), 0,
        )
        EGL14.eglMakeCurrent(display, surface, surface, context)
        renderer.onSurfaceCreated()
        renderer.onSurfaceChanged(SIZE, SIZE)
    }

    @After
    fun tearDown() {
        renderer.release()
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display, surface)
        EGL14.eglDestroyContext(display, context)
        EGL14.eglReleaseThread()
    }

    @Test
    fun focusPlaneDoesNotMove() {
        assertEquals(0f, horizontalShift(depth = 0.5f, steps = 0), 1.5f)
        assertEquals(0f, horizontalShift(depth = 0.5f, steps = 16), 1.5f)
    }

    @Test
    fun nearAndFarMoveInOppositeDirections() {
        val near = horizontalShift(depth = 1f, steps = 0)
        val far = horizontalShift(depth = 0f, steps = 0)
        assertTrue("premier plan : $near", near < -4f)
        assertTrue("arrière-plan : $far", far > 4f)
        assertEquals(-near, far, 1.5f)
    }

    @Test
    fun rayMarchingMatchesSimpleShiftOnFlatDepth() {
        val simple = horizontalShift(depth = 1f, steps = 0)
        val marched = horizontalShift(depth = 1f, steps = 16)
        assertEquals(simple, marched, 2f)
    }

    /** Variation du rouge au centre (en niveaux 0..255) entre inclinaison nulle et inclinaison maximale. */
    private fun horizontalShift(depth: Float, steps: Int): Float {
        val gradient = Bitmap.createBitmap(
            IntArray(SIZE * SIZE) { 0xFF000000.toInt() or (((it % SIZE) * 4) shl 16) },
            SIZE, SIZE, Bitmap.Config.ARGB_8888,
        )
        renderer.setContent(gradient, DepthMap(SIZE, SIZE, FloatArray(SIZE * SIZE) { depth }))
        renderer.params = ParallaxParams(strength = 0.08f, focus = 0.5f, zoom = 1f, steps = steps)
        val still = redAtCenter(tiltX = 0f)
        val tilted = redAtCenter(tiltX = 1f)
        return tilted - still
    }

    private fun redAtCenter(tiltX: Float): Float {
        renderer.draw(tiltX, 0f)
        GLES30.glFinish()
        val pixel = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        GLES30.glReadPixels(SIZE / 2, SIZE / 2, 1, 1, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixel)
        return (pixel.get(0).toInt() and 0xFF).toFloat()
    }

    private companion object {
        const val SIZE = 64
    }
}
