package it.faiilpieno.domain.parser

import it.faiilpieno.domain.brand.BrandNormalizer
import it.faiilpieno.domain.fuel.FuelClassifier
import it.faiilpieno.domain.model.FuelPrice
import it.faiilpieno.domain.model.Station
import it.faiilpieno.domain.model.StationType
import java.io.BufferedReader
import java.io.Reader
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** Il file non ha la struttura attesa (intestazione, separatore o colonne diversi). */
class MimitFormatException(message: String) : RuntimeException(message)

data class ParseReport(
    val extractionDate: LocalDate,
    val parsedRows: Int,
    val skippedRows: Int,
)

/**
 * Parser in streaming dei CSV MIMIT: legge una riga alla volta e la passa a `onRow`,
 * senza mai tenere il file in memoria.
 *
 * Formato (dal 10/02/2026): prima riga "Estrazione del AAAA-MM-GG", seconda riga
 * intestazione, separatore "|", nessun quoting, decimali con il punto.
 */
object MimitCsvParser {

    const val SEPARATOR = '|'

    val ROME: ZoneId = ZoneId.of("Europe/Rome")

    private val STATION_COLUMNS = listOf(
        "idImpianto", "Gestore", "Bandiera", "Tipo Impianto", "Nome Impianto",
        "Indirizzo", "Comune", "Provincia", "Latitudine", "Longitudine",
    )
    private val PRICE_COLUMNS = listOf("idImpianto", "descCarburante", "prezzo", "isSelf", "dtComu")

    private val extractionRegex = Regex("""Estrazione del (\d{4}-\d{2}-\d{2})""")
    private val dtComuFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
    private val whitespace = Regex("""\s+""")
    private val domainLike = Regex("""^[\w.-]+\.(it|com|net|org|eu)$""", RegexOption.IGNORE_CASE)

    fun parseStations(reader: Reader, onRow: (Station) -> Unit): ParseReport =
        parse(reader, STATION_COLUMNS, ::parseStationFields, onRow)

    fun parsePrices(reader: Reader, onRow: (FuelPrice) -> Unit): ParseReport =
        parse(reader, PRICE_COLUMNS, ::parsePriceFields, onRow)

    /** Legge solo la data di estrazione, per decidere se il file è nuovo senza analizzarlo tutto. */
    fun readExtractionDate(reader: Reader): LocalDate =
        parseExtractionLine(BufferedReader(reader).readLine())

    private fun <T> parse(
        reader: Reader,
        expectedColumns: List<String>,
        parseFields: (List<String>) -> T?,
        onRow: (T) -> Unit,
    ): ParseReport {
        val buffered = reader as? BufferedReader ?: BufferedReader(reader)
        val date = parseExtractionLine(buffered.readLine())
        checkHeader(buffered.readLine(), expectedColumns)

        var parsed = 0
        var skipped = 0
        while (true) {
            val line = buffered.readLine() ?: break
            if (line.isBlank()) continue
            val row = parseFields(line.split(SEPARATOR))
            if (row == null) skipped++ else {
                onRow(row)
                parsed++
            }
        }
        return ParseReport(date, parsed, skipped)
    }

    private fun parseExtractionLine(line: String?): LocalDate {
        val text = line?.removePrefix("﻿")?.trim()
            ?: throw MimitFormatException("File vuoto")
        val match = extractionRegex.find(text)
            ?: throw MimitFormatException("Riga di estrazione mancante: \"$text\"")
        return try {
            LocalDate.parse(match.groupValues[1])
        } catch (e: DateTimeParseException) {
            throw MimitFormatException("Data di estrazione non valida: \"$text\"")
        }
    }

    private fun checkHeader(line: String?, expected: List<String>) {
        val header = line?.trim() ?: throw MimitFormatException("Intestazione mancante")
        if (SEPARATOR !in header) {
            throw MimitFormatException("Separatore \"$SEPARATOR\" non trovato nell'intestazione")
        }
        val columns = header.split(SEPARATOR).map { it.trim().lowercase() }
        if (columns != expected.map { it.lowercase() }) {
            throw MimitFormatException("Colonne inattese: $header")
        }
    }

    /**
     * Il nome impianto può contenere a sua volta "|" (es. "STOIL | gestori.prezzibenzina.it"):
     * i primi 4 e gli ultimi 5 campi sono fissi, quello che sta in mezzo è il nome.
     */
    private fun parseStationFields(f: List<String>): Station? {
        if (f.size < STATION_COLUMNS.size) return null
        val id = f[0].trim().toLongOrNull() ?: return null
        val n = f.size
        val nameParts = f.subList(4, n - 5).map { clean(it) }.filter { it.isNotEmpty() }
        val name = nameParts.filterNot { nameParts.size > 1 && domainLike.matches(it) }.joinToString(" ")
        val brandRaw = clean(f[2])
        return Station(
            id = id,
            operator = clean(f[1]),
            brandRaw = brandRaw,
            brand = BrandNormalizer.normalize(brandRaw),
            type = when (f[3].trim().lowercase()) {
                "stradale" -> StationType.STRADALE
                "autostradale" -> StationType.AUTOSTRADALE
                else -> StationType.ALTRO
            },
            name = name,
            address = clean(f[n - 5]),
            municipality = clean(f[n - 4]),
            province = clean(f[n - 3]),
            latitude = f[n - 2].trim().toDoubleOrNull(),
            longitude = f[n - 1].trim().toDoubleOrNull(),
        )
    }

    private fun parsePriceFields(f: List<String>): FuelPrice? {
        if (f.size != PRICE_COLUMNS.size) return null
        val id = f[0].trim().toLongOrNull() ?: return null
        val description = clean(f[1]).ifEmpty { return null }
        val priceMilli = parseMilli(f[2]) ?: return null
        val isSelf = when (f[3].trim()) {
            "1" -> true
            "0" -> false
            else -> return null
        }
        val communicatedAt = try {
            LocalDateTime.parse(f[4].trim(), dtComuFormat).atZone(ROME).toInstant()
        } catch (e: DateTimeParseException) {
            return null
        }
        val fuelClass = FuelClassifier.classify(description)
        return FuelPrice(id, description, fuelClass.category, fuelClass.isBase, isSelf, priceMilli, communicatedAt)
    }

    /** "2.309" → 2309, senza passare per i Double. */
    private fun parseMilli(text: String): Int? = try {
        BigDecimal(text.trim()).movePointRight(3).setScale(0, RoundingMode.HALF_UP).intValueExact()
    } catch (e: NumberFormatException) {
        null
    } catch (e: ArithmeticException) {
        null
    }

    private fun clean(s: String): String = s.replace(whitespace, " ").trim()
}
