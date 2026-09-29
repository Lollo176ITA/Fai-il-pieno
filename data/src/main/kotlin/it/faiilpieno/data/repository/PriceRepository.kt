package it.faiilpieno.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import it.faiilpieno.data.db.StationDao
import it.faiilpieno.data.db.StationPriceRow
import it.faiilpieno.data.db.toDomain
import it.faiilpieno.data.mimit.MimitDownloader
import it.faiilpieno.data.mimit.MimitImporter
import it.faiilpieno.domain.geo.BoundingBox
import it.faiilpieno.domain.geo.Haversine
import it.faiilpieno.domain.model.DatasetInfo
import it.faiilpieno.domain.model.FuelCategory
import it.faiilpieno.domain.model.FuelPrice
import it.faiilpieno.domain.model.GeoPoint
import it.faiilpieno.domain.model.ServiceMode
import it.faiilpieno.domain.model.Station
import it.faiilpieno.domain.nearby.Offer
import it.faiilpieno.domain.parser.MimitCsvParser
import it.faiilpieno.domain.pricing.PriceRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

sealed interface RefreshOutcome {
    data class Updated(val info: DatasetInfo) : RefreshOutcome
    data object Unchanged : RefreshOutcome
}

data class NearbySearch(val offers: List<Offer>, val radiusMeters: Double)

data class StationDetail(
    val station: Station,
    val prices: List<FuelPrice>,
    /** Prezzi molto sotto la mediana nazionale, mostrati con un avviso. */
    val suspect: Set<FuelPrice>,
    /** I prezzi comunicati prima di questo istante vanno mostrati come "non aggiornati". */
    val freshSince: Instant?,
)

@Singleton
class PriceRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: StationDao,
    private val downloader: MimitDownloader,
    private val importer: MimitImporter,
) {

    val datasetInfo: Flow<DatasetInfo?> = dao.observeDatasetInfo().map { it?.toDomain() }

    /**
     * Scarica prima i prezzi e ne legge la data: se non è più recente di quella in archivio
     * l'anagrafica non viene neanche scaricata. Lancia IOException o MimitFormatException.
     */
    suspend fun refresh(force: Boolean = false): RefreshOutcome = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "mimit")
        val pricesFile = File(dir, "prezzo_alle_8.csv")
        val stationsFile = File(dir, "anagrafica_impianti_attivi.csv")
        try {
            downloader.download(MimitDownloader.PRICES_URL, pricesFile)
            val newDate = pricesFile.bufferedReader().use { MimitCsvParser.readExtractionDate(it) }
            val current = dao.datasetInfo()?.extractionDate
            if (!force && current != null && !newDate.isAfter(current)) return@withContext RefreshOutcome.Unchanged

            downloader.download(MimitDownloader.STATIONS_URL, stationsFile)
            RefreshOutcome.Updated(importer.import(stationsFile, pricesFile))
        } finally {
            pricesFile.delete()
            stationsFile.delete()
        }
    }

    suspend fun nationalAverage(category: FuelCategory, mode: ServiceMode): Int? =
        dao.nationalAverage(category, modesFor(category, mode))

    /**
     * Cerca entro [SEARCH_RADII] allargando il raggio finché non trova almeno [MIN_RESULTS]
     * distributori: in città bastano pochi km, in campagna serve andare più lontano.
     */
    suspend fun findNearby(center: GeoPoint, category: FuelCategory, mode: ServiceMode): NearbySearch {
        val freshSince = freshSince() ?: return NearbySearch(emptyList(), SEARCH_RADII.first())
        var offers = emptyList<Offer>()
        for (radius in SEARCH_RADII) {
            offers = offersWithin(center, radius, category, mode, freshSince)
            if (offers.size >= MIN_RESULTS) return NearbySearch(offers, radius)
        }
        return NearbySearch(offers, SEARCH_RADII.last())
    }

    /**
     * Tutti i distributori con un prezzo aggiornato per la mappa (circa 20.000 per la benzina self).
     * La distanza non serve e resta NaN: la mappa la calcola solo per quelli che mostra in elenco.
     */
    suspend fun allOffers(category: FuelCategory, mode: ServiceMode): List<Offer> =
        offersInArea(BoundingBox(-90.0, 90.0, -180.0, 180.0), category, mode)

    /** Distributori nel riquadro [box], senza distanza (NaN). */
    suspend fun offersInArea(box: BoundingBox, category: FuelCategory, mode: ServiceMode): List<Offer> {
        val freshSince = freshSince() ?: return emptyList()
        return dao.offersInArea(
            box.minLatitude, box.maxLatitude, box.minLongitude, box.maxLongitude,
            category, modesFor(category, mode), freshSince, Int.MAX_VALUE,
        ).onePerStation(mode).map { row -> Offer(row.station.toDomain(), row.price(), Double.NaN) }
    }

    suspend fun stationDetail(id: Long): StationDetail? {
        val station = dao.station(id)?.toDomain() ?: return null
        val rows = dao.pricesOf(id)
        return StationDetail(
            station = station,
            prices = rows.map { it.toDomain() },
            suspect = rows.filter { it.isSuspect }.map { it.toDomain() }.toSet(),
            freshSince = freshSince(),
        )
    }

    /**
     * Prezzo del carburante [category] al distributore [stationId] nella modalità preferita (o
     * nell'altra, se manca). Serve a ricavare i litri da un importo.
     */
    suspend fun priceAt(stationId: Long, category: FuelCategory, mode: ServiceMode): Int? {
        val candidates = dao.pricesOf(stationId).filter { it.fuelCategory == category && it.isBase }
        return (candidates.firstOrNull { it.isSelf == mode.isSelf } ?: candidates.firstOrNull())?.priceMilli
    }

    private suspend fun offersWithin(
        center: GeoPoint,
        radius: Double,
        category: FuelCategory,
        mode: ServiceMode,
        freshSince: Instant,
    ): List<Offer> {
        val box = BoundingBox.around(center, radius)
        return dao.offersInArea(
            box.minLatitude, box.maxLatitude, box.minLongitude, box.maxLongitude,
            category, modesFor(category, mode), freshSince, Int.MAX_VALUE,
        ).onePerStation(mode).mapNotNull { row ->
            val station = row.station.toDomain()
            val distance = Haversine.distanceMeters(center, station.location ?: return@mapNotNull null)
            if (distance <= radius) Offer(station, row.price(), distance) else null
        }
    }

    private fun modesFor(category: FuelCategory, mode: ServiceMode): List<Boolean> =
        if (category.hasServiceModes) listOf(mode.isSelf) else listOf(true, false)

    /** Se un impianto ha entrambe le modalità, tiene quella scelta dall'utente. */
    private fun List<StationPriceRow>.onePerStation(mode: ServiceMode): List<StationPriceRow> =
        groupBy { it.station.id }.values.map { rows -> rows.firstOrNull { it.isSelf == mode.isSelf } ?: rows.first() }

    private suspend fun freshSince(): Instant? = dao.datasetInfo()?.extractionDate?.let(PriceRules::freshSince)

    companion object {
        val SEARCH_RADII = listOf(5_000.0, 10_000.0, 20_000.0)
        const val MIN_RESULTS = 5
    }
}
