package app.glimmerview

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import app.glimmerview.ui.screens.GlimmerApp
import app.glimmerview.ui.theme.GlimmerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Barres système transparentes ; la couleur des icônes est ajustée écran par écran.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            GlimmerTheme {
                val viewModel: GlimmerViewModel = viewModel()
                GlimmerApp(viewModel)
            }
        }
    }
}
