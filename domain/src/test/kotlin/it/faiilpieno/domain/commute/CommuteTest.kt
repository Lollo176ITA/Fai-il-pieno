package it.faiilpieno.domain.commute

import it.faiilpieno.domain.brand.BrandGroup
import it.faiilpieno.domain.geo.RoutePosition
import it.faiilpieno.domain.model.CarProfile
import it.faiilpieno.domain.model.FuelCategory
import it.faiilpieno.domain.model.FuelPrice
import it.faiilpieno.domain.model.Station
import it.faiilpieno.domain.model.StationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.Instant
import java.time.LocalDate

class DaysMaskTest {

    @Test
    fun `codifica e decodifica`() {
        val days = setOf(MONDAY, WEDNESDAY, FRIDAY)
        assertEquals(0b0010101, DaysMask.encode(days))
        assertEquals(days, DaysMask.decode(0b0010101))
    }

    @Test
    fun `tutta la settimana e nessun giorno`() {
        assertEquals(127, DaysMask.encode(java.time.DayOfWeek.entries.toSet()))
        assertEquals(0, DaysMask.encode(emptySet()))
        assertTrue(DaysMask.decode(0).isEmpty())
        assertEquals(5, DaysMask.WEEKDAYS.size)
        assertFalse(SUNDAY in DaysMask.WEEKDAYS)
    }
}

class CommuteScheduleTest {

    // Sabato 26 settembre 2026.
    private val saturday = LocalDate.of(2026, 9, 26)

    @Test
    fun `oggi se il giorno è previsto`() {
        assertEquals(saturday, CommuteSchedule.nextOccurrence(setOf(SATURDAY), saturday))
    }

    @Test
    fun `il prossimo lunedì per i giorni feriali`() {
        assertEquals(LocalDate.of(2026, 9, 28), CommuteSchedule.nextOccurrence(DaysMask.WEEKDAYS, saturday))
    }

    @Test
    fun `nessun giorno impostato`() {
        assertNull(CommuteSchedule.nextOccurrence(emptySet(), saturday))
    }
}

class CommuteRankerTest {

    private val car = CarProfile(fuel = FuelCategory.GASOLIO, tankCapacity = 50.0, consumptionPer100Km = 5.0)

    private fun offer(id: Long, priceMilli: Int, fromRouteM: Double, alongM: Double, brand: String = "Marchio $id") = RouteOffer(
        station = Station(id, "", "", brand, StationType.STRADALE, "", "", "", "", 45.0, 9.0),
        price = FuelPrice(id, "Gasolio", FuelCategory.GASOLIO, true, true, priceMilli, Instant.EPOCH),
        position = RoutePosition(fromRouteM, alongM),
    )

    @Test
    fun `consiglia il risparmio netto più alto`() {
        val offers = listOf(
            offer(1, 1_900, 50.0, 1_000.0),
            offer(2, 1_800, 400.0, 5_000.0),
            offer(3, 2_000, 10.0, 3_000.0),
        )
        val result = CommuteRanker.rank(offers, nationalAverageMilli = 1_950, car = car, sort = CommuteSort.PRICE)

        assertEquals(1_900, result.referenceMilli)
        assertFalse(result.usesNationalAverage)
        // (1,900 − 1,800) × 40 l − 0,8 km × 0,05 l/km × 1,800 € = 4 − 0,072
        assertEquals(4.0 - 0.072, result.recommended!!.netSavingEur, 1e-9)
        assertEquals(2L, result.recommended!!.offer.station.id)
        assertEquals(0.8, result.recommended!!.detourKm, 1e-9)
        assertEquals(listOf(2L, 1L, 3L), result.offers.map { it.offer.station.id })
        assertEquals(-15, result.recommended!!.nationalDeltaCents)
    }

    @Test
    fun `ordine lungo il percorso`() {
        val offers = listOf(offer(1, 1_900, 50.0, 9_000.0), offer(2, 1_800, 400.0, 500.0), offer(3, 2_000, 10.0, 3_000.0))
        val result = CommuteRanker.rank(offers, 1_950, car, CommuteSort.ALONG_ROUTE)
        assertEquals(listOf(2L, 3L, 1L), result.offers.map { it.offer.station.id })
    }

    @Test
    fun `con pochi distributori il riferimento è la media nazionale`() {
        val result = CommuteRanker.rank(listOf(offer(1, 1_850, 100.0, 0.0)), 1_950, car, CommuteSort.PRICE)
        assertTrue(result.usesNationalAverage)
        assertEquals(1_950, result.referenceMilli)
        assertEquals(1L, result.recommended?.offer?.station?.id)
    }

    @Test
    fun `nessun consiglio se la deviazione mangia il risparmio`() {
        val offers = listOf(
            offer(1, 1_900, 10.0, 0.0),
            offer(2, 1_901, 10.0, 0.0),
            // Solo 2 cent in meno ma 2 km fuori strada (4 km di deviazione).
            offer(3, 1_899, 2_000.0, 0.0),
        )
        val result = CommuteRanker.rank(offers, 1_950, car, CommuteSort.PRICE)
        assertNull(result.recommended?.takeIf { it.offer.station.id == 3L })
        result.recommended?.let { assertTrue(it.netSavingEur > 0) }
    }

    @Test
    fun `senza distributori e senza media nazionale`() {
        val result = CommuteRanker.rank(emptyList(), null, car, CommuteSort.PRICE)
        assertTrue(result.offers.isEmpty())
        assertNull(result.recommended)
        assertNull(result.referenceMilli)
        assertFalse(result.usesNationalAverage)
    }

    @Test
    fun `il filtro marchi non cambia la media del percorso`() {
        val eni = BrandGroup.entries.first { it.canonical != null }
        val offers = listOf(
            offer(1, 1_800, 10.0, 0.0, brand = "Senza marchio"),
            offer(2, 1_880, 10.0, 0.0, brand = eni.canonical!!),
            offer(3, 2_020, 10.0, 0.0, brand = "Senza marchio"),
        )
        val result = CommuteRanker.rank(offers, 1_950, car, CommuteSort.PRICE, brandFilter = setOf(eni))
        assertEquals(1_900, result.referenceMilli)
        assertEquals(listOf(2L), result.offers.map { it.offer.station.id })
        assertEquals(2L, result.recommended?.offer?.station?.id)
    }
}
