package app.glimmerview

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import androidx.lifecycle.ViewModelProvider
import app.glimmerview.ui.screens.GlimmerApp
import app.glimmerview.ui.theme.GlimmerTheme

class MainActivity : ComponentActivity() {

    // Même instance que celle fournie à Compose : le ViewModelStore de l'activité.
    private val viewModel: GlimmerViewModel by lazy { ViewModelProvider(this)[GlimmerViewModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Barres système transparentes ; la couleur des icônes est ajustée écran par écran.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // Après une rotation, l'intention de départ a déjà été traitée.
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            GlimmerTheme {
                GlimmerApp(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    /** Image partagée depuis une autre app : elle part directement au tirage. */
    private fun handleShare(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND || intent.type?.startsWith("image/") != true) return
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            ?: return
        viewModel.startSharedCreation(uri)
    }
}
