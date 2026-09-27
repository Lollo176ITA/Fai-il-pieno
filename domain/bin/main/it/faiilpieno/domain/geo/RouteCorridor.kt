package it.faiilpieno.domain.geo

import it.faiilpieno.domain.model.GeoPoint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot

/** Proiezione di un punto su un segmento: distanza e posizione (0 = inizio, 1 = fine). */
data class SegmentProjection(val distanceMeters: Double, val fraction: Double)

object PointSegment {
    /** Metri per grado di latitudine, con lo stesso raggio terrestre di [Haversine]. */
    internal const val METERS_PER_DEGREE = 6_371_008.8 * Math.PI / 180.0

    /**
     * Distanza punto-segmento su una proiezione piana centrata sul punto. Sui tratti di una
     * polyline stradale (da pochi metri a qualche km) l'errore rispetto alla sfera è trascurabile.
     */
    fun project(p: GeoPoint, a: GeoPoint, b: GeoPoint): SegmentProjection {
        val cosLat = cos(Math.toRadians(p.latitude))
        val ax = (a.longitude - p.longitude) * METERS_PER_DEGREE * cosLat
        val ay = (a.latitude - p.latitude) * METERS_PER_DEGREE
        val dx = (b.longitude - a.longitude) * METERS_PER_DEGREE * cosLat
        val dy = (b.latitude - a.latitude) * METERS_PER_DEGREE
        val lengthSquared = dx * dx + dy * dy
        val t = if (lengthSquared == 0.0) 0.0 else ((-ax * dx - ay * dy) / lengthSquared).coerceIn(0.0, 1.0)
        return SegmentProjection(hypot(ax + t * dx, ay + t * dy), t)
    }
}

/** Dove si trova un punto rispetto al percorso. */
data class RoutePosition(
    /** Distanza in linea d'aria dal punto più vicino del percorso. */
    val distanceFromRouteMeters: Double,
    /** Metri percorsi dalla partenza fino al punto del percorso più vicino. */
    val alongRouteMeters: Double,
)

/**
 * Fascia larga [bufferMeters] attorno a un percorso. I segmenti sono indicizzati in una griglia,
 * così per ogni distributore si misura la distanza solo dai pochi segmenti vicini, anche su
 * percorsi con migliaia di punti.
 */
class RouteCorridor(points: List<GeoPoint>, val bufferMeters: Double) {

    private val points: List<GeoPoint> = points.filterIndexed { i, p -> i == 0 || p != points[i - 1] }

    /** Metri dalla partenza all'inizio di ogni punto. */
    private val cumulative = DoubleArray(this.points.size)

    private val grid = HashMap<Long, MutableList<Int>>()

    val lengthMeters: Double

    /** Riquadro che contiene tutta la fascia: primo filtro (indicizzato) sul database. */
    val boundingBox: BoundingBox

    init {
        require(this.points.isNotEmpty()) { "Percorso vuoto" }
        for (i in 1 until this.points.size) {
            cumulative[i] = cumulative[i - 1] + Haversine.distanceMeters(this.points[i - 1], this.points[i])
        }
        lengthMeters = cumulative.last()

        val maxAbsLat = this.points.maxOf { abs(it.latitude) }
        // Margine del 10% per compensare la proiezione piana.
        val marginLat = bufferMeters * 1.1 / PointSegment.METERS_PER_DEGREE
        val marginLon = marginLat / cos(Math.toRadians(maxAbsLat)).coerceAtLeast(0.01)
        boundingBox = BoundingBox(
            this.points.minOf { it.latitude } - marginLat,
            this.points.maxOf { it.latitude } + marginLat,
            this.points.minOf { it.longitude } - marginLon,
            this.points.maxOf { it.longitude } + marginLon,
        )

        for (i in segmentIndices()) {
            val a = this.points[i]
            val b = this.points[minOf(i + 1, this.points.lastIndex)]
            val minRow = cell(minOf(a.latitude, b.latitude) - marginLat)
            val maxRow = cell(maxOf(a.latitude, b.latitude) + marginLat)
            val minCol = cell(minOf(a.longitude, b.longitude) - marginLon)
            val maxCol = cell(maxOf(a.longitude, b.longitude) + marginLon)
            for (row in minRow..maxRow) for (col in minCol..maxCol) {
                grid.getOrPut(key(row, col)) { ArrayList(4) }.add(i)
            }
        }
    }

    /** Posizione del punto rispetto al percorso, o null se è fuori dalla fascia. */
    fun locate(p: GeoPoint): RoutePosition? {
        val candidates = grid[key(cell(p.latitude), cell(p.longitude))] ?: return null
        var best: RoutePosition? = null
        for (i in candidates) {
            val a = points[i]
            val b = points[minOf(i + 1, points.lastIndex)]
            val projection = PointSegment.project(p, a, b)
            if (projection.distanceMeters > bufferMeters) continue
            if (best == null || projection.distanceMeters < best.distanceFromRouteMeters) {
                val segmentLength = if (i < points.lastIndex) cumulative[i + 1] - cumulative[i] else 0.0
                best = RoutePosition(projection.distanceMeters, cumulative[i] + projection.fraction * segmentLength)
            }
        }
        return best
    }

    /** Un percorso di un solo punto ha un unico segmento degenere. */
    private fun segmentIndices(): IntRange = if (points.size == 1) 0..0 else 0 until points.lastIndex

    private fun cell(degrees: Double): Int = floor(degrees / CELL_DEGREES).toInt()

    private fun key(row: Int, col: Int): Long = (row.toLong() shl 32) or (col.toLong() and 0xffffffffL)

    private companion object {
        /** Circa 1,1 km di latitudine: celle poco più grandi della fascia predefinita. */
        const val CELL_DEGREES = 0.01
    }
}
