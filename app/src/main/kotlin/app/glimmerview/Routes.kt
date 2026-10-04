package app.glimmerview

import app.glimmerview.ui.components.SocleTab

/** Une œuvre, qu'elle vienne de la galerie (scène dessinée) ou de la collection personnelle. */
sealed interface WorkRef {
    data class Scene(val id: String) : WorkRef
    data class Saved(val id: String) : WorkRef
}

/** Écrans de l'app ; la pile de navigation est tenue par [GlimmerViewModel]. */
sealed interface Route {
    data object Vernissage : Route
    data object Galerie : Route
    data object Atelier : Route
    data object Collection : Route
    data object Preferences : Route
    data class Oeuvre(val ref: WorkRef) : Route
    data class MiseAuPoint(val ref: WorkRef) : Route
    data class Accrocher(val ref: WorkRef) : Route
    data class Esquisse(val ref: WorkRef.Saved) : Route
    data object Tirage : Route
    data object Lab : Route
}

val Route.tab: SocleTab?
    get() = when (this) {
        Route.Galerie -> SocleTab.GALLERY
        Route.Atelier -> SocleTab.ATELIER
        Route.Collection -> SocleTab.COLLECTION
        Route.Preferences -> SocleTab.PREFERENCES
        else -> null
    }

val SocleTab.route: Route
    get() = when (this) {
        SocleTab.GALLERY -> Route.Galerie
        SocleTab.ATELIER -> Route.Atelier
        SocleTab.COLLECTION -> Route.Collection
        SocleTab.PREFERENCES -> Route.Preferences
    }
