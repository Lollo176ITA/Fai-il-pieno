package it.faiilpieno.domain.map

import it.faiilpieno.domain.geo.BoundingBox
import it.faiilpieno.domain.model.GeoPoint
import it.faiilpieno.domain.nearby.Offer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/** Cosa disegnare sulla mappa: un distributore con il suo prezzo o un gruppo di distributori vicini. */
sealed interface MapMarker {
    val cheapest: Offer

    data class Single(override val cheapest: Offer) : MapMarker

    /** [cheapest] è il distributore più economico del gruppo; [bounds] serve per avvicinarsi al tocco. */
    data class Cluster(
        val center: GeoPoint,
        val count: Int,
        override val cheapest: Offer,
        val bounds: BoundingBox,
    ) : MapMarker
}

/**
 * Raggruppa i distributori in una griglia sullo schermo (proiezione Web Mercator), così da lontano
 * la mappa mostra "12 · da 1,729" invece di migliaia di etichette sovrapposte.
 *
 * La griglia è ancorata al mondo e usa lo zoom intero: spostando la mappa o durante il pinch i
 * gruppi non cambiano di continuo. Le celle ai bordi dello schermo contano anche i distributori
 * appena fuori, per lo stesso motivo.
 */
object MapClusterer {
    /** Da questo zoom in su (circa 400 m per cella) si vedono tutti i distributori. */
    const val CLUSTER_BELOW_ZOOM = 13.0

    /** Lato della cella in unità della mappa (dp): circa un'etichetta e mezza, per non affollare la mappa. */
    const val CELL_SIZE = 96.0

    /** MapLibre misura lo zoom con tile da 512 unità. */
    private const val TILE_SIZE = 512.0

    fun cluster(offers: List<Offer>, viewport: BoundingBox, zoom: Double, cellSize: Double = CELL_SIZE): List<MapMarker> {
        if (zoom >= CLUSTER_BELOW_ZOOM) {
            return offers
                .filter { it.station.location?.let { p -> p in viewport } == true }
                .sortedBy { it.price.priceMilli }
                .map(MapMarker::Single)
        }
        val world = TILE_SIZE * 2.0.pow(floor(zoom))
        val minCol = floor(x(viewport.minLongitude, world) / cellSize)
        val maxCol = floor(x(viewport.maxLongitude, world) / cellSize)
        val minRow = floor(y(viewport.maxLatitude, world) / cellSize)
        val maxRow = floor(y(viewport.minLatitude, world) / cellSize)

        val cells = LinkedHashMap<Pair<Double, Double>, MutableList<Offer>>()
        for (offer in offers) {
            val p = offer.station.location ?: continue
            val col = floor(x(p.longitude, world) / cellSize)
            val row = floor(y(p.latitude, world) / cellSize)
            if (col in minCol..maxCol && row in minRow..maxRow) cells.getOrPut(col to row) { ArrayList() } += offer
        }
        return cells.values
            .map { members -> if (members.size == 1) MapMarker.Single(members[0]) else clusterOf(members) }
            .sortedBy { it.cheapest.price.priceMilli }
    }

    /** I [limit] distributori più economici nell'area visibile, dal meno caro. */
    fun cheapestIn(offers: List<Offer>, viewport: BoundingBox, limit: Int): List<Offer> =
        offers
            .filter { it.station.location?.let { p -> p in viewport } == true }
            .sortedWith(compareBy({ it.price.priceMilli }, { it.station.id }))
            .take(limit)

    private fun clusterOf(members: List<Offer>): MapMarker.Cluster {
        val points = members.mapNotNull { it.station.location }
        return MapMarker.Cluster(
            center = GeoPoint(points.map { it.latitude }.average(), points.map { it.longitude }.average()),
            count = members.size,
            cheapest = members.minWith(compareBy({ it.price.priceMilli }, { it.station.id })),
            bounds = BoundingBox(
                points.minOf { it.latitude }, points.maxOf { it.latitude },
                points.minOf { it.longitude }, points.maxOf { it.longitude },
            ),
        )
    }

    private fun x(longitude: Double, world: Double): Double = (longitude + 180.0) / 360.0 * world

    private fun y(latitude: Double, world: Double): Double {
        val rad = Math.toRadians(latitude)
        return (1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0 * world
    }
}
