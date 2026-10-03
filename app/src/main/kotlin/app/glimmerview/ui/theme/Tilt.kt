package app.glimmerview.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.glimmerview.render.TiltSensor

/** Inclinaison du téléphone, filtrée et recentrée, sur [-1, 1]. Lue par les cadres vivants. */
@Stable
class TiltState {
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)

    /** Faux si aucun capteur n'est disponible ou si les mouvements sont réduits. */
    var live by mutableStateOf(false)
}

val LocalTilt = staticCompositionLocalOf { TiltState() }

/** Suit le capteur d'orientation pendant que l'app est au premier plan. */
@Composable
fun rememberDeviceTilt(enabled: Boolean): TiltState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state = remember { TiltState() }
    DisposableEffect(enabled, lifecycleOwner) {
        if (!enabled) {
            state.x = 0f
            state.y = 0f
            state.live = false
            return@DisposableEffect onDispose { }
        }
        val sensor = TiltSensor(context) { x, y ->
            state.x = x
            state.y = y
        }
        state.live = sensor.source != TiltSensor.Source.NONE
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> sensor.start()
                Lifecycle.Event.ON_PAUSE -> sensor.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sensor.stop()
        }
    }
    return state
}
