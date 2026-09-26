package it.faiilpieno.data.mimit

import it.faiilpieno.data.db.AppDatabase
import it.faiilpieno.data.db.DatasetInfoEntity
import it.faiilpieno.data.db.FuelAverageEntity
import it.faiilpieno.data.db.PriceEntity
import it.faiilpieno.data.db.StationEntity
import it.faiilpieno.data.db.toDomain
import it.faiilpieno.data.db.toEntity
import it.faiilpieno.domain.model.DatasetInfo
import it.faiilpieno.domain.parser.MimitCsvParser
import it.faiilpieno.domain.pricing.PriceRules
import java.io.File
import java.time.Instant
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Sostituisce in un'unica transazione tutti i dati con quelli dei due file scaricati:
 * se qualcosa va storto a metà, restano i dati del giorno prima.
 */
class MimitImporter @Inject constructor(private val db: AppDatabase) {

    fun import(stationsFile: File, pricesFile: File, now: Instant = Instant.now()): DatasetInfo {
        val dao = db.stationDao()
        return db.runInTransaction<DatasetInfo> {
            dao.clearPrices()
            dao.clearStations()
            dao.clearAverages()

            val stationIds = HashSet<Long>(32_768)
            val stations = Batch<StationEntity>(dao::insertStations)
            val stationReport = stationsFile.bufferedReader().use { reader ->
                MimitCsvParser.parseStations(reader) { station ->
                    stationIds += station.id
                    stations += station.toEntity()
                }
            }
            stations.flush()

            var rejectedPrices = 0
            val prices = Batch<PriceEntity>(dao::insertPrices)
            val priceReport = pricesFile.bufferedReader().use { reader ->
                MimitCsvParser.parsePrices(reader) { price ->
                    if (price.stationId in stationIds && PriceRules.isPlausible(price)) {
                        prices += price.toEntity()
                    } else {
                        rejectedPrices++
                    }
                }
            }
            prices.flush()

            // La data dei prezzi è quella che conta per l'utente.
            val extractionDate = priceReport.extractionDate
            val freshSince = PriceRules.freshSince(extractionDate)

            // Prima si marcano i prezzi sospetti, poi si calcolano le medie senza di loro.
            dao.countFreshBase(freshSince).forEach { group ->
                val median = dao.priceAt(group.fuelCategory, group.isSelf, freshSince, group.count / 2) ?: return@forEach
                dao.markSuspect(group.fuelCategory, group.isSelf, PriceRules.suspiciousBelow(median))
            }

            val averages = dao.computeAverages(freshSince).map {
                FuelAverageEntity(it.fuelCategory, it.isSelf, it.averageMilli.roundToInt(), it.sampleCount)
            }
            dao.insertAverages(averages)

            val info = DatasetInfoEntity(
                extractionDate = extractionDate,
                importedAt = now,
                stationCount = stationReport.parsedRows,
                priceCount = priceReport.parsedRows - rejectedPrices,
                skippedRows = stationReport.skippedRows + priceReport.skippedRows + rejectedPrices,
            )
            dao.upsertDatasetInfo(info)
            info.toDomain()
        }
    }

    private class Batch<T>(private val insert: (List<T>) -> Unit) {
        private val items = ArrayList<T>(SIZE)

        operator fun plusAssign(item: T) {
            items += item
            if (items.size == SIZE) flush()
        }

        fun flush() {
            if (items.isEmpty()) return
            insert(items.toList())
            items.clear()
        }

        companion object {
            const val SIZE = 500
        }
    }
}
