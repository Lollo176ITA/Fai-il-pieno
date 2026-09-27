package it.faiilpieno.ui.routes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import it.faiilpieno.data.repository.CommuteRepository
import it.faiilpieno.domain.commute.DaysMask
import it.faiilpieno.domain.commute.Place
import it.faiilpieno.domain.commute.PlaceKind
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject

data class CommuteForm(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val places: List<Place> = emptyList(),
    val fromId: Long? = null,
    val toId: Long? = null,
    val days: Set<DayOfWeek> = DaysMask.WEEKDAYS,
    val roundTrip: Boolean = true,
    val samePlaceError: Boolean = false,
    val noDaysError: Boolean = false,
)

sealed interface CommuteEditEvent {
    data class Saved(val commuteId: Long, val isNew: Boolean) : CommuteEditEvent
    data object Deleted : CommuteEditEvent
}

@HiltViewModel
class CommuteEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CommuteRepository,
) : ViewModel() {

    private val commuteId = savedStateHandle.toRoute<CommuteEditRoute>().commuteId

    private val _form = MutableStateFlow(CommuteForm())
    val form: StateFlow<CommuteForm> = _form.asStateFlow()

    private val eventChannel = Channel<CommuteEditEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val places = repository.places.first()
            val existing = commuteId.takeIf { it != 0L }?.let { id -> repository.commute(id).first() }
            _form.value = if (existing != null) {
                CommuteForm(
                    loaded = true,
                    isNew = false,
                    places = places,
                    fromId = existing.from.id,
                    toId = existing.to.id,
                    days = existing.days,
                    roundTrip = existing.roundTrip,
                )
            } else {
                // Proposta più comune: da casa al lavoro (o all'università).
                val from = places.firstOrNull { it.kind == PlaceKind.HOME } ?: places.firstOrNull()
                val to = places.firstOrNull { it.kind == PlaceKind.WORK || it.kind == PlaceKind.UNIVERSITY }?.takeIf { it != from }
                    ?: places.firstOrNull { it != from }
                CommuteForm(loaded = true, places = places, fromId = from?.id, toId = to?.id)
            }
        }
    }

    fun setFrom(id: Long) = _form.update { it.copy(fromId = id, samePlaceError = false) }

    fun setTo(id: Long) = _form.update { it.copy(toId = id, samePlaceError = false) }

    fun toggleDay(day: DayOfWeek) = _form.update {
        it.copy(days = if (day in it.days) it.days - day else it.days + day, noDaysError = false)
    }

    fun setRoundTrip(value: Boolean) = _form.update { it.copy(roundTrip = value) }

    fun save() {
        val form = _form.value
        val from = form.fromId
        val to = form.toId
        val samePlace = from == null || to == null || from == to
        _form.update { it.copy(samePlaceError = samePlace, noDaysError = form.days.isEmpty()) }
        if (samePlace || form.days.isEmpty()) return
        viewModelScope.launch {
            val id = repository.saveCommute(commuteId, from!!, to!!, form.days, form.roundTrip)
            eventChannel.send(CommuteEditEvent.Saved(id, isNew = commuteId == 0L))
        }
    }

    fun delete() {
        if (commuteId == 0L) return
        viewModelScope.launch {
            repository.deleteCommute(commuteId)
            eventChannel.send(CommuteEditEvent.Deleted)
        }
    }
}
