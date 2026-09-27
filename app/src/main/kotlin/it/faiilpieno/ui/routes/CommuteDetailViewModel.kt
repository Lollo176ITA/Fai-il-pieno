package it.faiilpieno.ui.routes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import it.faiilpieno.data.prefs.PreferencesRepository
import it.faiilpieno.data.repository.CommuteRepository
import it.faiilpieno.data.repository.PriceRepository
import it.faiilpieno.data.repository.RouteJob
import it.faiilpieno.domain.brand.BrandGroup
import it.faiilpieno.domain.commute.Commute
import it.faiilpieno.domain.commute.CommuteResult
import it.faiilpieno.domain.commute.CommuteSort
import it.faiilpieno.domain.model.CarProfile
import it.faiilpieno.domain.model.DatasetInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DetailResults {
    /** Percorso non calcolato o prezzi non ancora scaricati. */
    data object Unavailable : DetailResults
    data object Loading : DetailResults
    data class Ready(val result: CommuteResult) : DetailResults
}

data class CommuteDetailState(
    val loaded: Boolean = false,
    /** null dopo il caricamento = il tragitto è stato eliminato. */
    val commute: Commute? = null,
    val job: RouteJob? = null,
    val bufferMeters: Int = PreferencesRepository.DEFAULT_ROUTE_BUFFER_M,
    val sort: CommuteSort = CommuteSort.PRICE,
    val car: CarProfile = CarProfile(),
    val brands: Set<BrandGroup> = emptySet(),
    val dataset: DatasetInfo? = null,
    val results: DetailResults = DetailResults.Loading,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CommuteDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CommuteRepository,
    private val prefs: PreferencesRepository,
    prices: PriceRepository,
) : ViewModel() {

    private val commuteId = savedStateHandle.toRoute<CommuteDetailRoute>().commuteId
    private val sort = MutableStateFlow(CommuteSort.PRICE)

    private val settings = combine(prefs.carProfile, prefs.routeBufferMeters, prefs.searchPreferences, prices.datasetInfo, sort) { car, buffer, search, info, sort ->
        CommuteDetailState(bufferMeters = buffer, sort = sort, car = car, brands = search.brands, dataset = info)
    }

    /** Ultimo risultato pronto: resta visibile mentre si ricalcola, così la lista non lampeggia. */
    private var lastReady: DetailResults.Ready? = null

    val state: StateFlow<CommuteDetailState> = combine(
        repository.commute(commuteId),
        repository.routeJobs.map { it[commuteId] }.distinctUntilChanged(),
        settings,
    ) { commute, job, base -> base.copy(loaded = true, commute = commute, job = job) }
        .transformLatest { base ->
            val commute = base.commute
            if (commute?.route == null || base.dataset == null) {
                lastReady = null
                emit(base.copy(results = DetailResults.Unavailable))
                return@transformLatest
            }
            emit(base.copy(results = lastReady ?: DetailResults.Loading))
            val result = repository.advise(commute, base.car, base.bufferMeters.toDouble(), base.brands, base.sort)
            val ready = result?.let(DetailResults::Ready)
            lastReady = ready
            emit(base.copy(results = ready ?: DetailResults.Unavailable))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CommuteDetailState())

    fun setBuffer(meters: Int) = viewModelScope.launch { prefs.setRouteBuffer(meters) }

    fun setSort(value: CommuteSort) {
        sort.value = value
    }

    fun recomputeRoute() = repository.requestRoute(commuteId)

    fun clearBrands() = viewModelScope.launch { prefs.setBrands(emptySet()) }
}
