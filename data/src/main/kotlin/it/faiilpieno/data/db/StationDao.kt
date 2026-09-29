package it.faiilpieno.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.faiilpieno.domain.model.FuelCategory
import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class AverageRow(
    val fuelCategory: FuelCategory,
    val isSelf: Boolean,
    val averageMilli: Double,
    val sampleCount: Int,
)

@Dao
interface StationDao {

    // --- Import (chiamati dentro una transazione, su un thread di I/O) ---

    @Query("DELETE FROM prices")
    fun clearPrices()

    @Query("DELETE FROM stations")
    fun clearStations()

    @Query("DELETE FROM fuel_averages")
    fun clearAverages()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertStations(stations: List<StationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPrices(prices: List<PriceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAverages(averages: List<FuelAverageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertDatasetInfo(info: DatasetInfoEntity)

    @Query(
        """
        SELECT fuelCategory, isSelf, AVG(priceMilli) AS averageMilli, COUNT(*) AS sampleCount
        FROM prices
        WHERE isBase = 1 AND isSuspect = 0 AND communicatedAt >= :freshSince
        GROUP BY fuelCategory, isSelf
        """,
    )
    fun computeAverages(freshSince: Instant): List<AverageRow>

    @Query(
        """
        SELECT fuelCategory, isSelf, COUNT(*) AS count FROM prices
        WHERE isBase = 1 AND communicatedAt >= :freshSince
        GROUP BY fuelCategory, isSelf
        """,
    )
    fun countFreshBase(freshSince: Instant): List<GroupCount>

    /** Prezzo in posizione [offset] tra i base aggiornati, in ordine crescente: con n/2 è la mediana. */
    @Query(
        """
        SELECT priceMilli FROM prices
        WHERE fuelCategory = :category AND isSelf = :isSelf AND isBase = 1 AND communicatedAt >= :freshSince
        ORDER BY priceMilli LIMIT 1 OFFSET :offset
        """,
    )
    fun priceAt(category: FuelCategory, isSelf: Boolean, freshSince: Instant, offset: Int): Int?

    @Query(
        """
        UPDATE prices SET isSuspect = 1
        WHERE fuelCategory = :category AND isSelf = :isSelf AND isBase = 1 AND priceMilli < :below
        """,
    )
    fun markSuspect(category: FuelCategory, isSelf: Boolean, below: Int)

    // --- Letture ---

    @Query("SELECT * FROM dataset_info WHERE id = 0")
    fun observeDatasetInfo(): Flow<DatasetInfoEntity?>

    @Query("SELECT * FROM dataset_info WHERE id = 0")
    suspend fun datasetInfo(): DatasetInfoEntity?

    /** Media pesata sulle modalità indicate (una sola per benzina e gasolio, entrambe per GPL e metano). */
    @Query(
        """
        SELECT CAST(ROUND(SUM(averageMilli * sampleCount) * 1.0 / SUM(sampleCount)) AS INTEGER)
        FROM fuel_averages WHERE fuelCategory = :category AND isSelf IN (:isSelf)
        """,
    )
    suspend fun nationalAverage(category: FuelCategory, isSelf: List<Boolean>): Int?

    /** Distributori nel riquadro con un prezzo base aggiornato, dal più economico: con [limit] restano i migliori. */
    @Query(
        """
        SELECT s.*, p.fuelRaw, p.fuelCategory, p.isBase, p.isSelf, p.priceMilli, p.communicatedAt
        FROM stations s JOIN prices p ON p.stationId = s.id
        WHERE s.hasValidCoords = 1
          AND s.latitude BETWEEN :minLat AND :maxLat
          AND s.longitude BETWEEN :minLon AND :maxLon
          AND p.fuelCategory = :category AND p.isBase = 1 AND p.isSelf IN (:isSelf)
          AND p.communicatedAt >= :freshSince AND p.isSuspect = 0
        ORDER BY p.priceMilli
        LIMIT :limit
        """,
    )
    suspend fun offersInArea(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        category: FuelCategory,
        isSelf: List<Boolean>,
        freshSince: Instant,
        limit: Int,
    ): List<StationPriceRow>

    @Query("SELECT * FROM stations WHERE id = :id")
    suspend fun station(id: Long): StationEntity?

    @Query("SELECT * FROM prices WHERE stationId = :id ORDER BY isBase DESC, fuelCategory, isSelf DESC, fuelRaw")
    suspend fun pricesOf(id: Long): List<PriceEntity>
}
