package it.faiilpieno.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.faiilpieno.data.location.LocationProvider
import it.faiilpieno.data.location.LocationResult
import it.faiilpieno.data.prefs.PreferencesRepository
import it.faiilpieno.data.repository.CommuteRepository
import it.faiilpieno.domain.commute.Commute
import it.faiilpieno.data.repository.PriceRepository
import it.faiilpieno.domain.geo.BoundingBox
import it.faiilpieno.domain.model.CarProfile
import it.faiilpieno.domain.model.DatasetInfo
import it.faiilpieno.domain.model.GeoPoint
import it.faiilpieno.domain.nearby.Offer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class Viewport(val box: BoundingBox, val zoom: Double)

data class MapUiState(
    val dataset: DatasetInfo? = null,
    val car: CarProfile = CarProfile(),
    val offers: List<Offer> = emptyList(),
    val nationalAverage: Int? = null,
    val zoomTooLow: Boolean = true,
    val userLocation: GeoPoint? = null,
    val commutes: List<Commute> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class MapViewModel @Inject constructor(
    private val prices: PriceRepository,
    prefs: PreferencesRepository,
    private val locationProvider: LocationProvider,
    commutes: CommuteRepository,
) : ViewModel() {

    private val viewport = MutableStateFlow<Viewport?>(null)
    private val userLocation = MutableStateFlow<GeoPoint?>(null)

    private data class AreaQuery(val viewport: Viewport?, val car: CarProfile, val info: DatasetInfo?, val user: GeoPoint?)

    private data class AreaResult(val offers: List<Offer>, val nationalAverage: Int?, val zoomTooLow: Boolean)

    private val area = combine(viewport.debounce(250), prefs.carProfile, prices.datasetInfo, userLocation) { vp, car, info, user ->
        AreaQuery(vp, car, info, user)
    }.mapLatest { (vp, car, info, user) ->
        when {
            info == null || vp == null -> AreaResult(emptyList(), null, zoomTooLow = vp == null || vp.zoom < MIN_ZOOM)
            vp.zoom < MIN_ZOOM -> AreaResult(emptyList(), null, zoomTooLow = true)
            else -> AreaResult(
                prices.offersInArea(vp.box, car.fuel, car.serviceMode, user, MAX_MARKERS),
                prices.nationalAverage(car.fuel, car.serviceMode),
                zoomTooLow = false,
            )
        }
    }

    val uiState: StateFlow<MapUiState> = combine(prices.datasetInfo, prefs.carProfile, area, userLocation, commutes.commutes) { info, car, a, user, routes ->
        MapUiState(info, car, a.offers, a.nationalAverage, a.zoomTooLow, user, routes)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    init {
        viewModelScope.launch { locateUser() }
    }

    fun onViewportChanged(box: BoundingBox, zoom: Double) {
        viewport.value = Viewport(box, zoom)
    }

    suspend fun locateUser(): GeoPoint? {
        val found = (locationProvider.current() as? LocationResult.Found)?.point
        if (found != null) userLocation.value = found
        return found
    }

    companion object {
        /** Sotto questo zoom i distributori sarebbero migliaia: si chiede di avvicinarsi. */
        const val MIN_ZOOM = 10.5
        const val MAX_MARKERS = 400
    }
}
