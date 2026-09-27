package it.faiilpieno.ui.routes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import it.faiilpieno.data.location.LocationProvider
import it.faiilpieno.data.location.LocationResult
import it.faiilpieno.data.ors.AddressSuggestion
import it.faiilpieno.data.ors.OrsException
import it.faiilpieno.data.repository.CommuteRepository
import it.faiilpieno.domain.commute.Place
import it.faiilpieno.domain.commute.PlaceKind
import it.faiilpieno.domain.model.GeoPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Results(val suggestions: List<AddressSuggestion>) : SearchState
    data class Failed(val reason: OrsException.Reason) : SearchState
}

enum class LocateError { PERMISSION, UNAVAILABLE }

data class PlaceForm(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val kind: PlaceKind = PlaceKind.HOME,
    val name: String = "",
    val location: GeoPoint? = null,
    /** Indirizzo leggibile; null se la posizione viene dal GPS e non si è trovato un indirizzo. */
    val address: String? = null,
    val query: String = "",
    val search: SearchState = SearchState.Idle,
    val locating: Boolean = false,
    val locateError: LocateError? = null,
    val nameError: Boolean = false,
    val locationError: Boolean = false,
    /** Tragitti che verrebbero eliminati insieme al luogo. */
    val usedByCommutes: Int = 0,
)

sealed interface PlaceEvent {
    data object Saved : PlaceEvent
    data object Deleted : PlaceEvent
    /** Serve chiedere il permesso di posizione all'utente. */
    data object RequestPermission : PlaceEvent
}

@HiltViewModel
class PlaceEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CommuteRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<PlaceEditRoute>()

    private val _form = MutableStateFlow(PlaceForm())
    val form: StateFlow<PlaceForm> = _form.asStateFlow()

    private val eventChannel = Channel<PlaceEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private var searchJob: Job? = null

    /** Le ricerche preferiscono i risultati vicini ai luoghi già salvati. */
    private var focus: GeoPoint? = null

    init {
        viewModelScope.launch {
            focus = repository.places.first().firstOrNull()?.location
            // Senza luoghi salvati, la posizione attuale (se già concessa) aiuta a scegliere la città giusta.
            if (focus == null && locationProvider.hasPermission()) {
                launch { focus = (locationProvider.current() as? LocationResult.Found)?.point }
            }
            val existing = args.placeId.takeIf { it != 0L }?.let { repository.place(it) }
            _form.value = if (existing != null) {
                PlaceForm(
                    loaded = true,
                    isNew = false,
                    kind = existing.kind,
                    name = existing.label,
                    location = existing.location,
                    address = existing.address,
                    usedByCommutes = repository.commutesUsing(existing.id),
                )
            } else {
                val kind = args.kind?.let { name -> PlaceKind.entries.firstOrNull { it.name == name } } ?: PlaceKind.HOME
                PlaceForm(loaded = true, kind = kind)
            }
        }
    }

    /**
     * Cambiando tipo si propone il nome predefinito ("Casa", "Lavoro"…), ma solo se l'utente
     * non ne ha scritto uno suo.
     */
    fun setKind(kind: PlaceKind, defaultNames: Map<PlaceKind, String>) = _form.update { form ->
        val nameIsDefault = form.name.isBlank() || form.name == defaultNames[form.kind]
        form.copy(kind = kind, name = if (nameIsDefault) defaultNames[kind].orEmpty() else form.name, nameError = false)
    }

    fun setName(name: String) = _form.update { it.copy(name = name, nameError = false) }

    fun setQuery(query: String) {
        _form.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.trim().length < MIN_QUERY) {
            _form.update { it.copy(search = SearchState.Idle) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            _form.update { it.copy(search = SearchState.Loading) }
            val state = try {
                SearchState.Results(repository.searchAddress(query.trim(), focus))
            } catch (e: OrsException) {
                SearchState.Failed(e.reason)
            }
            _form.update { it.copy(search = state) }
        }
    }

    fun choose(suggestion: AddressSuggestion) {
        searchJob?.cancel()
        _form.update {
            it.copy(
                location = suggestion.point,
                address = suggestion.label,
                query = "",
                search = SearchState.Idle,
                locationError = false,
                locateError = null,
            )
        }
    }

    fun useCurrentLocation() {
        if (!locationProvider.hasPermission()) {
            viewModelScope.launch { eventChannel.send(PlaceEvent.RequestPermission) }
            return
        }
        locate()
    }

    /** Dopo la richiesta di permesso. */
    fun onPermissionResult() {
        if (locationProvider.hasPermission()) locate() else _form.update { it.copy(locateError = LocateError.PERMISSION) }
    }

    private fun locate() {
        _form.update { it.copy(locating = true, locateError = null) }
        viewModelScope.launch {
            when (val result = locationProvider.current()) {
                is LocationResult.Found -> {
                    // L'indirizzo è solo descrittivo: se il servizio non risponde si tiene la posizione.
                    val address = runCatching { repository.addressOf(result.point) }.getOrNull()
                    _form.update {
                        it.copy(locating = false, location = result.point, address = address, locationError = false, search = SearchState.Idle, query = "")
                    }
                }
                LocationResult.PermissionDenied -> _form.update { it.copy(locating = false, locateError = LocateError.PERMISSION) }
                LocationResult.ServicesOff, LocationResult.Unavailable ->
                    _form.update { it.copy(locating = false, locateError = LocateError.UNAVAILABLE) }
            }
        }
    }

    fun save() {
        val form = _form.value
        val name = form.name.trim()
        val location = form.location
        _form.update { it.copy(nameError = name.isEmpty(), locationError = location == null) }
        if (name.isEmpty() || location == null) return
        viewModelScope.launch {
            repository.savePlace(Place(args.placeId, name, form.kind, location, form.address))
            eventChannel.send(PlaceEvent.Saved)
        }
    }

    fun delete() {
        if (args.placeId == 0L) return
        viewModelScope.launch {
            repository.deletePlace(args.placeId)
            eventChannel.send(PlaceEvent.Deleted)
        }
    }

    private companion object {
        const val MIN_QUERY = 3
        /** Si cerca quando l'utente smette di scrivere: meno richieste alla quota giornaliera. */
        const val DEBOUNCE_MS = 600L
    }
}
