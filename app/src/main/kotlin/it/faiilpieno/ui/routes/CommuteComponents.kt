package it.faiilpieno.ui.routes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.faiilpieno.R
import it.faiilpieno.domain.commute.CommuteResult
import it.faiilpieno.domain.commute.RankedRouteOffer
import it.faiilpieno.ui.components.PriceDeltaBadge
import it.faiilpieno.ui.components.PriceText
import it.faiilpieno.ui.format.deltaSpoken
import it.faiilpieno.ui.format.distanceText
import it.faiilpieno.ui.format.eurosText
import it.faiilpieno.ui.format.priceSpoken
import it.faiilpieno.ui.format.quantityUnitLabel
import it.faiilpieno.ui.station.openNavigation
import it.faiilpieno.ui.theme.bold
import it.faiilpieno.ui.theme.tabular
import kotlin.math.roundToInt

/** "dopo 4,2 km da Casa · 150 m dal percorso" */
@Composable
fun routePositionText(ranked: RankedRouteOffer, fromLabel: String): String {
    val position = ranked.offer.position
    return stringResource(
        R.string.commute_offer_position,
        distanceText(position.alongRouteMeters),
        fromLabel,
        distanceText(position.distanceFromRouteMeters),
    )
}

/** Spiega rispetto a cosa è calcolato il risparmio. */
@Composable
fun savingReferenceText(result: CommuteResult, ranked: RankedRouteOffer): String = stringResource(
    if (result.usesNationalAverage) R.string.commute_reference_national else R.string.commute_reference_zone,
    result.quantity.roundToInt().toString(),
    quantityUnitLabel(ranked.offer.price.category),
)

/**
 * Card del distributore consigliato lungo un tragitto: dove, quanto costa, quanto si risparmia.
 * Usata in cima alla scheda Oggi e nel dettaglio del tragitto.
 */
@Composable
fun CommuteAdviceCard(
    label: String,
    ranked: RankedRouteOffer,
    result: CommuteResult,
    fromLabel: String,
    spokenLabel: String,
    onDetails: (Long) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    val station = ranked.offer.station
    val price = ranked.offer.price
    val context = LocalContext.current
    val position = routePositionText(ranked, fromLabel)
    val saving = stringResource(R.string.hero_saving, eurosText(ranked.netSavingEur))
    val description = stringResource(
        R.string.commute_advice_a11y,
        spokenLabel,
        station.brand,
        position,
        priceSpoken(price.priceMilli, price.category),
        saving,
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.clearAndSetSemantics { contentDescription = description }) {
                Text(label.uppercase(), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.size(4.dp))
                Text(station.brand, style = MaterialTheme.typography.headlineSmall)
                Text(
                    listOf(station.name, station.address).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(position, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.size(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    PriceText(price.priceMilli, price.category, style = MaterialTheme.typography.displaySmall)
                    ranked.nationalDeltaCents?.let { PriceDeltaBadge(it, large = true) }
                }
                Spacer(Modifier.size(8.dp))
                Text(saving, style = MaterialTheme.typography.headlineMedium.bold().tabular())
                Text(savingReferenceText(result, ranked), style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { openNavigation(context, station) },
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_directions), contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.action_navigate), modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = { onDetails(station.id) }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_details))
                }
            }
            if (secondaryActionLabel != null && onSecondaryAction != null) {
                TextButton(onClick = onSecondaryAction, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(painterResource(R.drawable.ic_route), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(secondaryActionLabel, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/** Riga di un distributore lungo il percorso. */
@Composable
fun RouteStationRow(ranked: RankedRouteOffer, fromLabel: String, onClick: () -> Unit) {
    val station = ranked.offer.station
    val price = ranked.offer.price
    val position = routePositionText(ranked, fromLabel)
    val deltaText = ranked.nationalDeltaCents?.let { deltaSpoken(it) } ?: ""
    val description = stringResource(
        R.string.commute_station_row_a11y,
        station.brand,
        priceSpoken(price.priceMilli, price.category),
        position,
        deltaText,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, role = Role.Button)
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Column(Modifier.weight(1f)) {
            Text(station.brand, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(station.name, station.municipality).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(position, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PriceText(price.priceMilli, price.category, style = MaterialTheme.typography.headlineSmall)
            ranked.nationalDeltaCents?.let { PriceDeltaBadge(it) }
        }
    }
}
