package it.faiilpieno.domain.fuel

import it.faiilpieno.domain.model.FuelCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class FuelClassifierTest {

    private fun check(description: String, category: FuelCategory, isBase: Boolean) =
        assertEquals(description, FuelClass(category, isBase), FuelClassifier.classify(description))

    @Test
    fun `prodotti base`() {
        check("Benzina", FuelCategory.BENZINA, true)
        check("Gasolio", FuelCategory.GASOLIO, true)
        check("GPL", FuelCategory.GPL, true)
        check("Metano", FuelCategory.METANO, true)
        check("L-GNC", FuelCategory.METANO, true)
        check(" benzina ", FuelCategory.BENZINA, true)
    }

    @Test
    fun `gasoli speciali`() {
        listOf(
            "Blue Diesel", "HVOlution", "HVO", "Supreme Diesel", "Hi-Q Diesel", "Gasolio speciale",
            "Diesel Shell V Power", "V-Power Diesel", "DieselMax", "REHVO", "Excellium diesel", "Gasolio artico",
        ).forEach { check(it, FuelCategory.GASOLIO, false) }
    }

    @Test
    fun `benzine speciali`() {
        listOf(
            "Blue Super", "Benzina speciale", "Benzina WR 100", "HiQ Perform+", "Benzina Shell V Power",
            "V-Power", "Verde speciale", "Benzina 102 Ottani",
        ).forEach { check(it, FuelCategory.BENZINA, false) }
    }

    @Test
    fun `sconosciuti e GNL finiscono in altro`() {
        check("GNL", FuelCategory.ALTRO, false)
        check("F101", FuelCategory.ALTRO, false)
    }
}
