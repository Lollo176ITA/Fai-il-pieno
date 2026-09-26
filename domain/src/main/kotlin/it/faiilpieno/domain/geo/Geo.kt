package it.faiilpieno.domain.geo

import it.faiilpieno.domain.model.GeoPoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Haversine {
    private const val EARTH_RADIUS_M = 6_371_008.8

    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).let { it * it } + cos(lat1) * cos(lat2) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}

/** Rettangolo lat/lon usato come primo filtro veloce (indicizzato) prima di Haversine. */
data class BoundingBox(
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double,
) {
    operator fun contains(p: GeoPoint): Boolean =
        p.latitude in minLatitude..maxLatitude && p.longitude in minLongitude..maxLongitude

    companion object {
        private const val METERS_PER_DEGREE_LAT = 111_320.0

        /** Rettangolo che contiene sicuramente il cerchio di raggio [radiusMeters] attorno a [center]. */
        fun around(center: GeoPoint, radiusMeters: Double): BoundingBox {
            val dLat = radiusMeters / METERS_PER_DEGREE_LAT
            val cosLat = cos(Math.toRadians(center.latitude)).coerceAtLeast(0.01)
            val dLon = radiusMeters / (METERS_PER_DEGREE_LAT * cosLat)
            return BoundingBox(
                center.latitude - dLat, center.latitude + dLat,
                center.longitude - dLon, center.longitude + dLon,
            )
        }
    }
}

/** Le coordinate MIMIT sono inserite dai gestori e non verificate: scartiamo quelle fuori dall'Italia. */
object ItalyBounds {
    // Include isole minori, Lampedusa, Campione d'Italia, San Marino e Vaticano.
    private val box = BoundingBox(35.2, 47.2, 6.5, 18.6)

    fun isValid(latitude: Double?, longitude: Double?): Boolean {
        if (latitude == null || longitude == null) return false
        if (latitude.isNaN() || longitude.isNaN()) return false
        return GeoPoint(latitude, longitude) in box
    }
}
