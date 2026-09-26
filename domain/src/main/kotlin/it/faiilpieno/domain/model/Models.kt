package it.faiilpieno.domain.model

import java.time.Instant
import java.time.LocalDate

/** Categoria di carburante. Il metano è venduto a kg, tutti gli altri a litro. */
enum class FuelCategory {
    BENZINA, GASOLIO, GPL, METANO, ALTRO;

    val isSoldByKg: Boolean get() = this == METANO

    /**
     * Self e servito hanno prezzi diversi solo per benzina e gasolio. GPL e metano sono quasi
     * sempre serviti (il self esiste in circa il 3% degli impianti): lì la modalità si ignora.
     */
    val hasServiceModes: Boolean get() = this == BENZINA || this == GASOLIO

    companion object {
        /** Categorie selezionabili dall'utente (ALTRO non lo è). */
        val selectable = listOf(BENZINA, GASOLIO, GPL, METANO)
    }
}

enum class StationType { STRADALE, AUTOSTRADALE, ALTRO }

enum class ServiceMode {
    SELF, SERVITO;

    val isSelf: Boolean get() = this == SELF
}

data class Station(
    val id: Long,
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
) {
    val location: GeoPoint?
        get() = if (latitude != null && longitude != null) GeoPoint(latitude, longitude) else null
}

/** Prezzo in millesimi di euro (2,309 €/l → 2309) per evitare errori di arrotondamento. */
data class FuelPrice(
    val stationId: Long,
    val fuelRaw: String,
    val category: FuelCategory,
    val isBase: Boolean,
    val isSelf: Boolean,
    val priceMilli: Int,
    val communicatedAt: Instant,
)

data class GeoPoint(val latitude: Double, val longitude: Double)

/** Metadati dell'ultima estrazione MIMIT importata. */
data class DatasetInfo(
    val extractionDate: LocalDate,
    val importedAt: Instant,
    val stationCount: Int,
    val priceCount: Int,
    val skippedRows: Int,
)
