package app.glimmerview.render

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.view.Surface

/**
 * Contexte OpenGL ES 3 lié à une [Surface] (celle du fond d'écran).
 * Doit être créé, utilisé et libéré sur un même thread.
 */
class EglWindow(surface: Surface) {

    private val display: EGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    private val context: EGLContext
    private val eglSurface: EGLSurface

    init {
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize a échoué" }
        val config = chooseConfig(display, EGL14.EGL_WINDOW_BIT)
        context = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0,
        )
        check(context != EGL14.EGL_NO_CONTEXT) { "Contexte OpenGL ES 3 indisponible" }
        eglSurface = EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
        check(eglSurface != EGL14.EGL_NO_SURFACE) { "eglCreateWindowSurface a échoué" }
        check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) { "eglMakeCurrent a échoué" }
    }

    fun swapBuffers(): Boolean = EGL14.eglSwapBuffers(display, eglSurface)

    fun release() {
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display, eglSurface)
        EGL14.eglDestroyContext(display, context)
        // Pas d'eglTerminate : d'autres moteurs du même process peuvent utiliser l'affichage.
        EGL14.eglReleaseThread()
    }

    internal companion object {
        fun chooseConfig(display: EGLDisplay, surfaceType: Int): EGLConfig {
            val attributes = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
                EGL14.EGL_SURFACE_TYPE, surfaceType,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            check(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0) {
                "Aucune configuration EGL compatible OpenGL ES 3"
            }
            return checkNotNull(configs[0])
        }
    }
}

/** Informations sur le GPU, obtenues via un petit contexte hors écran. */
object GlInfo {

    /** Renvoie « fabricant · GPU · version GL », ou un message d'erreur. Bloquant. */
    fun describe(): String = runCatching {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(display, version, 0, version, 1)
        val config = EglWindow.chooseConfig(display, EGL14.EGL_PBUFFER_BIT)
        val context = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0,
        )
        val surface = EGL14.eglCreatePbufferSurface(
            display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0,
        )
        EGL14.eglMakeCurrent(display, surface, surface, context)
        val description = listOf(
            GLES30.glGetString(GLES30.GL_VENDOR),
            GLES30.glGetString(GLES30.GL_RENDERER),
            GLES30.glGetString(GLES30.GL_VERSION),
        ).joinToString(" · ")
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display, surface)
        EGL14.eglDestroyContext(display, context)
        EGL14.eglReleaseThread()
        description
    }.getOrElse { "GPU inconnu (${it.message})" }
}
