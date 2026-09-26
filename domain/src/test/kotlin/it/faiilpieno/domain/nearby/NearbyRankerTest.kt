package it.faiilpieno.domain.nearby

import it.faiilpieno.domain.brand.BrandGroup
import it.faiilpieno.domain.model.CarProfile
import it.faiilpieno.domain.model.FuelCategory
import it.faiilpieno.domain.model.FuelPrice
import it.faiilpieno.domain.model.Station
import it.faiilpieno.domain.model.StationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class NearbyRankerTest {

    // Serbatoio da 50 l → rifornimento tipico 40 l; 5 l/100 km.
    private val car = CarProfile(tankCapacity = 50.0, consumptionPer100Km = 5.0)

    private fun offer(id: Long, brand: String, priceMilli: Int, meters: Double) = Offer(
        station = Station(id, "", brand, brand, StationType.STRADALE, "S$id", "", "", "", 45.0, 9.0),
        price = FuelPrice(id, "Benzina", FuelCategory.BENZINA, true, true, priceMilli, Instant.EPOCH),
        distanceMeters = meters,
    )

    private val offers = listOf(
        offer(1, "Eni", 2000, 500.0),
        offer(2, "Pompe bianche", 1900, 3_000.0),
        offer(3, "Q8", 1950, 1_000.0),
    )

    @Test
    fun `ordina per prezzo o per distanza`() {
        assertEquals(listOf(2L, 3L, 1L), NearbyRanker.rank(offers, 1950, car, SortMode.PRICE).offers.map { it.offer.station.id })
        assertEquals(listOf(1L, 3L, 2L), NearbyRanker.rank(offers, 1950, car, SortMode.DISTANCE).offers.map { it.offer.station.id })
    }

    @Test
    fun `media di zona e delta nazionale`() {
        val result = NearbyRanker.rank(offers, 1950, car, SortMode.PRICE)
        assertEquals(1950, result.zoneAverageMilli)
        assertEquals(-5, result.offers.first().nationalDeltaCents)
        assertEquals(40.0, result.quantity, 1e-9)
    }

    @Test
    fun `consiglia il miglior risparmio netto considerando la deviazione`() {
        // id 2: (1,950 − 1,900) × 40 = 2,00 € − 5 km × 0,05 × 1,9 = 1,525 €
        // id 3: 0 € − 1 km × 0,05 × 1,95 < 0
        val result = NearbyRanker.rank(offers, null, car, SortMode.DISTANCE)
        assertEquals(2L, result.recommended?.offer?.station?.id)
        assertEquals(5.0, result.recommended!!.detourKm, 1e-9)
        assertEquals(1.525, result.recommended.netSavingEur, 1e-9)
    }

    @Test
    fun `nessun consiglio se non conviene`() {
        val same = listOf(offer(1, "Eni", 2000, 500.0), offer(2, "Q8", 2000, 900.0))
        assertNull(NearbyRanker.rank(same, null, car, SortMode.PRICE).recommended)
    }

    @Test
    fun `il filtro marchi non cambia la media di zona`() {
        val result = NearbyRanker.rank(offers, null, car, SortMode.PRICE, setOf(BrandGroup.ENI, BrandGroup.Q8))
        assertEquals(listOf(3L, 1L), result.offers.map { it.offer.station.id })
        assertEquals(1950, result.zoneAverageMilli)
        assertNull(result.recommended)
    }

    @Test
    fun `lista vuota`() {
        val result = NearbyRanker.rank(emptyList(), 1950, car, SortMode.PRICE)
        assertEquals(0, result.offers.size)
        assertNull(result.zoneAverageMilli)
    }
}
