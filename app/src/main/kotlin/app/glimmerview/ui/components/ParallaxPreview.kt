package app.glimmerview.ui.components

import android.app.Activity
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.glimmerview.core.DepthMap
import app.glimmerview.core.ParallaxParams
import app.glimmerview.render.ParallaxGlView

/** Image et profondeur d'une œuvre, prêtes pour le moteur de rendu. */
class PreviewContent(val key: String, val color: Bitmap, val depth: DepthMap)

/** Aperçu 3D en direct : le même moteur que le fond d'écran, piloté par le capteur. */
@Composable
fun ParallaxPreview(
    content: PreviewContent?,
    params: ParallaxParams,
    depthView: Boolean,
    modifier: Modifier = Modifier,
    /** Appelé quand [content] est dessiné pour la première fois (la vignette posée dessus peut s'effacer). */
    onShown: (PreviewContent) -> Unit = {},
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val shown by rememberUpdatedState(onShown)
    var view by remember { mutableStateOf<ParallaxGlView?>(null) }
    AndroidView(
        factory = { context ->
            ParallaxGlView(context).also { glView ->
                glView.measureGpu = false
                glView.onContentShown = { token -> (token as? PreviewContent)?.let { shown(it) } }
                view = glView
            }
        },
        modifier = modifier,
        update = { glView ->
            if (content != null && glView.tag !== content) {
                glView.setContent(content.color, content.depth, content)
                glView.tag = content
            }
            glView.setParams(params)
            glView.setDepthView(depthView)
        },
        onRelease = { it.stop() },
    )
    val glView = view ?: return
    DisposableEffect(lifecycleOwner, glView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glView.start()
                Lifecycle.Event.ON_PAUSE -> glView.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

/** Icônes des barres système : sombres sur le plâtre, claires sur le socle d'encre. */
@Composable
fun SystemBarIcons(darkStatusBarIcons: Boolean = true, darkNavigationBarIcons: Boolean = true) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = darkStatusBarIcons
            isAppearanceLightNavigationBars = darkNavigationBarIcons
        }
    }
}
