package it.faiilpieno.domain.model

/** Unità con cui l'utente preferisce leggere e inserire il consumo. */
enum class ConsumptionUnit { PER_100_KM, KM_PER_UNIT }

/**
 * Profilo dell'auto. Il consumo è sempre salvato in litri (o kg, per il metano) ogni 100 km;
 * [consumptionUnit] serve solo a mostrarlo come preferisce l'utente.
 */
data class CarProfile(
    val fuel: FuelCategory = FuelCategory.BENZINA,
    val tankCapacity: Double = DEFAULT_TANK,
    val consumptionPer100Km: Double = DEFAULT_CONSUMPTION,
    val consumptionUnit: ConsumptionUnit = ConsumptionUnit.KM_PER_UNIT,
    val serviceMode: ServiceMode = ServiceMode.SELF,
    val isConfigured: Boolean = false,
) {
    /** Quantità stimata di un rifornimento tipico: si arriva al distributore con circa il 20% nel serbatoio. */
    val typicalRefill: Double get() = tankCapacity * TYPICAL_REFILL_RATIO

    companion object {
        const val DEFAULT_TANK = 45.0
        const val DEFAULT_CONSUMPTION = 6.5
        const val TYPICAL_REFILL_RATIO = 0.8

        fun per100KmToKmPerUnit(per100Km: Double): Double = 100.0 / per100Km
        fun kmPerUnitToPer100Km(kmPerUnit: Double): Double = 100.0 / kmPerUnit
    }
}
