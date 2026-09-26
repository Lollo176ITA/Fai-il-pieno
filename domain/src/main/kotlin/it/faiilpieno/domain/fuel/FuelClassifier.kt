package it.faiilpieno.domain.fuel

import it.faiilpieno.domain.model.FuelCategory

data class FuelClass(val category: FuelCategory, val isBase: Boolean)

/**
 * Classifica le descrizioni libere di `descCarburante` (oltre 60 varianti: "Blue Diesel",
 * "HVOlution", "Benzina WR 100"...). Sono "base" solo i quattro prodotti standard: sono gli
 * unici confrontati tra distributori e usati per le medie.
 */
object FuelClassifier {

    private val base = mapOf(
        "benzina" to FuelCategory.BENZINA,
        "gasolio" to FuelCategory.GASOLIO,
        "gpl" to FuelCategory.GPL,
        "metano" to FuelCategory.METANO,
        // Gas naturale compresso ottenuto da GNL: alla pompa è metano per auto, venduto a kg.
        "l-gnc" to FuelCategory.METANO,
    )

    private val dieselMarkers = listOf("diesel", "gasolio", "hvo")
    private val petrolMarkers = listOf("benzina", "super", "ottani", "verde", "v-power", "hiq perform")

    fun classify(description: String): FuelClass {
        val key = description.trim().lowercase()
        base[key]?.let { return FuelClass(it, isBase = true) }
        return when {
            dieselMarkers.any { it in key } -> FuelClass(FuelCategory.GASOLIO, isBase = false)
            petrolMarkers.any { it in key } -> FuelClass(FuelCategory.BENZINA, isBase = false)
            "gpl" in key -> FuelClass(FuelCategory.GPL, isBase = false)
            "metano" in key -> FuelClass(FuelCategory.METANO, isBase = false)
            // GNL (gas liquefatto, per mezzi pesanti) e sigle non riconosciute.
            else -> FuelClass(FuelCategory.ALTRO, isBase = false)
        }
    }
}
