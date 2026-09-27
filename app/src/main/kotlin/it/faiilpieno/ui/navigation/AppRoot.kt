package it.faiilpieno.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.lifecycle.HiltViewModel
import it.faiilpieno.R
import it.faiilpieno.data.prefs.PreferencesRepository
import it.faiilpieno.ui.car.CarScreen
import it.faiilpieno.ui.map.MapScreen
import it.faiilpieno.ui.onboarding.OnboardingScreen
import it.faiilpieno.ui.routes.CommuteDetailRoute
import it.faiilpieno.ui.routes.CommuteDetailScreen
import it.faiilpieno.ui.routes.CommuteEditRoute
import it.faiilpieno.ui.routes.CommuteEditScreen
import it.faiilpieno.ui.routes.PlaceEditRoute
import it.faiilpieno.ui.routes.PlaceEditScreen
import it.faiilpieno.ui.routes.RoutesRoute
import it.faiilpieno.ui.routes.RoutesScreen
import it.faiilpieno.ui.today.TodayScreen
import javax.inject.Inject
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable

@Serializable data object TodayRoute

@Serializable data object MapRoute

@Serializable data object SettingsRoute

/** [routeClasses]: le destinazioni in cui la scheda risulta selezionata (anche quelle annidate). */
private enum class Tab(val route: Any, val routeClasses: Set<KClass<*>>, @StringRes val label: Int, @DrawableRes val icon: Int) {
    TODAY(TodayRoute, setOf(TodayRoute::class), R.string.tab_today, R.drawable.ic_gas_station),
    MAP(MapRoute, setOf(MapRoute::class), R.string.tab_map, R.drawable.ic_map),
    ROUTES(
        RoutesRoute,
        setOf(RoutesRoute::class, PlaceEditRoute::class, CommuteEditRoute::class, CommuteDetailRoute::class),
        R.string.tab_routes,
        R.drawable.ic_route,
    ),

}

@HiltViewModel
class RootViewModel @Inject constructor(prefs: PreferencesRepository) : ViewModel() {
    /** null finché le preferenze non sono lette: evita di mostrare l'onboarding per un istante. */
    val onboardingDone: StateFlow<Boolean?> = prefs.onboardingDone.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

@Composable
fun AppRoot(viewModel: RootViewModel = hiltViewModel()) {
    val onboardingDone by viewModel.onboardingDone.collectAsStateWithLifecycle()
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        when (onboardingDone) {
            null -> Unit
            false -> OnboardingScreen()
            true -> MainScaffold()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val destination = entry?.destination
    val tab = Tab.entries.firstOrNull { destination?.hasRoute(it.route::class) == true }
    val settings = destination?.hasRoute<SettingsRoute>() == true
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        topBar = {
            if (tab != null || settings) {
                TopAppBar(
                    expandedHeight = (64 * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp,
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    title = {
                        Column {
                            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(stringResource(if (settings) R.string.settings_title else tab!!.label),
                                style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                        }
                    },
                    navigationIcon = {
                        if (settings) IconButton(onClick = { navController.popBackStack() }) {
                            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                        }
                    },
                    actions = {
                        if (!settings) FilledTonalIconButton(
                            onClick = { navController.navigate(SettingsRoute) { launchSingleTop = true } },
                            modifier = Modifier.padding(end = 12.dp).size(48.dp),
                        ) { Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings_title)) }
                    },
                )
            }
        },
        bottomBar = { if (tab != null) BottomBar(navController) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TodayRoute,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable<TodayRoute> {
                TodayScreen(
                    onOpenCar = { navController.navigate(SettingsRoute) },
                    onOpenCommute = { navController.navigate(CommuteDetailRoute(it)) },
                )
            }
            composable<MapRoute> { MapScreen(onOpenCommute = { navController.navigate(CommuteDetailRoute(it)) }) }
            composable<RoutesRoute> {
                RoutesScreen(
                    onAddPlace = { kind -> navController.navigate(PlaceEditRoute(kind = kind?.name)) },
                    onEditPlace = { navController.navigate(PlaceEditRoute(placeId = it)) },
                    onAddCommute = { navController.navigate(CommuteEditRoute()) },
                    onOpenCommute = { navController.navigate(CommuteDetailRoute(it)) },
                )
            }
            composable<PlaceEditRoute> { PlaceEditScreen(onBack = navController::popBackStack) }
            composable<CommuteEditRoute> {
                CommuteEditScreen(
                    onBack = navController::popBackStack,
                    onSaved = { id, isNew ->
                        // Un tragitto nuovo apre il suo dettaglio; una modifica torna al dettaglio da cui si era partiti.
                        if (isNew) {
                            navController.navigate(CommuteDetailRoute(id)) {
                                popUpTo<CommuteEditRoute> { inclusive = true }
                            }
                        } else {
                            navController.popBackStack()
                        }
                    },
                    // Si elimina solo da un tragitto esistente, cioè partendo dal suo dettaglio.
                    onDeleted = { navController.popBackStack<CommuteDetailRoute>(inclusive = true) },
                )
            }
            composable<CommuteDetailRoute> {
                CommuteDetailScreen(
                    onBack = navController::popBackStack,
                    onEdit = { navController.navigate(CommuteEditRoute(it)) },
                )
            }
            composable<SettingsRoute> { CarScreen() }
        }
    }
}

@Composable
private fun BottomBar(navController: NavHostController) {
    val entry by navController.currentBackStackEntryAsState()
    val destination = entry?.destination
    ShortNavigationBar {
        Tab.entries.forEach { tab ->
            ShortNavigationBarItem(
                selected = tab.routeClasses.any { destination?.hasRoute(it) == true },
                onClick = { navController.navigateToTab(tab) },
                icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                // Le etichette possono andare a capo con il testo ingrandito.
                label = {
                    Text(stringResource(tab.label), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                },
            )
        }
    }
}

private fun NavHostController.navigateToTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
