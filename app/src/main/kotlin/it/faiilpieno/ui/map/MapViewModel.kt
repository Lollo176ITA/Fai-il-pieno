package it.faiilpieno.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.faiilpieno.data.location.LocationProvider
import it.faiilpieno.data.location.LocationResult
import it.faiilpieno.data.prefs.PreferencesRepository
import it.faiilpieno.data.repository.CommuteRepository
import it.faiilpieno.data.repository.PriceRepository
import it.faiilpieno.domain.brand.BrandGroup
import it.faiilpieno.domain.commute.Commute
import it.faiilpieno.domain.geo.BoundingBox
import it.faiilpieno.domain.map.MapClusterer
import it.faiilpieno.domain.map.MapMarker
import it.faiilpieno.domain.model.CarProfile
import it.faiilpieno.domain.model.DatasetInfo
import it.faiilpieno.domain.model.FuelCategory
import it.faiilpieno.domain.model.GeoPoint
import it.faiilpieno.domain.model.ServiceMode
import it.faiilpieno.domain.nearby.Offer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class Viewport(val box: BoundingBox, val zoom: Double)

data class MapUiState(
    val dataset: DatasetInfo? = null,
    val car: CarProfile = CarProfile(),
    /** Distributori e gruppi da disegnare nell'area visibile (più un margine). */
    val markers: List<MapMarker> = emptyList(),
    /** I più economici nell'area visibile, per la striscia in basso; vuota da troppo lontano. */
    val cheapest: List<Offer> = emptyList(),
    val nationalAverage: Int? = null,
    val loading: Boolean = false,
    /** Vero se la mappa è abbastanza vicina ma nell'area non c'è nessun distributore. */
    val emptyArea: Boolean = false,
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

    private data class CatalogKey(val fuel: FuelCategory, val mode: ServiceMode, val info: DatasetInfo?, val brands: Set<BrandGroup>)

    private data class Catalog(val offers: List<Offer> = emptyList(), val nationalAverage: Int? = null, val loading: Boolean = false)

    private data class Area(val markers: List<MapMarker>, val cheapest: List<Offer>, val empty: Boolean, val loading: Boolean, val average: Int?)

    /**
     * Tutti i distributori del carburante scelto, letti una volta sola: spostare la mappa non
     * interroga più il database. Il filtro marchi della scheda Oggi vale anche qui.
     */
    private val catalog = combine(prefs.carProfile, prices.datasetInfo, prefs.searchPreferences.map { it.brands }) { car, info, brands ->
        CatalogKey(car.fuel, car.serviceMode, info, brands)
    }.distinctUntilChanged().transformLatest { key ->
        if (key.info == null) {
            emit(Catalog())
            return@transformLatest
        }
        emit(Catalog(loading = true))
        val offers = prices.allOffers(key.fuel, key.mode).filter { BrandGroup.accepts(key.brands, it.station.brand) }
        emit(Catalog(offers, prices.nationalAverage(key.fuel, key.mode)))
    }.flowOn(Dispatchers.Default)

    private val area = combine(catalog, viewport.debounce(100)) { catalog, vp ->
        if (vp == null) return@combine Area(emptyList(), emptyList(), empty = false, catalog.loading, catalog.nationalAverage)
        // Il margine prepara i dintorni: spostando di poco la mappa i prezzi ci sono già.
        val markers = MapClusterer.cluster(catalog.offers, vp.box.expandedBy(MARGIN), vp.zoom)
        val cheapest = if (vp.zoom >= LIST_MIN_ZOOM) MapClusterer.cheapestIn(catalog.offers, vp.box, LIST_SIZE) else emptyList()
        val empty = !catalog.loading && vp.zoom >= LIST_MIN_ZOOM && cheapest.isEmpty()
        Area(markers, cheapest, empty, catalog.loading, catalog.nationalAverage)
    }.flowOn(Dispatchers.Default)

    val uiState: StateFlow<MapUiState> = combine(prices.datasetInfo, prefs.carProfile, area, userLocation, commutes.commutes) { info, car, a, user, routes ->
        MapUiState(info, car, a.markers, a.cheapest, a.average, a.loading, a.empty, user, routes)
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
        /** Da più lontano l'elenco dei più economici coprirebbe mezza regione: non serve. */
        const val LIST_MIN_ZOOM = 10.0
        const val LIST_SIZE = 10
        private const val MARGIN = 0.5
    }
}
