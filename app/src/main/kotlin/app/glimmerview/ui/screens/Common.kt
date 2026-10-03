package app.glimmerview.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringArrayResource
import androidx.core.os.ConfigurationCompat
import app.glimmerview.R
import app.glimmerview.core.ApertureScale
import java.text.NumberFormat

/** « f/16 », « f/11 », « f/8 », « f/5,6 », « f/4 », avec le séparateur décimal de la langue. */
@Composable
fun apertureLabels(): List<String> {
    val configuration = LocalConfiguration.current
    return remember(configuration) {
        val locale = ConfigurationCompat.getLocales(configuration)[0]
        val format = if (locale != null) NumberFormat.getInstance(locale) else NumberFormat.getInstance()
        ApertureScale.fNumbers.map { "f/" + format.format(it) }
    }
}

/** Repères de la bague : ∞, 10, 5, 3, 2, 1,5, 1 (mètres). */
@Composable
fun focusLabels(): List<String> {
    val configuration = LocalConfiguration.current
    return remember(configuration) {
        val locale = ConfigurationCompat.getLocales(configuration)[0]
        val format = if (locale != null) NumberFormat.getInstance(locale) else NumberFormat.getInstance()
        listOf("∞") + listOf(10.0, 5.0, 3.0, 2.0, 1.5, 1.0).map(format::format)
    }
}

@Composable
fun apertureWords(): List<String> = stringArrayResource(R.array.aperture_words).toList()

@Composable
fun focusWords(): List<String> = stringArrayResource(R.array.focus_words).toList()
