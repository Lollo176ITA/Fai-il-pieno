package it.faiilpieno.domain.geo

import it.faiilpieno.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PolylineTest {

    @Test
    fun `decodifica l'esempio della documentazione Google`() {
        val points = Polyline.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(3, points.size)
        assertPoint(GeoPoint(38.5, -120.2), points[0])
        assertPoint(GeoPoint(40.7, -120.95), points[1])
        assertPoint(GeoPoint(43.252, -126.453), points[2])
    }

    @Test
    fun `codifica e decodifica sono inverse`() {
        val route = listOf(GeoPoint(41.89021, 12.49223), GeoPoint(41.90101, 12.50183), GeoPoint(45.46427, 9.18951))
        val decoded = Polyline.decode(Polyline.encode(route))
        assertEquals(route.size, decoded.size)
        route.zip(decoded).forEach { (expected, actual) -> assertPoint(expected, actual) }
    }

    @Test
    fun `stringa vuota = nessun punto`() {
        assertTrue(Polyline.decode("").isEmpty())
        assertEquals("", Polyline.encode(emptyList()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `stringa troncata rifiutata`() {
        Polyline.decode("_p~iF~ps|U_")
    }

    private fun assertPoint(expected: GeoPoint, actual: GeoPoint) {
        assertEquals(expected.latitude, actual.latitude, 1e-5)
        assertEquals(expected.longitude, actual.longitude, 1e-5)
    }
}

class PointSegmentTest {

    // Segmento di circa 1,1 km verso est, a Milano.
    private val a = GeoPoint(45.4642, 9.1800)
    private val b = GeoPoint(45.4642, 9.1940)

    @Test
    fun `punto sul segmento`() {
        val mid = GeoPoint(45.4642, 9.1870)
        val result = PointSegment.project(mid, a, b)
        assertEquals(0.0, result.distanceMeters, 0.5)
        assertEquals(0.5, result.fraction, 0.01)
    }

    @Test
    fun `punto a 200 m a nord della metà`() {
        val p = destination(GeoPoint(45.4642, 9.1870), 200.0, 0.0)
        val result = PointSegment.project(p, a, b)
        assertEquals(200.0, result.distanceMeters, 2.0)
        assertEquals(0.5, result.fraction, 0.01)
    }

    @Test
    fun `oltre la fine vale la distanza dall'estremo`() {
        val p = destination(b, 300.0, 90.0)
        val result = PointSegment.project(p, a, b)
        assertEquals(300.0, result.distanceMeters, 3.0)
        assertEquals(1.0, result.fraction, 0.0)
    }

    @Test
    fun `prima dell'inizio vale la distanza dall'origine`() {
        val p = destination(a, 150.0, 225.0)
        val result = PointSegment.project(p, a, b)
        assertEquals(Haversine.distanceMeters(p, a), result.distanceMeters, 2.0)
        assertEquals(0.0, result.fraction, 0.0)
    }

    @Test
    fun `segmento degenere di un solo punto`() {
        val p = destination(a, 400.0, 45.0)
        val result = PointSegment.project(p, a, a)
        assertEquals(400.0, result.distanceMeters, 4.0)
        assertEquals(0.0, result.fraction, 0.0)
    }
}

class RouteCorridorTest {

    // Percorso a "L": 2 km verso est, poi 2 km verso nord.
    private val start = GeoPoint(45.4642, 9.1900)
    private val corner = destination(start, 2_000.0, 90.0)
    private val end = destination(corner, 2_000.0, 0.0)
    private val route = listOf(start, destination(start, 1_000.0, 90.0), corner, destination(corner, 1_000.0, 0.0), end)

    @Test
    fun `lunghezza del percorso`() {
        assertEquals(4_000.0, RouteCorridor(route, 500.0).lengthMeters, 10.0)
    }

    @Test
    fun `distributore accanto al primo tratto`() {
        val station = destination(destination(start, 500.0, 90.0), 120.0, 180.0)
        val position = located(RouteCorridor(route, 500.0).locate(station))
        assertEquals(120.0, position.distanceFromRouteMeters, 2.0)
        assertEquals(500.0, position.alongRouteMeters, 5.0)
    }

    @Test
    fun `distributore accanto al secondo tratto`() {
        val station = destination(destination(corner, 1_500.0, 0.0), 300.0, 90.0)
        val position = located(RouteCorridor(route, 500.0).locate(station))
        assertEquals(300.0, position.distanceFromRouteMeters, 3.0)
        assertEquals(3_500.0, position.alongRouteMeters, 10.0)
    }

    @Test
    fun `vicino all'angolo conta il tratto più vicino`() {
        // A sud-est dell'angolo: più vicino all'angolo stesso che ai due tratti.
        val station = destination(corner, 200.0, 135.0)
        val position = located(RouteCorridor(route, 500.0).locate(station))
        assertEquals(200.0, position.distanceFromRouteMeters, 3.0)
        assertEquals(2_000.0, position.alongRouteMeters, 10.0)
    }

    @Test
    fun `oltre la distanza massima non è nel corridoio`() {
        val station = destination(destination(start, 1_000.0, 90.0), 600.0, 180.0)
        assertNull(RouteCorridor(route, 500.0).locate(station))
        assertNotNull(RouteCorridor(route, 1_000.0).locate(station))
    }

    @Test
    fun `il riquadro contiene il corridoio`() {
        val corridor = RouteCorridor(route, 500.0)
        val station = destination(start, 480.0, 270.0)
        assertTrue(station in corridor.boundingBox)
        assertNotNull(corridor.locate(station))
    }

    @Test
    fun `percorso lungo con tratti di un solo punto ripetuto`() {
        val withDuplicates = listOf(start, start) + route + listOf(end)
        val station = destination(destination(start, 500.0, 90.0), 120.0, 0.0)
        val position = located(RouteCorridor(withDuplicates, 500.0).locate(station))
        assertEquals(120.0, position.distanceFromRouteMeters, 2.0)
    }

    private fun <T> located(value: T?): T {
        assertNotNull("fuori dal corridoio", value)
        return value!!
    }
}
