package it.faiilpieno.domain.commute

import it.faiilpieno.domain.brand.BrandGroup
import it.faiilpieno.domain.geo.RoutePosition
import it.faiilpieno.domain.model.CarProfile
import it.faiilpieno.domain.model.FuelPrice
import it.faiilpieno.domain.model.Station
import it.faiilpieno.domain.pricing.NetSavings
import it.faiilpieno.domain.pricing.deltaCents

enum class CommuteSort { PRICE, ALONG_ROUTE }

/** Un distributore vicino al percorso, con il prezzo del carburante cercato. */
data class RouteOffer(
    val station: Station,
    val price: FuelPrice,
    val position: RoutePosition,
)

data class RankedRouteOffer(
    val offer: RouteOffer,
    /** Differenza dalla media nazionale in centesimi; null se la media non è disponibile. */
    val nationalDeltaCents: Int?,
    /** Km in più per fermarsi: andare dal percorso al distributore e tornare. */
    val detourKm: Double,
    /** Risparmio netto rispetto alla media del percorso su un rifornimento tipico, in euro. */
    val netSavingEur: Double,
)

data class CommuteResult(
    val offers: List<RankedRouteOffer>,
    /** Il distributore con il miglior risparmio netto, solo se positivo. */
    val recommended: RankedRouteOffer?,
    /** Prezzo di riferimento usato per il risparmio. */
    val referenceMilli: Int?,
    /** Vero se lungo il percorso ci sono pochi distributori e il riferimento è la media nazionale. */
    val usesNationalAverage: Boolean,
    val quantity: Double,
)

object CommuteRanker {

    /** Sotto questo numero di distributori la media del percorso non è significativa. */
    const val MIN_ZONE_SAMPLES = 3

    /**
     * risparmio netto = (prezzo_medio_zona − prezzo) × quantità − km_deviazione × consumo × prezzo
     *
     * La zona è la fascia attorno al percorso; la deviazione è andata e ritorno dal percorso
     * al distributore, in linea d'aria, come per i distributori vicini. La media usa tutti i
     * distributori del percorso, indipendentemente dal filtro marchi.
     */
    fun rank(
        offers: List<RouteOffer>,
        nationalAverageMilli: Int?,
        car: CarProfile,
        sort: CommuteSort,
        brandFilter: Set<BrandGroup> = emptySet(),
    ): CommuteResult {
        val quantity = car.typicalRefill
        val usesNational = offers.size < MIN_ZONE_SAMPLES
        val reference = if (usesNational) {
            nationalAverageMilli
        } else {
            Math.round(offers.map { it.price.priceMilli }.average()).toInt()
        }

        val ranked = offers
            .filter { BrandGroup.accepts(brandFilter, it.station.brand) }
            .map { offer ->
                val detourKm = 2 * offer.position.distanceFromRouteMeters / 1000.0
                RankedRouteOffer(
                    offer = offer,
                    nationalDeltaCents = nationalAverageMilli?.let { deltaCents(offer.price.priceMilli, it) },
                    detourKm = detourKm,
                    netSavingEur = reference?.let {
                        NetSavings.compute(it, offer.price.priceMilli, quantity, detourKm, car.consumptionPer100Km)
                    } ?: 0.0,
                )
            }
        val sorted = when (sort) {
            CommuteSort.PRICE -> ranked.sortedWith(compareBy({ it.offer.price.priceMilli }, { it.detourKm }))
            CommuteSort.ALONG_ROUTE -> ranked.sortedBy { it.offer.position.alongRouteMeters }
        }
        val recommended = ranked
            .filter { it.netSavingEur > 0.0 }
            .maxWithOrNull(compareBy<RankedRouteOffer> { it.netSavingEur }.thenByDescending { it.detourKm })

        return CommuteResult(sorted, recommended, reference, usesNational && reference != null, quantity)
    }
}
