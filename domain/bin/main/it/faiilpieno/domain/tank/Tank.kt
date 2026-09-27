package it.faiilpieno.domain.tank

import it.faiilpieno.domain.commute.Commute
import it.faiilpieno.domain.model.CarProfile
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Un rifornimento registrato dall'utente. [liters] può mancare: un pieno senza litri basta per
 * ripartire con il serbatoio pieno, ma non per calibrare il consumo.
 */
data class Refuel(
    val id: Long,
    val at: Instant,
    val liters: Double?,
    val amountCents: Int?,
    val isFull: Boolean,
    val stationId: Long?,
)

/** Km percorsi in un giorno con i tragitti abituali. */
object DailyDistance {
    fun kmOn(date: LocalDate, commutes: List<Commute>): Double = commutes.sumOf { commute ->
        val route = commute.route
        if (route == null || date.dayOfWeek !in commute.days) 0.0
        else route.distanceMeters / 1000.0 * (if (commute.roundTrip) 2 else 1)
    }
}

data class TankEstimate(
    /** Carburante stimato all'inizio di oggi, in litri (kg per il metano). */
    val levelLiters: Double,
    val capacity: Double,
    /** Livello a cui si accende la riserva. */
    val reserveLiters: Double,
    /** Primo giorno in cui si scende in riserva; oggi se ci si è già. null se non si consuma mai. */
    val reserveDate: LocalDate?,
    /** Km percorribili prima della riserva con il consumo del profilo. */
    val kmToReserve: Double,
    val lastFullDate: LocalDate,
    /** Km stimati dall'ultimo pieno a ieri. */
    val kmSinceFull: Double,
) {
    val fraction: Double get() = (levelLiters / capacity).coerceIn(0.0, 1.0)

    fun daysToReserve(today: LocalDate): Long? = reserveDate?.let { ChronoUnit.DAYS.between(today, it) }
}

/**
 * Stima il carburante senza localizzazione in background: dall'ultimo pieno si scalano, giorno per
 * giorno, i km dei tragitti abituali previsti in quel giorno. Il consumo di un giorno si conta a
 * fine giornata: il giorno del pieno conta, oggi no. Chi guida anche fuori dai tragitti consuma
 * di più: la calibrazione (litri reali ÷ km stimati) lo assorbe nel consumo del profilo.
 */
object TankEstimator {

    /** La spia della riserva di solito si accende con circa 1/8 di serbatoio. */
    const val RESERVE_RATIO = 0.125

    /** Oltre questo orizzonte non si cerca la data della riserva. */
    private const val HORIZON_DAYS = 120L

    fun estimate(refuels: List<Refuel>, commutes: List<Commute>, car: CarProfile, today: LocalDate, zone: ZoneId): TankEstimate? {
        val sorted = refuels.sortedBy { it.at }
        val lastFull = sorted.lastOrNull { it.isFull } ?: return null
        val fullDate = lastFull.at.atZone(zone).toLocalDate()
        val partialsByDay = sorted
            .filter { !it.isFull && it.at > lastFull.at && it.liters != null }
            .groupBy { it.at.atZone(zone).toLocalDate() }
        val perKm = car.consumptionPer100Km / 100.0

        var level = car.tankCapacity
        var kmSinceFull = 0.0
        var day = fullDate
        while (day.isBefore(today)) {
            level = addPartials(level, partialsByDay[day], car.tankCapacity)
            val km = DailyDistance.kmOn(day, commutes)
            kmSinceFull += km
            level = (level - km * perKm).coerceAtLeast(0.0)
            day = day.plusDays(1)
        }
        // I parziali di oggi (o del giorno del pieno, se è oggi) sono già nel serbatoio.
        level = addPartials(level, partialsByDay[today], car.tankCapacity)

        val reserve = car.tankCapacity * RESERVE_RATIO
        return TankEstimate(
            levelLiters = level,
            capacity = car.tankCapacity,
            reserveLiters = reserve,
            reserveDate = reserveDate(level, reserve, commutes, perKm, today),
            kmToReserve = if (perKm > 0) ((level - reserve) / perKm).coerceAtLeast(0.0) else 0.0,
            lastFullDate = fullDate,
            kmSinceFull = kmSinceFull,
        )
    }

    private fun addPartials(level: Double, partials: List<Refuel>?, capacity: Double): Double =
        (level + partials.orEmpty().sumOf { it.liters ?: 0.0 }).coerceAtMost(capacity)

    private fun reserveDate(level: Double, reserve: Double, commutes: List<Commute>, perKm: Double, today: LocalDate): LocalDate? {
        if (level <= reserve) return today
        var remaining = level
        for (offset in 0 until HORIZON_DAYS) {
            val day = today.plusDays(offset)
            remaining -= DailyDistance.kmOn(day, commutes) * perKm
            if (remaining <= reserve) return day
        }
        return null
    }
}

data class Calibration(val consumptionPer100Km: Double, val km: Double, val liters: Double)

/**
 * Consumo reale con il metodo "da pieno a pieno": i litri messi dopo il pieno precedente
 * (parziali compresi) divisi per i km stimati nel frattempo.
 */
object ConsumptionCalibration {

    /** Sotto questi km la stima è troppo incerta. */
    const val MIN_KM = 50.0

    /** Un valore meno della metà o più del doppio di quello attuale è quasi certamente un errore. */
    private const val MAX_FACTOR = 2.0

    /** Calibrazione sull'ultimo pieno, o null se i dati non bastano o il risultato è implausibile. */
    fun compute(refuels: List<Refuel>, commutes: List<Commute>, car: CarProfile, zone: ZoneId): Calibration? {
        val sorted = refuels.sortedBy { it.at }
        val fulls = sorted.filter { it.isFull }
        if (fulls.size < 2) return null
        val last = fulls.last()
        val previous = fulls[fulls.lastIndex - 1]
        val lastLiters = last.liters ?: return null

        val partials = sorted.filter { !it.isFull && it.at > previous.at && it.at < last.at }
        if (partials.any { it.liters == null }) return null
        val liters = lastLiters + partials.sumOf { it.liters!! }

        val start = previous.at.atZone(zone).toLocalDate()
        val end = last.at.atZone(zone).toLocalDate()
        val km = generateSequence(start) { it.plusDays(1) }.takeWhile { it.isBefore(end) }.sumOf { DailyDistance.kmOn(it, commutes) }
        if (km < MIN_KM) return null

        val per100 = liters / km * 100
        val current = car.consumptionPer100Km
        if (per100 < current / MAX_FACTOR || per100 > current * MAX_FACTOR) return null
        return Calibration(per100, km, liters)
    }
}

/** Quando avvisare che la riserva si avvicina e in quale giorno proporre la sosta. */
object ReserveAlert {

    /** Si avvisa quando alla riserva mancano al massimo questi giorni. */
    const val ALERT_DAYS = 2L

    /** Nella scheda Oggi l'avviso compare un po' prima. */
    const val BANNER_DAYS = 3L

    fun shouldAlert(estimate: TankEstimate, today: LocalDate, withinDays: Long = ALERT_DAYS): Boolean {
        val days = estimate.daysToReserve(today) ?: return false
        return days <= withinDays
    }

    /**
     * Primo giorno da [from] a [until] (compresi) in cui si fa un tragitto: è lì che conviene
     * fermarsi. null se in quel periodo non ci sono tragitti.
     */
    fun stopDay(commutes: List<Commute>, from: LocalDate, until: LocalDate): LocalDate? =
        generateSequence(from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(until) }
            .firstOrNull { DailyDistance.kmOn(it, commutes) > 0 }
}
