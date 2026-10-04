package app.glimmerview.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.glimmerview.R
import app.glimmerview.ui.components.RenameSheet
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.glimmerview.GlimmerViewModel
import app.glimmerview.Route
import app.glimmerview.lab.LabScreen
import app.glimmerview.lab.LabViewModel
import app.glimmerview.lab.PreviewScreen
import app.glimmerview.lab.Screen
import app.glimmerview.route
import app.glimmerview.tab
import app.glimmerview.ui.components.Socle
import app.glimmerview.ui.components.SystemBarIcons
import app.glimmerview.ui.theme.GlimmerColors
import app.glimmerview.ui.theme.LocalTilt
import app.glimmerview.ui.theme.rememberDeviceTilt

/** Racine de l'interface : pile de navigation, socle commun aux quatre salles, messages. */
@Composable
fun GlimmerApp(vm: GlimmerViewModel) {
    val tilt = rememberDeviceTilt(enabled = !vm.settings.reduceMotion)
    val context = LocalContext.current

    LaunchedEffect(vm.message) {
        val text = vm.message ?: return@LaunchedEffect
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        vm.message = null
    }
    BackHandler(enabled = vm.backStack.size > 1) { vm.back() }

    CompositionLocalProvider(LocalTilt provides tilt) {
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = vm.route,
                transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(160)) },
                // Les quatre salles partagent le même socle : on ne fond que leur contenu.
                contentKey = { if (it.tab != null) "salles" else it },
                label = "route",
            ) { route ->
                when (route) {
                    Route.Vernissage -> VernissageScreen(vm)
                    Route.Galerie, Route.Atelier, Route.Collection, Route.Preferences -> Rooms(vm, route)
                    is Route.Oeuvre -> OeuvreScreen(vm, route.ref)
                    is Route.MiseAuPoint -> MiseAuPointScreen(vm, route.ref)
                    is Route.Accrocher -> AccrocherScreen(vm, route.ref)
                    is Route.Esquisse -> EsquisseScreen(vm, route.ref)
                    Route.Tirage -> TirageScreen(vm)
                    Route.Lab -> LabRoute()
                }
            }
            Overlays(vm)
        }
    }
}

/** Panneaux posés par-dessus n'importe quel écran. */
@Composable
private fun Overlays(vm: GlimmerViewModel) {
    val renamingId = vm.renaming
    val renamingInfo = vm.works.firstOrNull { it.id == renamingId }
    if (renamingId != null && renamingInfo != null) {
        key(renamingId) {
            RenameSheet(
                kicker = stringResource(R.string.rename_kicker),
                currentTitle = renamingInfo.title,
                cancelLabel = stringResource(R.string.action_cancel),
                saveLabel = stringResource(R.string.rename_save),
                onRename = { vm.renameWork(renamingId, it) },
                onDismiss = vm::cancelRename,
            )
        }
    }
}

/** Les quatre salles : un panneau plâtre aux coins arrondis posé sur le socle d'encre. */
@Composable
private fun Rooms(vm: GlimmerViewModel, route: Route) {
    SystemBarIcons(darkStatusBarIcons = true, darkNavigationBarIcons = false)
    Column(Modifier.fillMaxSize().background(GlimmerColors.Ink)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 34.dp, bottomEnd = 34.dp))
                .background(GlimmerColors.Plaster),
        ) {
            Crossfade(targetState = route, animationSpec = tween(220), label = "salle") { room ->
                when (room) {
                    Route.Galerie -> GalerieRoom(vm)
                    Route.Atelier -> AtelierRoom(vm)
                    Route.Collection -> CollectionRoom(vm)
                    else -> PreferencesRoom(vm)
                }
            }
        }
        Socle(selected = route.tab, onSelect = { vm.selectTab(it.route) })
    }
}

/** Contenu défilant d'une salle, sous la barre d'état. */
@Composable
fun RoomPage(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/** Le laboratoire de la Phase 0, gardé pour mesurer les performances sur un appareil. */
@Composable
private fun LabRoute() {
    val lab: LabViewModel = viewModel()
    Box(Modifier.fillMaxSize().background(GlimmerColors.Plaster).statusBarsPadding()) {
        when (lab.screen) {
            Screen.Lab -> LabScreen(lab)
            Screen.Preview -> PreviewScreen(lab)
        }
    }
}
