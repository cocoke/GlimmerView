package app.glimmerview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import app.glimmerview.lab.LabScreen
import app.glimmerview.lab.LabViewModel
import app.glimmerview.lab.PreviewScreen
import app.glimmerview.lab.Screen
import app.glimmerview.ui.GlimmerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GlimmerTheme {
                val viewModel: LabViewModel = viewModel()
                when (viewModel.screen) {
                    Screen.Lab -> LabScreen(viewModel)
                    Screen.Preview -> PreviewScreen(viewModel)
                }
            }
        }
    }
}
