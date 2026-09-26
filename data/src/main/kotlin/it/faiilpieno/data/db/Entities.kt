package it.faiilpieno.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import it.faiilpieno.domain.geo.ItalyBounds
import it.faiilpieno.domain.model.DatasetInfo
import it.faiilpieno.domain.model.FuelCategory
import it.faiilpieno.domain.model.FuelPrice
import it.faiilpieno.domain.model.Station
import it.faiilpieno.domain.model.StationType
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "stations",
    indices = [Index("brand"), Index("hasValidCoords", "latitude", "longitude")],
)
data class StationEntity(
    @PrimaryKey val id: Long,
    val operator: String,
    val brandRaw: String,
    val brand: String,
    val type: StationType,
    val name: String,
    val address: String,
    val municipality: String,
    val province: String,
    val latitude: Double?,
    val longitude: Double?,
    /** Falso per coordinate mancanti o fuori dall'Italia: l'impianto resta nel DB ma non in lista né in mappa. */
    val hasValidCoords: Boolean,
)

@Entity(
    tableName = "prices",
    primaryKeys = ["stationId", "fuelRaw", "isSelf"],
    foreignKeys = [
        ForeignKey(
            entity = StationEntity::class,
            parentColumns = ["id"],
            childColumns = ["stationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("fuelCategory", "isBase", "isSelf", "communicatedAt")],
)
data class PriceEntity(
    val stationId: Long,
    val fuelRaw: String,
    val fuelCategory: FuelCategory,
    val isBase: Boolean,
    val isSelf: Boolean,
    val priceMilli: Int,
    val communicatedAt: Instant,
    /** Molto sotto la mediana nazionale: probabile errore, escluso da lista e medie. */
    val isSuspect: Boolean = false,
)

@Entity(tableName = "fuel_averages", primaryKeys = ["fuelCategory", "isSelf"])
data class FuelAverageEntity(
    val fuelCategory: FuelCategory,
    val isSelf: Boolean,
    val averageMilli: Int,
    val sampleCount: Int,
)

@Entity(tableName = "dataset_info")
data class DatasetInfoEntity(
    @PrimaryKey val id: Int = SINGLE_ROW,
    val extractionDate: LocalDate,
    val importedAt: Instant,
    val stationCount: Int,
    val priceCount: Int,
    val skippedRows: Int,
) {
    companion object {
        const val SINGLE_ROW = 0
    }
}

/** Riga del join distributore + prezzo. */
data class StationPriceRow(
    @Embedded val station: StationEntity,
    val fuelRaw: String,
    val fuelCategory: FuelCategory,
    val isBase: Boolean,
    val isSelf: Boolean,
    val priceMilli: Int,
    val communicatedAt: Instant,
) {
    fun price() = FuelPrice(station.id, fuelRaw, fuelCategory, isBase, isSelf, priceMilli, communicatedAt)
}

fun Station.toEntity() = StationEntity(
    id, operator, brandRaw, brand, type, name, address, municipality, province,
    latitude, longitude, hasValidCoords = ItalyBounds.isValid(latitude, longitude),
)

fun StationEntity.toDomain() = Station(
    id, operator, brandRaw, brand, type, name, address, municipality, province,
    latitude.takeIf { hasValidCoords }, longitude.takeIf { hasValidCoords },
)

fun FuelPrice.toEntity() = PriceEntity(stationId, fuelRaw, category, isBase, isSelf, priceMilli, communicatedAt)

fun PriceEntity.toDomain() = FuelPrice(stationId, fuelRaw, fuelCategory, isBase, isSelf, priceMilli, communicatedAt)

data class GroupCount(val fuelCategory: FuelCategory, val isSelf: Boolean, val count: Int)

fun DatasetInfoEntity.toDomain() = DatasetInfo(extractionDate, importedAt, stationCount, priceCount, skippedRows)
