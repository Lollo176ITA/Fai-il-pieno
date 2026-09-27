package it.faiilpieno.domain.tank

import it.faiilpieno.domain.commute.Commute
import it.faiilpieno.domain.commute.DaysMask
import it.faiilpieno.domain.commute.Place
import it.faiilpieno.domain.commute.PlaceKind
import it.faiilpieno.domain.commute.Route
import it.faiilpieno.domain.model.CarProfile
import it.faiilpieno.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private val ZONE: ZoneId = ZoneId.of("Europe/Rome")

/** Lunedì 21 settembre 2026. */
private val MONDAY: LocalDate = LocalDate.of(2026, 9, 21)

private fun at(date: LocalDate, hour: Int = 8): Instant = date.atTime(LocalTime.of(hour, 0)).atZone(ZONE).toInstant()

private fun commute(km: Double, days: Set<DayOfWeek> = DaysMask.WEEKDAYS, roundTrip: Boolean = true, withRoute: Boolean = true): Commute {
    val place = Place(1, "Casa", PlaceKind.HOME, GeoPoint(45.0, 9.0), null)
    val route = if (withRoute) Route(km * 1000, 1200.0, listOf(GeoPoint(45.0, 9.0)), Instant.EPOCH) else null
    return Commute(1, place, place.copy(id = 2), days, roundTrip, route)
}

private fun full(date: LocalDate, liters: Double? = null, hour: Int = 8) = Refuel(0, at(date, hour), liters, null, isFull = true, stationId = null)

private fun partial(date: LocalDate, liters: Double?, hour: Int = 8) = Refuel(0, at(date, hour), liters, null, isFull = false, stationId = null)

class DailyDistanceTest {

    @Test
    fun `andata e ritorno nei giorni previsti`() {
        val commutes = listOf(commute(20.0), commute(5.0, days = setOf(DayOfWeek.MONDAY), roundTrip = false))
        assertEquals(45.0, DailyDistance.kmOn(MONDAY, commutes), 1e-9)
        assertEquals(40.0, DailyDistance.kmOn(MONDAY.plusDays(1), commutes), 1e-9)
        assertEquals(0.0, DailyDistance.kmOn(MONDAY.plusDays(5), commutes), 1e-9)
    }

    @Test
    fun `i tragitti senza percorso non contano`() {
        assertEquals(0.0, DailyDistance.kmOn(MONDAY, listOf(commute(20.0, withRoute = false))), 1e-9)
    }
}

class TankEstimatorTest {

    // 40 l, 5 l/100 km: 40 km al giorno (20 + 20) = 2 l al giorno nei feriali. Riserva a 5 l.
    private val car = CarProfile(tankCapacity = 40.0, consumptionPer100Km = 5.0)
    private val commutes = listOf(commute(20.0))

    @Test
    fun `senza un pieno non si stima`() {
        assertNull(TankEstimator.estimate(emptyList(), commutes, car, MONDAY, ZONE))
        assertNull(TankEstimator.estimate(listOf(partial(MONDAY, 20.0)), commutes, car, MONDAY, ZONE))
    }

    @Test
    fun `il giorno del pieno il serbatoio è pieno`() {
        val estimate = TankEstimator.estimate(listOf(full(MONDAY)), commutes, car, MONDAY, ZONE)!!
        assertEquals(40.0, estimate.levelLiters, 1e-9)
        // 35 l sopra la riserva, 2 l al giorno feriale: il 18° giorno feriale, mercoledì 14 ottobre,
        // si scende a 4 l.
        assertEquals(LocalDate.of(2026, 10, 14), estimate.reserveDate)
        assertEquals(700.0, estimate.kmToReserve, 1e-9)
    }

    @Test
    fun `si scalano i giorni passati, compreso quello del pieno`() {
        // Pieno lunedì, oggi giovedì: consumati lun, mar, mer = 6 l.
        val estimate = TankEstimator.estimate(listOf(full(MONDAY)), commutes, car, MONDAY.plusDays(3), ZONE)!!
        assertEquals(34.0, estimate.levelLiters, 1e-9)
        assertEquals(MONDAY, estimate.lastFullDate)
        assertEquals(120.0, estimate.kmSinceFull, 1e-9)
    }

    @Test
    fun `il fine settimana non consuma`() {
        // Pieno venerdì, oggi lunedì: solo venerdì consuma.
        val friday = MONDAY.plusDays(4)
        val estimate = TankEstimator.estimate(listOf(full(friday)), commutes, car, friday.plusDays(3), ZONE)!!
        assertEquals(38.0, estimate.levelLiters, 1e-9)
    }

    @Test
    fun `i parziali con i litri si aggiungono, senza superare il serbatoio`() {
        val refuels = listOf(
            full(MONDAY),
            partial(MONDAY.plusDays(2), 3.0),
            // Senza litri non si sa quanto è entrato: si ignora.
            partial(MONDAY.plusDays(3), null),
        )
        val estimate = TankEstimator.estimate(refuels, commutes, car, MONDAY.plusDays(4), ZONE)!!
        // 40 − 2 − 2 + 3 − 2 − 2 = 35
        assertEquals(35.0, estimate.levelLiters, 1e-9)

        val overflow = TankEstimator.estimate(listOf(full(MONDAY), partial(MONDAY, 30.0, hour = 18)), commutes, car, MONDAY, ZONE)!!
        assertEquals(40.0, overflow.levelLiters, 1e-9)
    }

    @Test
    fun `conta solo l'ultimo pieno`() {
        val refuels = listOf(full(MONDAY.minusDays(14)), full(MONDAY))
        val estimate = TankEstimator.estimate(refuels, commutes, car, MONDAY.plusDays(1), ZONE)!!
        assertEquals(38.0, estimate.levelLiters, 1e-9)
    }

    @Test
    fun `già in riserva`() {
        // 18 giorni feriali dopo il pieno: 36 l consumati, restano 4 l.
        val today = LocalDate.of(2026, 10, 15)
        val estimate = TankEstimator.estimate(listOf(full(MONDAY)), commutes, car, today, ZONE)!!
        assertEquals(4.0, estimate.levelLiters, 1e-9)
        assertEquals(today, estimate.reserveDate)
        assertEquals(0.0, estimate.kmToReserve, 1e-9)
    }

    @Test
    fun `senza tragitti la riserva non è prevedibile`() {
        val estimate = TankEstimator.estimate(listOf(full(MONDAY)), emptyList(), car, MONDAY.plusDays(3), ZONE)!!
        assertEquals(40.0, estimate.levelLiters, 1e-9)
        assertNull(estimate.reserveDate)
        assertEquals(700.0, estimate.kmToReserve, 1e-9)
    }
}

class ConsumptionCalibrationTest {

    private val car = CarProfile(tankCapacity = 40.0, consumptionPer100Km = 5.0)
    private val commutes = listOf(commute(20.0))

    @Test
    fun `da pieno a pieno`() {
        // Pieno lunedì, pieno il lunedì dopo: 5 giorni × 40 km = 200 km, 12 l → 6 l/100 km.
        val refuels = listOf(full(MONDAY), full(MONDAY.plusDays(7), liters = 12.0))
        val calibration = ConsumptionCalibration.compute(refuels, commutes, car, ZONE)!!
        assertEquals(6.0, calibration.consumptionPer100Km, 1e-9)
        assertEquals(200.0, calibration.km, 1e-9)
        assertEquals(12.0, calibration.liters, 1e-9)
    }

    @Test
    fun `i parziali in mezzo si sommano`() {
        val refuels = listOf(full(MONDAY), partial(MONDAY.plusDays(3), 4.0), full(MONDAY.plusDays(7), liters = 7.0))
        assertEquals(5.5, ConsumptionCalibration.compute(refuels, commutes, car, ZONE)!!.consumptionPer100Km, 1e-9)
    }

    @Test
    fun `non si calibra senza dati sufficienti`() {
        // Ultimo pieno senza litri.
        assertNull(ConsumptionCalibration.compute(listOf(full(MONDAY), full(MONDAY.plusDays(7))), commutes, car, ZONE))
        // Un parziale senza litri in mezzo.
        assertNull(
            ConsumptionCalibration.compute(
                listOf(full(MONDAY), partial(MONDAY.plusDays(2), null), full(MONDAY.plusDays(7), liters = 12.0)),
                commutes, car, ZONE,
            ),
        )
        // Un solo pieno.
        assertNull(ConsumptionCalibration.compute(listOf(full(MONDAY, liters = 30.0)), commutes, car, ZONE))
        // Meno di 50 km stimati (un solo giorno).
        assertNull(ConsumptionCalibration.compute(listOf(full(MONDAY), full(MONDAY.plusDays(1), liters = 2.0)), commutes, car, ZONE))
    }

    @Test
    fun `valori implausibili scartati`() {
        // 200 km con 30 l = 15 l/100 km: triplo del consumo attuale.
        assertNull(ConsumptionCalibration.compute(listOf(full(MONDAY), full(MONDAY.plusDays(7), liters = 30.0)), commutes, car, ZONE))
        // 200 km con 4 l = 2 l/100 km: meno della metà.
        assertNull(ConsumptionCalibration.compute(listOf(full(MONDAY), full(MONDAY.plusDays(7), liters = 4.0)), commutes, car, ZONE))
    }
}

class ReserveAlertTest {

    private val car = CarProfile(tankCapacity = 40.0, consumptionPer100Km = 5.0)
    private val commutes = listOf(commute(20.0))

    @Test
    fun `avviso entro due giorni dalla riserva`() {
        // Riserva mercoledì 14 ottobre (vedi TankEstimatorTest).
        val reserve = LocalDate.of(2026, 10, 14)
        val onMonday = TankEstimator.estimate(listOf(full(MONDAY)), commutes, car, reserve.minusDays(2), ZONE)!!
        assertEquals(reserve, onMonday.reserveDate)
        assertTrue(ReserveAlert.shouldAlert(onMonday, reserve.minusDays(2)))

        val onFriday = TankEstimator.estimate(listOf(full(MONDAY)), commutes, car, reserve.minusDays(5), ZONE)!!
        assertFalse(ReserveAlert.shouldAlert(onFriday, reserve.minusDays(5)))
        assertTrue(ReserveAlert.shouldAlert(onFriday, reserve.minusDays(5), withinDays = 5))
    }

    @Test
    fun `senza tragitti nessun avviso`() {
        val estimate = TankEstimator.estimate(listOf(full(MONDAY)), emptyList(), car, MONDAY, ZONE)!!
        assertFalse(ReserveAlert.shouldAlert(estimate, MONDAY))
    }

    @Test
    fun `la sosta è il primo giorno di tragitto`() {
        val saturday = MONDAY.plusDays(5)
        assertEquals(MONDAY.plusDays(7), ReserveAlert.stopDay(commutes, saturday, saturday.plusDays(3)))
        assertNull(ReserveAlert.stopDay(commutes, saturday, saturday.plusDays(1)))
    }
}
