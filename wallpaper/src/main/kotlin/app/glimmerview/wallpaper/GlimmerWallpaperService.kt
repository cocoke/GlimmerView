package app.glimmerview.wallpaper

import android.app.WallpaperColors
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.opengl.GLES30
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.SurfaceHolder
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import app.glimmerview.core.WallpaperSettings
import app.glimmerview.core.WallpaperStore
import app.glimmerview.render.EglWindow
import app.glimmerview.render.ParallaxRenderer
import app.glimmerview.render.TiltSensor
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Fond d'écran animé GlimmerView.
 *
 * Rendu à la demande : une image n'est dessinée que lorsque l'inclinaison filtrée change
 * de façon perceptible. Téléphone immobile = aucune image dessinée.
 */
class GlimmerWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = GlimmerEngine()

    private inner class GlimmerEngine : Engine(), Choreographer.FrameCallback {

        private val renderThread = HandlerThread("GlimmerRender").apply { start() }
        private val renderHandler = Handler(renderThread.looper)
        private val mainHandler = Handler(Looper.getMainLooper())
        private val store = WallpaperStore(this@GlimmerWallpaperService)
        private val renderer = ParallaxRenderer()
        private val tiltSensor = TiltSensor(this@GlimmerWallpaperService, renderHandler, ::onTilt)

        // Champs ci-dessous : accédés uniquement depuis le thread de rendu
        private var egl: EglWindow? = null
        private var loadedVersion = -1L
        private var visible = false
        private var framePending = false
        private var tiltX = 0f
        private var tiltY = 0f
        private var drawnX = Float.NaN
        private var drawnY = Float.NaN
        private var gpuName = ""
        private var settings = WallpaperSettings()
        private var still = false
        private var lastFrameNanos = 0L

        // Statistiques depuis la création du moteur
        private var frames = 0
        private var drawNanos = 0L
        private var sensorEvents = 0
        private var visibleSince = 0L
        private var visibleNanos = 0L

        @Volatile
        private var colors: Any? = null

        /** Signal de l'app : une œuvre vient d'être accrochée ou réglée. */
        private val changeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                renderHandler.post {
                    reload(force = false)
                    if (visible) requestFrame()
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setOffsetNotificationsEnabled(false)
            // Non exporté sur toutes les versions : seule l'app peut envoyer ce signal.
            ContextCompat.registerReceiver(
                this@GlimmerWallpaperService,
                changeReceiver,
                IntentFilter(WallpaperStore.ACTION_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            runOnRenderThread {
                egl = EglWindow(holder.surface)
                renderer.onSurfaceCreated()
                gpuName = GLES30.glGetString(GLES30.GL_RENDERER) ?: ""
                reload(force = true)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            runOnRenderThread {
                renderer.onSurfaceChanged(width, height)
                drawFrame()
            }
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            runOnRenderThread { drawFrame() }
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            super.onVisibilityChanged(isVisible)
            renderHandler.post {
                visible = isVisible
                if (isVisible) {
                    reload(force = false)
                    applySettings()
                    visibleSince = SystemClock.elapsedRealtimeNanos()
                    if (!still) tiltSensor.start()
                    requestFrame()
                } else {
                    tiltSensor.stop()
                    visibleNanos += SystemClock.elapsedRealtimeNanos() - visibleSince
                    writeStats()
                }
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            // La surface est détruite au retour de cette méthode : on libère EGL avant.
            runOnRenderThread {
                tiltSensor.stop()
                renderer.release()
                egl?.release()
                egl = null
            }
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            runCatching { unregisterReceiver(changeReceiver) }
            renderHandler.post {
                tiltSensor.stop()
                renderThread.quitSafely()
            }
            super.onDestroy()
        }

        @RequiresApi(27)
        override fun onComputeColors(): WallpaperColors? = colors as WallpaperColors?

        override fun doFrame(frameTimeNanos: Long) {
            framePending = false
            if (!visible) return
            // Cadence réduite (préférence « 30 i/s ») : on saute les vsync trop rapprochées.
            val minInterval = 1_000_000_000L / settings.frameRate - FRAME_TOLERANCE_NANOS
            if (lastFrameNanos != 0L && frameTimeNanos - lastFrameNanos < minInterval) {
                framePending = true
                Choreographer.getInstance().postFrameCallback(this)
                return
            }
            lastFrameNanos = frameTimeNanos
            drawFrame()
        }

        /** Relit les préférences : l'app les modifie pendant que le fond d'écran est masqué. */
        private fun applySettings() {
            settings = store.loadSettings()
            tiltSensor.recenterSeconds = if (settings.recenter) RECENTER_SECONDS else NO_RECENTER_SECONDS
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            still = settings.reduceMotion || (settings.pauseInBatterySaver && powerManager.isPowerSaveMode)
            if (still) {
                tiltX = 0f
                tiltY = 0f
            }
        }

        private fun onTilt(x: Float, y: Float) {
            sensorEvents++
            tiltX = x
            tiltY = y
            if (drawnX.isNaN() || abs(x - drawnX) > EPSILON || abs(y - drawnY) > EPSILON) requestFrame()
        }

        private fun requestFrame() {
            if (framePending || !visible || egl == null) return
            framePending = true
            Choreographer.getInstance().postFrameCallback(this)
        }

        private fun drawFrame() {
            val window = egl ?: return
            val start = System.nanoTime()
            renderer.draw(tiltX, tiltY)
            window.swapBuffers()
            drawNanos += System.nanoTime() - start
            frames++
            drawnX = tiltX
            drawnY = tiltY
        }

        private fun reload(force: Boolean) {
            val version = store.version()
            if (!force && version == loadedVersion) return
            val wallpaper = store.load() ?: return
            renderer.setContent(wallpaper.color, wallpaper.depth)
            renderer.params = wallpaper.params
            loadedVersion = wallpaper.version
            if (Build.VERSION.SDK_INT >= 27) {
                val width = 112
                val height = (width.toFloat() * wallpaper.color.height / wallpaper.color.width).roundToInt().coerceAtLeast(1)
                colors = WallpaperColors.fromBitmap(wallpaper.color.scale(width, height))
                mainHandler.post { notifyColorsChanged() }
            }
            drawnX = Float.NaN
        }

        private fun writeStats() {
            // L'aperçu du sélecteur système est éphémère : ses chiffres masqueraient ceux du vrai fond.
            if (isPreview) return
            val visibleSeconds = visibleNanos / 1e9
            val json = JSONObject()
                .put("frames", frames)
                .put("averageDrawMs", if (frames > 0) drawNanos / 1e6 / frames else 0.0)
                .put("visibleSeconds", visibleSeconds)
                .put("framesPerVisibleSecond", if (visibleSeconds > 0) frames / visibleSeconds else 0.0)
                .put("sensorEvents", sensorEvents)
                .put("sensor", tiltSensor.source.name)
                .put("gpu", gpuName)
                .put("updatedAt", System.currentTimeMillis())
            store.writeStats(json.toString())
        }

        private fun runOnRenderThread(block: () -> Unit) {
            val done = CountDownLatch(1)
            renderHandler.post {
                try {
                    block()
                } finally {
                    done.countDown()
                }
            }
            done.await(500, TimeUnit.MILLISECONDS)
        }
    }

    private companion object {
        /** Variation d'inclinaison minimale (sur [-1, 1]) qui justifie une nouvelle image. */
        const val EPSILON = 0.002f

        /** Marge sur l'intervalle entre deux images : la vsync n'est jamais parfaitement régulière. */
        const val FRAME_TOLERANCE_NANOS = 2_000_000L

        const val RECENTER_SECONDS = 3f

        /** Sans recentrage, la référence suit si lentement qu'elle paraît fixe. */
        const val NO_RECENTER_SECONDS = 3600f
    }
}
