package it.faiilpieno.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.faiilpieno.data.prefs.PreferencesRepository
import it.faiilpieno.data.prefs.SearchPreferences
import it.faiilpieno.domain.brand.BrandGroup
import it.faiilpieno.domain.commute.RouteAvoidance
import it.faiilpieno.domain.commute.RoutePreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PreferencesState(
    val loaded: Boolean = false,
    val route: RoutePreferences = RoutePreferences(),
    val buffer: Int = PreferencesRepository.DEFAULT_ROUTE_BUFFER_M,
    val search: SearchPreferences = SearchPreferences(),
)

@HiltViewModel
class PreferencesViewModel @Inject constructor(private val prefs: PreferencesRepository) : ViewModel() {
    val state = combine(prefs.routePreferences, prefs.routeBufferMeters, prefs.searchPreferences) { route, buffer, search ->
        PreferencesState(true, route, buffer, search)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PreferencesState())

    fun setAvoidance(feature: RouteAvoidance, enabled: Boolean) = viewModelScope.launch { prefs.setRouteAvoidance(feature, enabled) }
    fun setBuffer(meters: Int) = viewModelScope.launch { prefs.setRouteBuffer(meters) }
    fun setBrands(brands: Set<BrandGroup>) = viewModelScope.launch { prefs.setBrands(brands) }
}
