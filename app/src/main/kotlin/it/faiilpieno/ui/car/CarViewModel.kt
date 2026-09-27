package it.faiilpieno.ui.car

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.faiilpieno.data.prefs.PreferencesRepository
import it.faiilpieno.data.repository.PriceRepository
import it.faiilpieno.data.repository.TankRepository
import it.faiilpieno.data.work.DataSync
import it.faiilpieno.data.work.SyncStatus
import it.faiilpieno.domain.model.CarProfile
import it.faiilpieno.domain.model.ConsumptionUnit
import it.faiilpieno.domain.model.DatasetInfo
import it.faiilpieno.domain.model.FuelCategory
import it.faiilpieno.domain.model.ServiceMode
import it.faiilpieno.domain.tank.TankEstimate
import it.faiilpieno.ui.format.Fmt
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CarForm(
    val fuel: FuelCategory = FuelCategory.BENZINA,
    val tank: String = "",
    val consumption: String = "",
    val unit: ConsumptionUnit = ConsumptionUnit.KM_PER_UNIT,
    val serviceMode: ServiceMode = ServiceMode.SELF,
    val tankError: Boolean = false,
    val consumptionError: Boolean = false,
    val loaded: Boolean = false,
)

data class TankState(
    val loaded: Boolean = false,
    val estimate: TankEstimate? = null,
    val hasRefuels: Boolean = false,
    /** null finché l'utente non ha scelto. */
    val alerts: Boolean? = null,
)

data class DataInfoState(val dataset: DatasetInfo? = null, val sync: SyncStatus = SyncStatus.Idle)

@HiltViewModel
class CarViewModel @Inject constructor(
    private val prefs: PreferencesRepository,
    prices: PriceRepository,
    private val dataSync: DataSync,
    private val tankRepository: TankRepository,
) : ViewModel() {

    private val _form = MutableStateFlow(CarForm())
    val form: StateFlow<CarForm> = _form.asStateFlow()

    private val savedEvents = Channel<Unit>(Channel.CONFLATED)
    val saved = savedEvents.receiveAsFlow()

    val dataInfo: StateFlow<DataInfoState> = combine(prices.datasetInfo, dataSync.status, ::DataInfoState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DataInfoState())

    val tank: StateFlow<TankState> = combine(tankRepository.estimate, tankRepository.refuels, prefs.reserveAlerts) { estimate, refuels, alerts ->
        TankState(loaded = true, estimate = estimate, hasRefuels = refuels.isNotEmpty(), alerts = alerts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TankState())

    init {
        viewModelScope.launch {
            val profile = prefs.carProfile.first()
            _form.value = CarForm(
                fuel = profile.fuel,
                tank = Fmt.editable(profile.tankCapacity),
                consumption = Fmt.editable(toDisplay(profile.consumptionPer100Km, profile.consumptionUnit)),
                unit = profile.consumptionUnit,
                serviceMode = profile.serviceMode,
                loaded = true,
            )
            // La calibrazione dai rifornimenti cambia il consumo: il campo si aggiorna, altrimenti
            // "Salva" riscriverebbe il valore vecchio.
            var lastConsumption = profile.consumptionPer100Km
            prefs.carProfile.collect { updated ->
                if (updated.consumptionPer100Km != lastConsumption) {
                    lastConsumption = updated.consumptionPer100Km
                    _form.update { form ->
                        form.copy(consumption = Fmt.editable(toDisplay(updated.consumptionPer100Km, form.unit)), consumptionError = false)
                    }
                }
            }
        }
    }

    fun setAlerts(enabled: Boolean) = viewModelScope.launch { prefs.setReserveAlerts(enabled) }

    fun undoLastRefuel() = viewModelScope.launch { tankRepository.deleteLast() }

    fun setFuel(fuel: FuelCategory) = _form.update { it.copy(fuel = fuel) }

    fun setServiceMode(mode: ServiceMode) = _form.update { it.copy(serviceMode = mode) }

    fun setTank(text: String) = _form.update { it.copy(tank = text, tankError = false) }

    fun setConsumption(text: String) = _form.update { it.copy(consumption = text, consumptionError = false) }

    /** Cambiando unità si converte il valore già scritto, così l'utente non deve ricalcolarlo. */
    fun setUnit(unit: ConsumptionUnit) = _form.update { form ->
        if (unit == form.unit) return@update form
        val converted = Fmt.parseDecimal(form.consumption)?.takeIf { it > 0 }?.let { 100.0 / it }
        form.copy(unit = unit, consumption = converted?.let(Fmt::editable) ?: form.consumption, consumptionError = false)
    }

    fun save() {
        val form = _form.value
        val tank = Fmt.parseDecimal(form.tank)?.takeIf { it in TANK_RANGE }
        val per100 = Fmt.parseDecimal(form.consumption)?.takeIf { it > 0 }
            ?.let { if (form.unit == ConsumptionUnit.KM_PER_UNIT) 100.0 / it else it }
            ?.takeIf { it in PER_100_RANGE }
        _form.update { it.copy(tankError = tank == null, consumptionError = per100 == null) }
        if (tank == null || per100 == null) return
        viewModelScope.launch {
            prefs.saveCarProfile(
                CarProfile(
                    fuel = form.fuel,
                    tankCapacity = tank,
                    consumptionPer100Km = per100,
                    consumptionUnit = form.unit,
                    serviceMode = form.serviceMode,
                    isConfigured = true,
                ),
            )
            savedEvents.send(Unit)
        }
    }

    fun refreshData() = dataSync.refreshNow(force = false)

    private fun toDisplay(per100: Double, unit: ConsumptionUnit) =
        if (unit == ConsumptionUnit.KM_PER_UNIT) CarProfile.per100KmToKmPerUnit(per100) else per100

    private companion object {
        val TANK_RANGE = 5.0..200.0
        /** Da 1 a 40 l/100 km (cioè da 2,5 a 100 km/l): fuori è quasi certamente un errore. */
        val PER_100_RANGE = 1.0..40.0
    }
}
