package it.faiilpieno.domain.parser

import it.faiilpieno.domain.model.FuelCategory
import it.faiilpieno.domain.model.FuelPrice
import it.faiilpieno.domain.model.Station
import it.faiilpieno.domain.model.StationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Reader
import java.io.StringReader
import java.time.LocalDate
import java.time.LocalDateTime

class MimitCsvParserTest {

    private fun resource(name: String): Reader =
        requireNotNull(javaClass.getResourceAsStream("/mimit/$name")) { "manca $name" }.reader(Charsets.UTF_8)

    private fun stations(name: String): Pair<ParseReport, Map<Long, Station>> {
        val rows = mutableListOf<Station>()
        val report = MimitCsvParser.parseStations(resource(name)) { rows += it }
        return report to rows.associateBy { it.id }
    }

    private fun prices(name: String): Pair<ParseReport, List<FuelPrice>> {
        val rows = mutableListOf<FuelPrice>()
        val report = MimitCsvParser.parsePrices(resource(name)) { rows += it }
        return report to rows
    }

    @Test
    fun `legge la data di estrazione dalla prima riga`() {
        val (report, _) = stations("stations.csv")
        assertEquals(LocalDate.of(2026, 9, 25), report.extractionDate)
    }

    @Test
    fun `conta righe valide e scartate, ignorando le righe vuote`() {
        val (report, rows) = stations("stations.csv")
        assertEquals(6, report.parsedRows)
        assertEquals(2, report.skippedRows) // id non numerico, riga troppo corta
        assertEquals(6, rows.size)
    }

    @Test
    fun `normalizza bandiera e spazi`() {
        val s = stations("stations.csv").second.getValue(59183)
        assertEquals("Eni", s.brand)
        assertEquals("Agip Eni", s.brandRaw)
        assertEquals("SS.189 KM. 64+649 - C.DA SAN MICHELE S.N.C", s.address)
        assertEquals(StationType.STRADALE, s.type)
        assertEquals(37.333935, s.latitude!!, 1e-9)
    }

    @Test
    fun `tabulazione nel nome diventa spazio`() {
        assertEquals("19834 MONTALLEGRO", stations("stations.csv").second.getValue(63381).name)
    }

    @Test
    fun `pipe nel nome non sfasa le colonne`() {
        val s = stations("stations.csv").second.getValue(40820)
        assertEquals("STOIL SIMPLE", s.name)
        assertEquals("ALESSANDRIA", s.municipality)
        assertEquals("AL", s.province)
        assertEquals(44.91704718250436, s.latitude!!, 1e-12)
        assertEquals(8.70067298412323, s.longitude!!, 1e-12)
    }

    @Test
    fun `coordinate mancanti diventano null ma l'impianto resta`() {
        val s = stations("stations.csv").second.getValue(12345)
        assertNull(s.latitude)
        assertNull(s.longitude)
        assertEquals(StationType.AUTOSTRADALE, s.type)
        assertEquals("Keropetrol", s.brand)
    }

    @Test
    fun `coordinate a zero vengono lette, la validazione è a parte`() {
        val s = stations("stations.csv").second.getValue(22222)
        assertEquals(0.0, s.latitude!!, 0.0)
        assertEquals("IP", s.brand)
    }

    @Test
    fun `gestisce il BOM iniziale`() {
        val (report, rows) = stations("stations_bom.csv")
        assertEquals(LocalDate.of(2026, 9, 25), report.extractionDate)
        assertEquals(1, rows.size)
    }

    @Test(expected = MimitFormatException::class)
    fun `rifiuta il vecchio formato con la virgola`() {
        stations("stations_comma.csv")
    }

    @Test(expected = MimitFormatException::class)
    fun `rifiuta un file senza riga di estrazione`() {
        prices("prices_no_extraction.csv")
    }

    @Test(expected = MimitFormatException::class)
    fun `rifiuta colonne inattese`() {
        prices("prices_wrong_columns.csv")
    }

    @Test(expected = MimitFormatException::class)
    fun `rifiuta un file vuoto`() {
        MimitCsvParser.parsePrices(StringReader("")) {}
    }

    @Test
    fun `prezzi validi e righe malformate`() {
        val (report, rows) = prices("prices.csv")
        assertEquals(7, report.parsedRows)
        // prezzo non numerico, isSelf=2, data in altro formato, campi mancanti, carburante vuoto
        assertEquals(5, report.skippedRows)
    }

    @Test
    fun `prezzo in millesimi, self e data di comunicazione`() {
        val p = prices("prices.csv").second.first()
        assertEquals(3464L, p.stationId)
        assertEquals(2309, p.priceMilli)
        assertTrue(p.isSelf)
        assertEquals(FuelCategory.BENZINA, p.category)
        assertTrue(p.isBase)
        assertEquals(
            LocalDateTime.of(2026, 9, 24, 19, 30, 6).atZone(MimitCsvParser.ROME).toInstant(),
            p.communicatedAt,
        )
    }

    @Test
    fun `carburanti speciali classificati ma non base`() {
        val blue = prices("prices.csv").second.single { it.fuelRaw == "Blue Diesel" }
        assertEquals(FuelCategory.GASOLIO, blue.category)
        assertFalse(blue.isBase)
    }

    @Test
    fun `readExtractionDate legge solo l'intestazione`() {
        assertEquals(LocalDate.of(2026, 9, 25), MimitCsvParser.readExtractionDate(resource("prices.csv")))
    }
}
