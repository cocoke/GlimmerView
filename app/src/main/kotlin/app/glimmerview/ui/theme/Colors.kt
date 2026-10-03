package app.glimmerview.ui.theme

import androidx.compose.ui.graphics.Color

/** Palette « Galerie » (charte V3). */
object GlimmerColors {
    /** Plâtre : le mur, fond de toutes les salles. */
    val Plaster = Color(0xFFF3F0EA)

    /** Passe-partout des cadres. */
    val Mat = Color(0xFFFBFAF6)

    /** Lin : panneaux et zones en creux. */
    val Linen = Color(0xFFE7E2D8)

    /** Pierre : filets de 1 dp. */
    val Stone = Color(0xFFCFC8BB)

    /** Bord fin des cadres. */
    val FrameEdge = Color(0xFFDED8CC)

    /** Encre : texte, actions principales, socle de navigation. */
    val Ink = Color(0xFF1C1B19)

    /** Taupe : texte secondaire, cartels. */
    val Taupe = Color(0xFF6F695F)

    /** Bleu de Prusse : l'accent unique. */
    val Prussian = Color(0xFF1F3A5F)

    // Vision profondeur « cyanotype » : loin → près
    val CyanoFar = Color(0xFF14243B)
    val CyanoMid = Color(0xFF3D5A80)
    val CyanoNear = Color(0xFFEDEAE2)
}
