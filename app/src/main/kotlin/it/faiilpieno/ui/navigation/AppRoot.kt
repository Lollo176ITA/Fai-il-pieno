package it.faiilpieno.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import it.faiilpieno.ui.today.TodayScreen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import javax.inject.Inject
import kotlin.reflect.KClass

@Serializable data object TodayRoute

@Serializable data object MapRoute

@Serializable data object CarRoute

private enum class Tab(val route: Any, val routeClass: KClass<*>, @StringRes val label: Int, @DrawableRes val icon: Int) {
    TODAY(TodayRoute, TodayRoute::class, R.string.tab_today, R.drawable.ic_gas_station),
    MAP(MapRoute, MapRoute::class, R.string.tab_map, R.drawable.ic_map),
    CAR(CarRoute, CarRoute::class, R.string.tab_car, R.drawable.ic_car),
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

@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    Scaffold(bottomBar = { BottomBar(navController) }) { padding ->
        NavHost(
            navController = navController,
            startDestination = TodayRoute,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable<TodayRoute> { TodayScreen(onOpenCar = { navController.navigateToTab(Tab.CAR) }) }
            composable<MapRoute> { MapScreen() }
            composable<CarRoute> { CarScreen() }
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
                selected = destination?.hasRoute(tab.routeClass) == true,
                onClick = { navController.navigateToTab(tab) },
                icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                label = { Text(stringResource(tab.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
