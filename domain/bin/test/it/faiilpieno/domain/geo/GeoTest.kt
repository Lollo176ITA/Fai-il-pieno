package it.faiilpieno.domain.geo

import it.faiilpieno.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {

    private val romaColosseo = GeoPoint(41.8902, 12.4922)
    private val milanoDuomo = GeoPoint(45.4642, 9.1900)

    @Test
    fun `distanza nulla sullo stesso punto`() {
        assertEquals(0.0, Haversine.distanceMeters(romaColosseo, romaColosseo), 1e-6)
    }

    @Test
    fun `Roma-Milano in linea d'aria circa 477 km`() {
        assertEquals(477_000.0, Haversine.distanceMeters(romaColosseo, milanoDuomo), 2_000.0)
    }

    @Test
    fun `un grado di latitudine circa 111 km`() {
        assertEquals(111_195.0, Haversine.distanceMeters(GeoPoint(45.0, 9.0), GeoPoint(46.0, 9.0)), 100.0)
    }

    @Test
    fun `il riquadro contiene il cerchio`() {
        val radius = 5_000.0
        val box = BoundingBox.around(milanoDuomo, radius)
        // Punti a 4,9 km nelle quattro direzioni devono cadere nel riquadro.
        listOf(0.0, 90.0, 180.0, 270.0).forEach { bearing ->
            val p = destination(milanoDuomo, 4_900.0, bearing)
            assertTrue("bearing $bearing", p in box)
            assertTrue(Haversine.distanceMeters(milanoDuomo, p) < radius)
        }
        assertFalse(romaColosseo in box)
    }

    @Test
    fun `coordinate valide solo in Italia`() {
        assertTrue(ItalyBounds.isValid(41.89, 12.49))
        assertTrue(ItalyBounds.isValid(35.50, 12.60)) // Lampedusa
        assertFalse(ItalyBounds.isValid(0.0, 0.0))
        assertFalse(ItalyBounds.isValid(null, 12.49))
        assertFalse(ItalyBounds.isValid(12.49, 41.89)) // invertite
        assertFalse(ItalyBounds.isValid(Double.NaN, 12.0))
    }
}
