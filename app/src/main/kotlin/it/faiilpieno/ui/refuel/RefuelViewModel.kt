package it.faiilpieno.ui.refuel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.faiilpieno.data.prefs.PreferencesRepository
import it.faiilpieno.data.repository.PriceRepository
import it.faiilpieno.data.repository.RefuelOutcome
import it.faiilpieno.data.repository.TankRepository
import it.faiilpieno.domain.model.CarProfile
import it.faiilpieno.domain.tank.TankEstimate
import it.faiilpieno.ui.format.Fmt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.roundToInt

data class RefuelForm(
    val loaded: Boolean = false,
    val car: CarProfile = CarProfile(),
    /** Marchio del distributore, se il rifornimento parte dal suo dettaglio. */
    val stationBrand: String? = null,
    val isFull: Boolean = true,
    val yesterday: Boolean = false,
    val quantity: String = "",
    val amount: String = "",
    val quantityError: Boolean = false,
    val amountError: Boolean = false,
    /** Parziale senza quantità né importo: non si saprebbe quanto è entrato. */
    val partialError: Boolean = false,
    val saving: Boolean = false,
)

data class RefuelSaved(
    val outcome: RefuelOutcome,
    val estimate: TankEstimate?,
    /** L'utente non ha ancora scelto se ricevere gli avvisi: è il momento di chiederlo. */
    val askAlerts: Boolean,
)

@HiltViewModel
class RefuelViewModel @Inject constructor(
    private val tank: TankRepository,
    private val prefs: PreferencesRepository,
    private val prices: PriceRepository,
) : ViewModel() {

    private var stationId: Long? = null

    private val _form = MutableStateFlow(RefuelForm())
    val form: StateFlow<RefuelForm> = _form.asStateFlow()

    private val _saved = MutableStateFlow<RefuelSaved?>(null)
    val saved: StateFlow<RefuelSaved?> = _saved.asStateFlow()

    /**
     * Chiamato all'apertura del foglio. Il ViewModel sopravvive alla chiusura: si riparte da zero
     * solo se è cambiato il distributore o se l'ultimo rifornimento è già stato salvato.
     */
    fun start(stationId: Long?) {
        if (_form.value.loaded && this.stationId == stationId && _saved.value == null) return
        this.stationId = stationId
        _saved.value = null
        viewModelScope.launch {
            val brand = stationId?.let { prices.stationDetail(it)?.station?.brand }
            _form.value = RefuelForm(loaded = true, car = prefs.carProfile.first(), stationBrand = brand)
        }
    }

    fun setFull(full: Boolean) = _form.update { it.copy(isFull = full, partialError = false) }

    fun setYesterday(yesterday: Boolean) = _form.update { it.copy(yesterday = yesterday) }

    fun setQuantity(text: String) = _form.update { it.copy(quantity = text, quantityError = false, partialError = false) }

    fun setAmount(text: String) = _form.update { it.copy(amount = text, amountError = false, partialError = false) }

    fun save() {
        val form = _form.value
        if (form.saving) return
        val quantity = parseOptional(form.quantity, 0.5..form.car.tankCapacity * MAX_OVER_TANK)
        val amount = parseOptional(form.amount, 0.5..MAX_AMOUNT_EUR)
        val partialError = !form.isFull && quantity.value == null && amount.value == null
        _form.update { it.copy(quantityError = quantity.invalid, amountError = amount.invalid, partialError = partialError) }
        if (quantity.invalid || amount.invalid || partialError) return

        _form.update { it.copy(saving = true) }
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val at = if (form.yesterday) LocalDate.now().minusDays(1).atTime(LocalTime.NOON).atZone(zone).toInstant() else Instant.now()
            val outcome = tank.addRefuel(
                at = at,
                liters = quantity.value,
                amountCents = amount.value?.let { (it * 100).roundToInt() },
                isFull = form.isFull,
                stationId = stationId,
            )
            _saved.value = RefuelSaved(outcome, tank.estimate.first(), askAlerts = prefs.reserveAlerts.first() == null)
            _form.update { it.copy(saving = false) }
        }
    }

    /** Risposta alla richiesta di avvisi (e al permesso di sistema, su Android 13+). */
    fun setAlerts(enabled: Boolean) {
        viewModelScope.launch { prefs.setReserveAlerts(enabled) }
        _saved.update { it?.copy(askAlerts = false) }
    }

    private data class Parsed(val value: Double?, val invalid: Boolean)

    /** Campo facoltativo: vuoto va bene, un numero fuori intervallo no. */
    private fun parseOptional(text: String, range: ClosedFloatingPointRange<Double>): Parsed {
        if (text.isBlank()) return Parsed(null, invalid = false)
        val value = Fmt.parseDecimal(text)?.takeIf { it in range }
        return Parsed(value, invalid = value == null)
    }

    private companion object {
        /** Qualche litro oltre la capienza dichiarata: bocchettone e tubo ne contengono un po'. */
        const val MAX_OVER_TANK = 1.15
        const val MAX_AMOUNT_EUR = 500.0
    }
}
