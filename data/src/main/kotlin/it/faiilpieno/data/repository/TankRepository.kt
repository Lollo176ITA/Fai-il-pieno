package it.faiilpieno.data.repository

import it.faiilpieno.data.db.RefuelDao
import it.faiilpieno.data.db.RefuelEntity
import it.faiilpieno.data.db.toDomain
import it.faiilpieno.data.prefs.PreferencesRepository
import it.faiilpieno.domain.tank.Calibration
import it.faiilpieno.domain.tank.ConsumptionCalibration
import it.faiilpieno.domain.tank.Refuel
import it.faiilpieno.domain.tank.TankEstimate
import it.faiilpieno.domain.tank.TankEstimator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Esito del salvataggio di un rifornimento. */
data class RefuelOutcome(
    /** Nuovo consumo, se i litri hanno permesso di calibrarlo. */
    val calibration: Calibration?,
    /** Consumo del profilo prima della calibrazione. */
    val previousConsumption: Double,
    /** Litri ricavati dall'importo, quando l'utente ha scritto solo quello. */
    val litersFromAmount: Double?,
)

@Singleton
class TankRepository @Inject constructor(
    private val dao: RefuelDao,
    private val prefs: PreferencesRepository,
    private val prices: PriceRepository,
    private val commutes: CommuteRepository,
) {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    val refuels: Flow<List<Refuel>> = dao.observeAll().map { rows -> rows.map(RefuelEntity::toDomain) }

    /** Stima all'inizio di oggi; null finché non c'è un pieno registrato. */
    val estimate: Flow<TankEstimate?> = combine(refuels, commutes.commutes, prefs.carProfile) { refuels, commutes, car ->
        TankEstimator.estimate(refuels, commutes, car, LocalDate.now(), zone)
    }

    /** Stima all'inizio del giorno [day]: per il controllo serale si usa domani. */
    suspend fun estimateOn(day: LocalDate): TankEstimate? =
        TankEstimator.estimate(dao.all().map(RefuelEntity::toDomain), commutes.commutes.first(), prefs.carProfile.first(), day, zone)

    /**
     * Registra un rifornimento. Se c'è solo l'importo, i litri si ricavano dal prezzo del
     * distributore o, in mancanza, dalla media nazionale. Dopo un pieno con i litri il consumo
     * del profilo si ricalcola (metodo da pieno a pieno).
     */
    suspend fun addRefuel(at: Instant, liters: Double?, amountCents: Int?, isFull: Boolean, stationId: Long?): RefuelOutcome {
        val car = prefs.carProfile.first()
        val litersFromAmount = if (liters == null && amountCents != null) {
            val priceMilli = stationId?.let { prices.priceAt(it, car.fuel, car.serviceMode) }
                ?: prices.nationalAverage(car.fuel, car.serviceMode)
            priceMilli?.takeIf { it > 0 }?.let { amountCents / 100.0 / (it / 1000.0) }
        } else {
            null
        }
        dao.insert(RefuelEntity(at = at, liters = liters ?: litersFromAmount, amountCents = amountCents, isFull = isFull, stationId = stationId))

        val calibration = if (isFull) {
            ConsumptionCalibration.compute(dao.all().map(RefuelEntity::toDomain), commutes.commutes.first(), car, zone)
        } else {
            null
        }
        calibration?.let { prefs.setConsumption(it.consumptionPer100Km) }
        return RefuelOutcome(calibration, car.consumptionPer100Km, litersFromAmount)
    }

    /** Annulla l'ultimo rifornimento registrato (per esempio inserito per errore). */
    suspend fun deleteLast() {
        dao.all().maxByOrNull { it.at }?.let { dao.delete(it.id) }
    }
}
