package it.faiilpieno.domain.pricing

object NetSavings {

    /**
     * risparmio netto = (prezzo_medio_zona − prezzo_distributore) × quantità
     *                 − (km_deviazione × consumo × prezzo_distributore)
     *
     * Prezzi in millesimi di euro, consumo in litri (o kg) ogni 100 km. Risultato in euro.
     */
    fun compute(
        zoneAverageMilli: Int,
        priceMilli: Int,
        quantity: Double,
        detourKm: Double,
        consumptionPer100Km: Double,
    ): Double {
        val price = priceMilli / 1000.0
        val grossSaving = (zoneAverageMilli - priceMilli) / 1000.0 * quantity
        val detourCost = detourKm * (consumptionPer100Km / 100.0) * price
        return grossSaving - detourCost
    }
}
