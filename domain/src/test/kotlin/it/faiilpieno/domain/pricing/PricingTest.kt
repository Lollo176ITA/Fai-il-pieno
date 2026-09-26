package it.faiilpieno.domain.pricing

import it.faiilpieno.domain.model.FuelCategory
import it.faiilpieno.domain.model.FuelPrice
import it.faiilpieno.domain.parser.MimitCsvParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

class PricingTest {

    @Test
    fun `risparmio netto senza deviazione`() {
        // (2,000 − 1,900) × 40 l = 4,00 €
        assertEquals(4.0, NetSavings.compute(2000, 1900, 40.0, 0.0, 6.0), 1e-9)
    }

    @Test
    fun `la deviazione riduce il risparmio`() {
        // 4,00 € − 10 km × 0,06 l/km × 1,900 €/l = 4,00 − 1,14 = 2,86 €
        assertEquals(2.86, NetSavings.compute(2000, 1900, 40.0, 10.0, 6.0), 1e-9)
    }

    @Test
    fun `deviazione troppo lunga rende il risparmio negativo`() {
        assertTrue(NetSavings.compute(2000, 1990, 40.0, 20.0, 7.0) < 0)
    }

    @Test
    fun `prezzo sopra la media dà risparmio negativo`() {
        assertEquals(-2.0, NetSavings.compute(1900, 1950, 40.0, 0.0, 6.0), 1e-9)
    }

    @Test
    fun `delta in centesimi arrotondato`() {
        assertEquals(-8, deltaCents(1920, 2000))
        assertEquals(5, deltaCents(2049, 2000))
        assertEquals(0, deltaCents(2004, 2000))
        assertEquals(1, deltaCents(2005, 2000))
    }

    @Test
    fun `prezzi implausibili`() {
        assertFalse(PriceRules.isPlausible(price(FuelCategory.BENZINA, 100)))
        assertTrue(PriceRules.isPlausible(price(FuelCategory.BENZINA, 1899)))
        assertTrue(PriceRules.isPlausible(price(FuelCategory.GPL, 849)))
        assertFalse(PriceRules.isPlausible(price(FuelCategory.METANO, 4999)))
    }

    @Test
    fun `soglia dei prezzi sospetti al 20 percento sotto la mediana`() {
        assertEquals(1878, PriceRules.suspiciousBelow(2347))
        assertTrue(1659 < PriceRules.suspiciousBelow(2347)) // caso reale scartato
        assertFalse(2115 < PriceRules.suspiciousBelow(2347)) // −10%: occasione vera, resta
    }

    @Test
    fun `soglia di freschezza relativa alla data di estrazione`() {
        val since = PriceRules.freshSince(LocalDate.of(2026, 9, 25))
        assertEquals(LocalDateTime.of(2026, 9, 11, 0, 0).atZone(MimitCsvParser.ROME).toInstant(), since)
    }

    private fun price(category: FuelCategory, milli: Int) =
        FuelPrice(1, "x", category, true, true, milli, Instant.EPOCH)
}
