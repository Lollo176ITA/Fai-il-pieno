package it.faiilpieno.domain.pricing

import it.faiilpieno.domain.model.FuelCategory
import it.faiilpieno.domain.model.FuelPrice
import it.faiilpieno.domain.parser.MimitCsvParser
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Regole per decidere quali prezzi sono affidabili. */
object PriceRules {

    /** Un prezzo comunicato da più di questi giorni non viene mostrato né usato nelle medie. */
    const val FRESHNESS_DAYS = 14L

    /** Intervalli plausibili in millesimi di €/l (€/kg per il metano): fuori sono errori di battitura. */
    private val plausibleRange = mapOf(
        FuelCategory.BENZINA to 800..4_000,
        FuelCategory.GASOLIO to 800..4_000,
        FuelCategory.GPL to 300..2_500,
        FuelCategory.METANO to 500..4_500,
        FuelCategory.ALTRO to 300..5_000,
    )

    fun isPlausible(price: FuelPrice): Boolean =
        price.priceMilli in plausibleRange.getValue(price.category)

    /**
     * Un prezzo più basso del 20% rispetto alla mediana nazionale è quasi sempre un errore del
     * gestore (sui dati reali: gasolio a 1,659 €/l con mediana 2,347, fermo da giorni e identico
     * tra self e servito). Non lo consigliamo e non lo usiamo per le medie.
     */
    const val SUSPICIOUS_BELOW_MEDIAN = 0.20

    fun suspiciousBelow(medianMilli: Int): Int = Math.round(medianMilli * (1 - SUSPICIOUS_BELOW_MEDIAN)).toInt()

    /**
     * Istante prima del quale un prezzo è considerato non aggiornato. È relativo alla data di
     * estrazione, non a oggi: se il ministero pubblica in ritardo, i prezzi non diventano tutti vecchi.
     */
    fun freshSince(extractionDate: LocalDate): Instant =
        extractionDate.minusDays(FRESHNESS_DAYS).atTime(LocalTime.MIDNIGHT).atZone(MimitCsvParser.ROME).toInstant()
}

/** Differenza rispetto a una media, arrotondata al centesimo: negativa = più economico. */
fun deltaCents(priceMilli: Int, averageMilli: Int): Int =
    Math.round((priceMilli - averageMilli) / 10.0).toInt()
