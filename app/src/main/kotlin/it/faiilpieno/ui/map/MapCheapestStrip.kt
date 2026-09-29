package it.faiilpieno.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.faiilpieno.R
import it.faiilpieno.domain.geo.Haversine
import it.faiilpieno.domain.model.GeoPoint
import it.faiilpieno.domain.nearby.Offer
import it.faiilpieno.domain.pricing.deltaCents
import it.faiilpieno.ui.components.PriceDeltaBadge
import it.faiilpieno.ui.components.PriceText
import it.faiilpieno.ui.format.deltaSpoken
import it.faiilpieno.ui.format.distanceText
import it.faiilpieno.ui.format.priceSpoken

/**
 * Striscia con i distributori più economici dell'area visibile, dal meno caro: la mappa risponde
 * subito a "dove vado?" senza dover leggere tutte le etichette.
 */
@Composable
internal fun CheapestStrip(
    offers: List<Offer>,
    nationalAverage: Int?,
    user: GeoPoint?,
    selected: Long?,
    listState: LazyListState,
    onSelect: (Offer) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Un'area nuova ha una classifica nuova: si riparte sempre dal più economico.
    val ids = offers.map { it.station.id }
    LaunchedEffect(ids) { listState.scrollToItem(0) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Text(
                stringResource(R.string.map_cheapest_title),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp).semantics { heading() },
            )
        }
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(offers, key = { it.station.id }) { offer ->
                CheapestCard(offer, nationalAverage, user, offer.station.id == selected, onClick = { onSelect(offer) })
            }
        }
    }
}

@Composable
private fun CheapestCard(offer: Offer, nationalAverage: Int?, user: GeoPoint?, selected: Boolean, onClick: () -> Unit) {
    val station = offer.station
    val price = offer.price
    val delta = nationalAverage?.let { deltaCents(price.priceMilli, it) }
    val distance = user?.let { u -> station.location?.let { distanceText(Haversine.distanceMeters(u, it)) } }
    val priceLabel = priceSpoken(price.priceMilli, price.category)
    val deltaLabel = delta?.let { deltaSpoken(it) } ?: ""
    val description = if (distance != null) {
        stringResource(R.string.station_row_a11y, station.brand, priceLabel, distance, deltaLabel)
    } else {
        stringResource(R.string.map_card_a11y, station.brand, priceLabel, deltaLabel)
    }
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.width(184.dp).clearAndSetSemantics { contentDescription = description },
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            PriceText(price.priceMilli, price.category, style = MaterialTheme.typography.titleLarge)
            Text(station.brand, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(station.name, station.municipality).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Con il testo ingrandito la distanza va a capo invece di essere tagliata.
            FlowRow(
                itemVerticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                delta?.let { PriceDeltaBadge(it) }
                distance?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}
