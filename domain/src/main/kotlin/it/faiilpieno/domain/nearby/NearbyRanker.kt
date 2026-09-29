package it.faiilpieno.domain.nearby

import it.faiilpieno.domain.brand.BrandGroup
import it.faiilpieno.domain.model.CarProfile
import it.faiilpieno.domain.model.FuelPrice
import it.faiilpieno.domain.model.Station
import it.faiilpieno.domain.pricing.NetSavings
import it.faiilpieno.domain.pricing.deltaCents

enum class SortMode { PRICE, DISTANCE }

/** Un distributore con il prezzo del carburante cercato e la distanza dall'utente. */
data class Offer(
    val station: Station,
    val price: FuelPrice,
    val distanceMeters: Double,
)

data class RankedOffer(
    val offer: Offer,
    /** Differenza dalla media nazionale in centesimi; null se la media non è disponibile. */
    val nationalDeltaCents: Int?,
    /** Km in più rispetto al distributore più vicino, andata e ritorno. */
    val detourKm: Double,
    /** Risparmio netto rispetto alla media della zona su un rifornimento tipico, in euro. */
    val netSavingEur: Double,
)

data class NearbyResult(
    val offers: List<RankedOffer>,
    /** Il distributore con il miglior risparmio netto, solo se positivo. */
    val recommended: RankedOffer?,
    val zoneAverageMilli: Int?,
    val quantity: Double,
)

object NearbyRanker {

    fun rank(
        offers: List<Offer>,
        nationalAverageMilli: Int?,
        car: CarProfile,
        sort: SortMode,
        brandFilter: Set<BrandGroup> = emptySet(),
    ): NearbyResult {
        val quantity = car.typicalRefill
        if (offers.isEmpty()) return NearbyResult(emptyList(), null, null, quantity)

        // La media di zona usa tutti i distributori vicini, indipendentemente dal filtro marchi.
        val zoneAverage = offers.map { it.price.priceMilli }.average().let { Math.round(it).toInt() }
        val nearestMeters = offers.minOf { it.distanceMeters }

        val ranked = offers
            .filter { BrandGroup.accepts(brandFilter, it.station.brand) }
            .map { offer ->
                val detourKm = 2 * (offer.distanceMeters - nearestMeters) / 1000.0
                RankedOffer(
                    offer = offer,
                    nationalDeltaCents = nationalAverageMilli?.let { deltaCents(offer.price.priceMilli, it) },
                    detourKm = detourKm,
                    netSavingEur = NetSavings.compute(
                        zoneAverageMilli = zoneAverage,
                        priceMilli = offer.price.priceMilli,
                        quantity = quantity,
                        detourKm = detourKm,
                        consumptionPer100Km = car.consumptionPer100Km,
                    ),
                )
            }

        val sorted = when (sort) {
            SortMode.PRICE -> ranked.sortedWith(compareBy({ it.offer.price.priceMilli }, { it.offer.distanceMeters }))
            SortMode.DISTANCE -> ranked.sortedWith(compareBy({ it.offer.distanceMeters }, { it.offer.price.priceMilli }))
        }
        val recommended = ranked
            .filter { it.netSavingEur > 0.0 }
            .maxWithOrNull(compareBy<RankedOffer> { it.netSavingEur }.thenByDescending { it.offer.distanceMeters })

        return NearbyResult(sorted, recommended, zoneAverage, quantity)
    }
}
