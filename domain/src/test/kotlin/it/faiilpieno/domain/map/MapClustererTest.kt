package it.faiilpieno.domain.map

import it.faiilpieno.domain.geo.BoundingBox
import it.faiilpieno.domain.model.FuelCategory
import it.faiilpieno.domain.model.FuelPrice
import it.faiilpieno.domain.model.Station
import it.faiilpieno.domain.model.StationType
import it.faiilpieno.domain.nearby.Offer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MapClustererTest {

    private fun offer(id: Long, lat: Double?, lon: Double?, priceMilli: Int) = Offer(
        station = Station(id, "", "Eni", "Eni", StationType.STRADALE, "S$id", "", "", "", lat, lon),
        price = FuelPrice(id, "Benzina", FuelCategory.BENZINA, true, true, priceMilli, Instant.EPOCH),
        distanceMeters = Double.NaN,
    )

    // Roma e dintorni.
    private val rome = BoundingBox(41.80, 41.98, 12.35, 12.65)

    // Due distributori a circa 150 m, uno a una decina di km.
    private val a = offer(1, 41.9000, 12.5000, 1_850)
    private val b = offer(2, 41.9010, 12.5010, 1_799)
    private val far = offer(3, 41.8200, 12.4000, 1_900)

    @Test
    fun `da vicino mostra tutti i distributori visibili, dal più economico`() {
        val outside = offer(4, 45.0, 9.0, 1_500)
        val markers = MapClusterer.cluster(listOf(a, b, far, outside), rome, zoom = 14.0)
        assertTrue(markers.all { it is MapMarker.Single })
        assertEquals(listOf(2L, 1L, 3L), markers.map { it.cheapest.station.id })
    }

    @Test
    fun `da lontano raggruppa i distributori vicini`() {
        val markers = MapClusterer.cluster(listOf(a, b, far), rome, zoom = 10.0)
        val cluster = markers.filterIsInstance<MapMarker.Cluster>().single()
        assertEquals(2, cluster.count)
        assertEquals(2L, cluster.cheapest.station.id)
        assertEquals(41.9000, cluster.bounds.minLatitude, 1e-9)
        assertEquals(41.9010, cluster.bounds.maxLatitude, 1e-9)
        assertEquals(41.9005, cluster.center.latitude, 1e-9)
        // Il distributore lontano resta da solo.
        assertEquals(3L, markers.filterIsInstance<MapMarker.Single>().single().cheapest.station.id)
    }

    @Test
    fun `lo zoom frazionario usa la griglia dello zoom intero`() {
        val atTen = MapClusterer.cluster(listOf(a, b, far), rome, zoom = 10.0)
        val atTenAndHalf = MapClusterer.cluster(listOf(a, b, far), rome, zoom = 10.9)
        assertEquals(atTen, atTenAndHalf)
    }

    @Test
    fun `a zoom minimo tutta Italia finisce in un gruppo`() {
        val italy = BoundingBox(36.0, 47.0, 6.6, 18.5)
        val milan = offer(5, 45.46, 9.19, 1_700)
        val markers = MapClusterer.cluster(listOf(a, b, far, milan), italy, zoom = 1.0)
        val cluster = markers.single() as MapMarker.Cluster
        assertEquals(4, cluster.count)
        assertEquals(5L, cluster.cheapest.station.id)
    }

    @Test
    fun `ignora i distributori senza coordinate`() {
        val noCoords = offer(6, null, null, 1_000)
        val markers = MapClusterer.cluster(listOf(noCoords, far), rome, zoom = 10.0)
        assertEquals(listOf(3L), markers.map { it.cheapest.station.id })
    }

    @Test
    fun `una cella a cavallo del bordo conta anche chi è appena fuori`() {
        // Viewport che taglia a metà la coppia a/b: il gruppo resta di 2.
        val cut = BoundingBox(41.80, 41.9005, 12.35, 12.65)
        val cluster = MapClusterer.cluster(listOf(a, b), cut, zoom = 10.0).single() as MapMarker.Cluster
        assertEquals(2, cluster.count)
    }

    @Test
    fun `i più economici dell'area visibile`() {
        val outside = offer(4, 45.0, 9.0, 1_500)
        val cheapest = MapClusterer.cheapestIn(listOf(a, b, far, outside), rome, limit = 2)
        assertEquals(listOf(2L, 1L), cheapest.map { it.station.id })
    }
}
