package it.faiilpieno.ui.routes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.faiilpieno.R
import it.faiilpieno.data.repository.RouteJob
import it.faiilpieno.domain.commute.Place
import it.faiilpieno.domain.commute.PlaceKind
import it.faiilpieno.ui.components.EmptyState
import it.faiilpieno.ui.components.InfoBanner
import it.faiilpieno.ui.components.LoadingState
import it.faiilpieno.ui.format.distanceText
import it.faiilpieno.ui.format.eurosText
import it.faiilpieno.ui.format.priceText

@Composable
fun RoutesScreen(
    onAddPlace: (PlaceKind?) -> Unit,
    onEditPlace: (Long) -> Unit,
    onAddCommute: () -> Unit,
    onOpenCommute: (Long) -> Unit,
    viewModel: RoutesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
        item(key = "title") {
            Text(
                stringResource(R.string.routes_title),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp).semantics { heading() },
            )
        }
        if (!state.hasApiKey) {
            item(key = "no-key") {
                InfoBanner(R.drawable.ic_warning, stringResource(R.string.routes_no_key), Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
        }
        when {
            state.loading -> item(key = "loading") { LoadingState(stringResource(R.string.commute_computing)) }
            state.places.isEmpty() -> item(key = "empty") {
                EmptyState(
                    R.drawable.ic_route,
                    stringResource(R.string.routes_empty_title),
                    stringResource(R.string.routes_empty_text),
                    actionLabel = stringResource(R.string.routes_add_home),
                    onAction = { onAddPlace(PlaceKind.HOME) },
                    secondaryLabel = stringResource(R.string.routes_add_place),
                    onSecondary = { onAddPlace(null) },
                )
            }
            else -> {
                commutesSection(state, onAddCommute, onOpenCommute, viewModel::retryRoute)
                placesSection(state.places, onAddPlace, onEditPlace)
            }
        }
    }
}

private fun LazyListScope.commutesSection(
    state: RoutesUiState,
    onAddCommute: () -> Unit,
    onOpenCommute: (Long) -> Unit,
    onRetry: (Long) -> Unit,
) {
    item(key = "commutes-title") { SectionTitle(stringResource(R.string.routes_commutes)) }
    if (state.commutes.isEmpty()) {
        item(key = "commutes-empty") {
            Text(
                stringResource(if (state.places.size < 2) R.string.routes_need_two_places else R.string.routes_no_commutes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
    items(state.commutes, key = { "c" + it.commute.id }) { card ->
        CommuteCardView(card, onClick = { onOpenCommute(card.commute.id) }, onRetry = { onRetry(card.commute.id) })
    }
    item(key = "add-commute") {
        FilledTonalButton(
            onClick = onAddCommute,
            enabled = state.places.size >= 2,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 48.dp),
        ) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.routes_add_commute), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun CommuteCardView(card: CommuteCard, onClick: () -> Unit, onRetry: () -> Unit) {
    val commute = card.commute
    val route = commute.route
    val days = daysSummary(commute.days)
    val status: String = when {
        card.job == RouteJob.Running -> stringResource(R.string.commute_computing)
        card.job is RouteJob.Failed -> routeErrorText(card.job.reason)
        route == null -> stringResource(R.string.commute_not_computed)
        else -> {
            val best = card.result?.recommended
            if (best != null) {
                stringResource(
                    R.string.commute_best,
                    best.offer.station.brand,
                    priceText(best.offer.price.priceMilli, best.offer.price.category),
                    eurosText(best.netSavingEur),
                )
            } else {
                stringResource(R.string.commute_no_best)
            }
        }
    }
    val info = route?.let { stringResource(R.string.commute_distance_duration, distanceText(it.distanceMeters), durationText(it.durationSeconds)) }
    val roundTrip = if (commute.roundTrip) stringResource(R.string.commute_round_trip) else null
    val description = stringResource(
        R.string.commute_card_a11y,
        commuteTitleSpoken(commute),
        listOfNotNull(days, roundTrip, info).joinToString(", "),
        status,
    )

    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.clearAndSetSemantics { contentDescription = description }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_route), contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        commuteTitle(commute),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp).weight(1f),
                    )
                    if (commute.roundTrip) {
                        Icon(painterResource(R.drawable.ic_swap), contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
                Text(
                    listOfNotNull(days, info).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    status,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (card.job is RouteJob.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (card.job is RouteJob.Failed || (route == null && card.job == null)) {
                TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(painterResource(R.drawable.ic_refresh), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.action_retry), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

private fun LazyListScope.placesSection(places: List<Place>, onAddPlace: (PlaceKind?) -> Unit, onEditPlace: (Long) -> Unit) {
    item(key = "places-title") { SectionTitle(stringResource(R.string.routes_places)) }
    items(places, key = { "p" + it.id }) { place ->
        PlaceRow(place, onClick = { onEditPlace(place.id) })
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
    }
    item(key = "add-place") {
        OutlinedButton(
            onClick = { onAddPlace(null) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 48.dp),
        ) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.routes_add_place), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun PlaceRow(place: Place, onClick: () -> Unit) {
    val kind = placeKindLabel(place.kind)
    val description = listOfNotNull(place.label, kind.takeIf { it != place.label }, place.address).joinToString(", ")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, role = Role.Button)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Icon(painterResource(placeKindIcon(place.kind)), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text(place.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                place.address ?: stringResource(R.string.place_current_position),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(painterResource(R.drawable.ic_edit), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp).semantics { heading() },
    )
}
