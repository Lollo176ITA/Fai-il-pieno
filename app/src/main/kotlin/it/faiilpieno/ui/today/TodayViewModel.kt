package it.faiilpieno.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.faiilpieno.data.location.LocationProvider
import it.faiilpieno.data.location.LocationResult
import it.faiilpieno.data.prefs.PreferencesRepository
import it.faiilpieno.data.prefs.SearchPreferences
import it.faiilpieno.data.repository.PriceRepository
import it.faiilpieno.data.work.DataSync
import it.faiilpieno.data.work.SyncStatus
import it.faiilpieno.domain.brand.BrandGroup
import it.faiilpieno.domain.model.CarProfile
import it.faiilpieno.domain.model.DatasetInfo
import it.faiilpieno.domain.model.FuelCategory
import it.faiilpieno.domain.model.GeoPoint
import it.faiilpieno.domain.model.ServiceMode
import it.faiilpieno.domain.nearby.NearbyRanker
import it.faiilpieno.domain.nearby.NearbyResult
import it.faiilpieno.domain.nearby.Offer
import it.faiilpieno.domain.nearby.SortMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

sealed interface LocationState {
    data object Locating : LocationState
    data object PermissionNeeded : LocationState
    data object ServicesOff : LocationState
    data object Unavailable : LocationState
    data class Found(val point: GeoPoint) : LocationState
}

sealed interface ResultsState {
    data object Idle : ResultsState
    data object Loading : ResultsState
    /** [isRefreshing]: si sta ricalcolando, intanto restano visibili i risultati precedenti. */
    data class Ready(val result: NearbyResult, val radiusMeters: Double, val isRefreshing: Boolean = false) : ResultsState
}

data class TodayUiState(
    val dataset: DatasetInfo? = null,
    val sync: SyncStatus = SyncStatus.Idle,
    val location: LocationState = LocationState.Locating,
    val car: CarProfile = CarProfile(),
    val search: SearchPreferences = SearchPreferences(),
    val results: ResultsState = ResultsState.Idle,
    val isDataStale: Boolean = false,
    val isOnline: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val prices: PriceRepository,
    private val prefs: PreferencesRepository,
    private val locationProvider: LocationProvider,
    private val dataSync: DataSync,
) : ViewModel() {

    private val location = MutableStateFlow<LocationState>(LocationState.Locating)
    private val reloadTick = MutableStateFlow(0)

    private data class SearchKey(
        val info: DatasetInfo,
        val point: GeoPoint,
        val fuel: FuelCategory,
        val mode: ServiceMode,
        val tick: Int,
    )

    private sealed interface RawSearch {
        data object Idle : RawSearch
        data object Loading : RawSearch
        data class Done(val offers: List<Offer>, val radius: Double, val nationalAverage: Int?) : RawSearch
    }

    /** La query sul DB riparte solo quando cambiano dati, posizione, carburante o modalità. */
    private val rawSearch = combine(prices.datasetInfo, location, prefs.carProfile, reloadTick) { info, loc, car, tick ->
        if (info != null && loc is LocationState.Found) SearchKey(info, loc.point, car.fuel, car.serviceMode, tick) else null
    }
        .distinctUntilChanged()
        .transformLatest { key ->
            if (key == null) {
                emit(RawSearch.Idle)
            } else {
                emit(RawSearch.Loading)
                val search = prices.findNearby(key.point, key.fuel, key.mode)
                emit(RawSearch.Done(search.offers, search.radiusMeters, prices.nationalAverage(key.fuel, key.mode)))
            }
        }

    /** Ordinamento, filtro marchi e risparmio sono calcoli in memoria: rapidi a ogni modifica. */
    private val results = combine(rawSearch, prefs.carProfile, prefs.searchPreferences) { raw, car, search ->
        when (raw) {
            RawSearch.Idle -> ResultsState.Idle
            RawSearch.Loading -> ResultsState.Loading
            is RawSearch.Done -> ResultsState.Ready(
                NearbyRanker.rank(raw.offers, raw.nationalAverage, car, search.sortMode, search.brands),
                raw.radius,
            )
        }
    }.runningFold(ResultsState.Idle as ResultsState) { previous, next ->
        if (next is ResultsState.Loading && previous is ResultsState.Ready) previous.copy(isRefreshing = true) else next
    }

    val uiState: StateFlow<TodayUiState> = combine(
        prices.datasetInfo,
        dataSync.status,
        location,
        combine(prefs.carProfile, prefs.searchPreferences, ::Pair),
        results,
    ) { info, sync, loc, (car, search), res ->
        TodayUiState(
            dataset = info,
            sync = sync,
            location = loc,
            car = car,
            search = search,
            results = res,
            isDataStale = info != null && info.extractionDate.isBefore(LocalDate.now().minusDays(STALE_AFTER_DAYS)),
            isOnline = dataSync.isOnline(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    init {
        viewModelScope.launch {
            if (needsRefresh(prices.datasetInfo.first())) dataSync.refreshNow()
        }
        refreshLocation()
    }

    fun refreshLocation() {
        viewModelScope.launch {
            if (location.value !is LocationState.Found) location.value = LocationState.Locating
            location.value = when (val result = locationProvider.current()) {
                is LocationResult.Found -> LocationState.Found(result.point)
                LocationResult.PermissionDenied -> LocationState.PermissionNeeded
                LocationResult.ServicesOff -> LocationState.ServicesOff
                LocationResult.Unavailable -> LocationState.Unavailable
            }
        }
    }

    /** Pull-to-refresh: nuova posizione e, se servono, dati nuovi. */
    fun refresh() {
        refreshLocation()
        reloadTick.update { it + 1 }
        viewModelScope.launch {
            if (needsRefresh(prices.datasetInfo.first())) dataSync.refreshNow()
        }
    }

    /** Al ritorno dalle impostazioni di sistema il permesso o il GPS potrebbero essere cambiati. */
    fun onResume() {
        val current = location.value
        if (current is LocationState.PermissionNeeded || current is LocationState.ServicesOff || current is LocationState.Unavailable) {
            refreshLocation()
        }
    }

    fun retryDownload() = dataSync.refreshNow()

    fun setFuel(fuel: FuelCategory) = viewModelScope.launch { prefs.setFuel(fuel) }

    fun setServiceMode(mode: ServiceMode) = viewModelScope.launch { prefs.setServiceMode(mode) }

    fun setSortMode(mode: SortMode) = viewModelScope.launch { prefs.setSortMode(mode) }

    fun setBrands(brands: Set<BrandGroup>) = viewModelScope.launch { prefs.setBrands(brands) }

    /**
     * Il file MIMIT esce verso le 9 con i prezzi del giorno prima: prima delle 10 l'ultimo
     * disponibile è quello di due giorni fa. Così non si riscarica a vuoto a ogni apertura.
     */
    private fun needsRefresh(info: DatasetInfo?): Boolean {
        if (info == null) return true
        val daysBack = if (LocalTime.now().isBefore(LocalTime.of(10, 0))) 2L else 1L
        return info.extractionDate.isBefore(LocalDate.now().minusDays(daysBack))
    }

    private companion object {
        const val STALE_AFTER_DAYS = 3L
    }
}
