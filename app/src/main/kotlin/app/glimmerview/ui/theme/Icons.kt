package app.glimmerview.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Pictogrammes au trait de la charte (grille de 24, trait de 1,5, extrémités arrondies). */
object GlimmerIcons {
    val Arrow by lazy { stroke("arrow", "M4 12h16M14 6l6 6-6 6") }
    val Back by lazy { stroke("back", "M20 12H4M10 6l-6 6 6 6") }
    val Close by lazy { stroke("close", "M6 6l12 12M18 6L6 18") }
    val Plus by lazy { stroke("plus", "M12 5v14M5 12h14") }
    val Check by lazy { stroke("check", "M5 12.5l4.5 4.5L19 7.5") }
    val Lift by lazy { stroke("lift", "M6 14l6-6 6 6") }
    val Chevron by lazy { stroke("chevron", "M9 6l6 6-6 6") }
    val Brush by lazy { stroke("brush", "M4 20l4-1 11-11-3-3L5 16z", "M14 6l3 3") }
    val Heart by lazy {
        stroke("heart", "M12 19.5s-7-4.2-7-9.7a3.9 3.9 0 0 1 7-2.4 3.9 3.9 0 0 1 7 2.4c0 5.5-7 9.7-7 9.7z")
    }
    val Share by lazy { stroke("share", "M12 15V4M8 8l4-4 4 4M5 12v7h14v-7") }
    val Trash by lazy { stroke("trash", "M5 7h14M10 7V4.5h4V7M7 7l1 13h8l1-13") }
    val Layers by lazy {
        stroke("layers", "M12 3.5l8.5 4.5-8.5 4.5-8.5-4.5z", "M3.5 12.5l8.5 4.5 8.5-4.5", "M3.5 16.5l8.5 4.5 8.5-4.5")
    }
    val Minus by lazy { stroke("minus", "M5 12h14") }
    val Wave by lazy { stroke("wave", "M3 14c3-4 6-4 9 0s6 4 9 0") }
    val Flat by lazy { stroke("flat", "M4 9h16M4 15h16") }
    val Eraser by lazy { stroke("eraser", "M8 20h12", "M5 15l8-8 6 6-5 5H8z") }
    val Undo by lazy { stroke("undo", "M9 14L4 9l5-5", "M4 9h10a6 6 0 0 1 0 12h-3") }
    val Redo by lazy { stroke("redo", "M15 14l5-5-5-5", "M20 9H10a6 6 0 0 0 0 12h3") }
    val Eye by lazy {
        stroke("eye", "M2.5 12s3.5-6 9.5-6 9.5 6 9.5 6-3.5 6-9.5 6-9.5-6-9.5-6z", "M9.5 12a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0z")
    }
    val Pencil by lazy { stroke("pencil", "M4 20h4L19 9l-4-4L4 16z", "M13 7l4 4") }

    /** Accrocher : un cadre suspendu à son clou. */
    val Hang by lazy { stroke("hang", "M12 3.5v1", "M8 10l4-4.5 4 4.5", "M5 10h14v10H5z") }
    val Dial by lazy {
        stroke("dial", "M4 12a8 8 0 1 0 16 0a8 8 0 1 0 -16 0z", "M12 12l4-3")
    }

    // Socle de navigation
    val Gallery by lazy {
        stroke("gallery", "M3.5 4.5h7v9h-7z", "M13.5 4.5h7v5.5h-7z", "M13.5 13h7v6.5h-7z", "M3.5 16.5h7v3h-7z")
    }
    val Atelier by lazy { stroke("atelier", "M4 3.5h16v17H4z", "M7 6.5h10v11H7z", "M12 9.5v5M9.5 12h5") }
    val Collection by lazy { stroke("collection", "M5 7.5h14M6.5 4.5h11M4 10.5h16v9H4z") }
    val Preferences by lazy {
        stroke(
            "preferences",
            "M4 12a8 8 0 1 0 16 0a8 8 0 1 0 -16 0z",
            "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z",
            "M12 4v2.5M12 17.5V20M4 12h2.5M17.5 12H20",
        )
    }

    private fun stroke(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            for (path in paths) {
                addPath(
                    pathData = addPathNodes(path),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
}
