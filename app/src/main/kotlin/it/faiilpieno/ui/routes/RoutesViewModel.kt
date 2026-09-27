package it.faiilpieno.ui.routes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.faiilpieno.data.prefs.PreferencesRepository
import it.faiilpieno.data.repository.CommuteRepository
import it.faiilpieno.data.repository.PriceRepository
import it.faiilpieno.data.repository.RouteJob
import it.faiilpieno.domain.brand.BrandGroup
import it.faiilpieno.domain.commute.Commute
import it.faiilpieno.domain.commute.CommuteResult
import it.faiilpieno.domain.commute.Place
import it.faiilpieno.domain.model.CarProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class CommuteCard(
    val commute: Commute,
    /** Calcolo del percorso in corso o fallito. */
    val job: RouteJob?,
    /** null se il percorso non è calcolato o non ci sono ancora prezzi. */
    val result: CommuteResult?,
)

data class RoutesUiState(
    val loading: Boolean = true,
    val places: List<Place> = emptyList(),
    val commutes: List<CommuteCard> = emptyList(),
    val hasApiKey: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RoutesViewModel @Inject constructor(
    private val repository: CommuteRepository,
    prefs: PreferencesRepository,
    prices: PriceRepository,
) : ViewModel() {

    private val settings = combine(prefs.carProfile, prefs.routeBufferMeters, prefs.searchPreferences, prices.datasetInfo) { car, buffer, search, info ->
        Settings(car, buffer.toDouble(), search.brands, info != null)
    }

    private data class Settings(
        val car: CarProfile,
        val buffer: Double,
        val brands: Set<BrandGroup>,
        val hasData: Boolean,
    )

    val uiState: StateFlow<RoutesUiState> = combine(repository.places, repository.commutes, repository.routeJobs, settings) { places, commutes, jobs, settings ->
        Snapshot(places, commutes, jobs, settings)
    }.mapLatest { (places, commutes, jobs, settings) ->
        RoutesUiState(
            loading = false,
            places = places,
            commutes = commutes.map { commute ->
                val result = if (settings.hasData) repository.advise(commute, settings.car, settings.buffer, settings.brands) else null
                CommuteCard(commute, jobs[commute.id], result)
            },
            hasApiKey = repository.hasApiKey,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutesUiState())

    private data class Snapshot(
        val places: List<Place>,
        val commutes: List<Commute>,
        val jobs: Map<Long, RouteJob>,
        val settings: Settings,
    )

    fun retryRoute(commuteId: Long) = repository.requestRoute(commuteId)
}
