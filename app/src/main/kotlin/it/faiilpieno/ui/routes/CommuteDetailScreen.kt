package it.faiilpieno.ui.routes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.faiilpieno.R
import it.faiilpieno.data.prefs.PreferencesRepository
import it.faiilpieno.data.repository.RouteJob
import it.faiilpieno.domain.commute.Commute
import it.faiilpieno.domain.commute.CommuteResult
import it.faiilpieno.domain.commute.CommuteSort
import it.faiilpieno.ui.components.ConnectedChoiceRow
import it.faiilpieno.ui.components.EmptyState
import it.faiilpieno.ui.components.InfoBanner
import it.faiilpieno.ui.components.LoadingState
import it.faiilpieno.ui.components.rememberFullSheetState
import it.faiilpieno.ui.map.rememberMapCameraState
import it.faiilpieno.ui.format.Fmt
import it.faiilpieno.ui.format.distanceText
import it.faiilpieno.ui.format.fuelAndModeLabel
import it.faiilpieno.ui.format.fuelLabel
import it.faiilpieno.ui.settings.PreferencesViewModel
import it.faiilpieno.ui.settings.RoutePreferencesCard
import it.faiilpieno.ui.station.StationDetailSheet
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommuteDetailScreen(onBack: () -> Unit, onEdit: (Long) -> Unit, viewModel: CommuteDetailViewModel = hiltViewModel(), preferencesViewModel: PreferencesViewModel = hiltViewModel()) {
    val preferences by preferencesViewModel.state.collectAsStateWithLifecycle()
    var showOptions by rememberSaveable { mutableStateOf(false) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedStation by rememberSaveable { mutableStateOf<Long?>(null) }
    val commute = state.commute
    val mapCamera = rememberMapCameraState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (commute != null) {
                        val spoken = commuteTitleSpoken(commute)
                        Text(
                            commuteTitle(commute),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics {
                                contentDescription = spoken
                                heading()
                            },
                        )
                    }
                },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (commute != null) {
                        IconButton(onClick = { onEdit(commute.id) }) {
                            Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.action_edit))
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            !state.loaded -> LoadingState(stringResource(R.string.commute_computing), Modifier.padding(padding))
            commute == null -> EmptyState(
                R.drawable.ic_route,
                stringResource(R.string.commute_not_found),
                "",
                Modifier.padding(padding),
                actionLabel = stringResource(R.string.action_back),
                onAction = onBack,
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                item(key = "info") { RouteInfo(commute, state.job, viewModel::recomputeRoute) }
                commute.route?.let { route ->
                    item(key = "map") { RouteMap(route, mapCamera, Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
                }
                item(key = "options") {
                    FilledTonalButton(onClick = { showOptions = true }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 48.dp)) {
                        Icon(painterResource(R.drawable.ic_filter), contentDescription = null)
                        Text(stringResource(R.string.route_options), Modifier.padding(start = 8.dp))
                    }
                }
                if (state.brands.isNotEmpty()) {
                    item(key = "brands") {
                        InfoBanner(
                            R.drawable.ic_filter,
                            stringResource(R.string.commute_brand_filter_on),
                            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            actionLabel = stringResource(R.string.action_clear_brands),
                            onAction = viewModel::clearBrands,
                        )
                    }
                }
                results(state, commute, viewModel, onSelect = { selectedStation = it })
                item(key = "recompute") {
                    TextButton(
                        onClick = viewModel::recomputeRoute,
                        enabled = state.job != RouteJob.Running,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp).heightIn(min = 48.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_refresh), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.commute_recompute), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }

    if (showOptions) ModalBottomSheet(onDismissRequest = { showOptions = false }, sheetState = rememberFullSheetState()) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RoutePreferencesCard(preferences.route, preferencesViewModel::setAvoidance,
                enabled = preferences.loaded, buffer = preferences.buffer, onBufferChange = preferencesViewModel::setBuffer)
            Text(stringResource(R.string.route_options_shared), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(onClick = { showOptions = false }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_close)) }
        }
    }
    selectedStation?.let { id -> StationDetailSheet(id, onDismiss = { selectedStation = null }) }
}

@Composable
private fun RouteInfo(commute: Commute, job: RouteJob?, onRetry: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            listOfNotNull(daysSummary(commute.days), stringResource(R.string.commute_round_trip).takeIf { commute.roundTrip }).joinToString(" · "),
            style = MaterialTheme.typography.bodyLarge,
        )
        val route = commute.route
        when {
            job == RouteJob.Running -> LoadingState(stringResource(R.string.commute_computing))
            job is RouteJob.Failed -> RouteProblem(routeErrorText(job.reason), onRetry)
            route == null -> RouteProblem(stringResource(R.string.commute_not_computed), onRetry)
            else -> Text(
                stringResource(
                    R.string.commute_route_info,
                    distanceText(route.distanceMeters),
                    durationText(route.durationSeconds),
                    Fmt.dayMonth(route.computedAt.atZone(ZoneId.systemDefault()).toLocalDate()),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RouteProblem(text: String, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
        FilledTonalButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(painterResource(R.drawable.ic_refresh), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.action_retry), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

private fun LazyListScope.results(
    state: CommuteDetailState,
    commute: Commute,
    viewModel: CommuteDetailViewModel,
    onSelect: (Long) -> Unit,
) {
    when (val results = state.results) {
        DetailResults.Unavailable -> Unit
        DetailResults.Loading -> item(key = "loading") { LoadingState(stringResource(R.string.state_locating)) }
        is DetailResults.Ready -> {
            val result = results.result
            if (result.offers.isEmpty()) {
                item(key = "empty") { EmptyResults(state, viewModel) }
                return
            }
            item(key = "hero") { Hero(result, commute, onSelect) }
            item(key = "list-header") { ListHeader(result, state, viewModel::setSort) }
            items(result.offers, key = { "s" + it.offer.station.id }) { ranked ->
                RouteStationRow(ranked, commute.from.label, onClick = { onSelect(ranked.offer.station.id) })
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun Hero(result: CommuteResult, commute: Commute, onSelect: (Long) -> Unit) {
    val recommended = result.recommended
    if (recommended == null) {
        InfoBanner(R.drawable.ic_equal, stringResource(R.string.commute_no_best), Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        return
    }
    val label = stringResource(R.string.hero_label)
    CommuteAdviceCard(
        label = label,
        ranked = recommended,
        result = result,
        fromLabel = commute.from.label,
        spokenLabel = label,
        onDetails = onSelect,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ListHeader(result: CommuteResult, state: CommuteDetailState, onSort: (CommuteSort) -> Unit) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)) {
        Text(
            stringResource(R.string.commute_stations_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            pluralStringResource(
                R.plurals.commute_stations_subtitle,
                result.offers.size,
                result.offers.size,
                distanceText(state.bufferMeters.toDouble()),
                fuelAndModeLabel(state.car.fuel, state.car.serviceMode),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(12.dp))
        ConnectedChoiceRow(
            options = CommuteSort.entries,
            selected = state.sort,
            label = { stringResource(if (it == CommuteSort.PRICE) R.string.commute_sort_price else R.string.commute_sort_along) },
            onSelect = onSort,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EmptyResults(state: CommuteDetailState, viewModel: CommuteDetailViewModel) {
    val radius = distanceText(state.bufferMeters.toDouble())
    val wider = PreferencesRepository.ROUTE_BUFFER_OPTIONS.firstOrNull { it > state.bufferMeters }
    if (state.brands.isNotEmpty()) {
        EmptyState(
            R.drawable.ic_filter,
            stringResource(R.string.commute_empty_title),
            stringResource(R.string.commute_empty_brand_text, radius),
            actionLabel = stringResource(R.string.action_clear_brands),
            onAction = viewModel::clearBrands,
        )
    } else {
        EmptyState(
            R.drawable.ic_gas_station,
            stringResource(R.string.commute_empty_title),
            stringResource(R.string.commute_empty_text, radius, fuelLabel(state.car.fuel)),
            actionLabel = wider?.let { stringResource(R.string.commute_widen, distanceText(it.toDouble())) },
            onAction = wider?.let { { viewModel.setBuffer(it) } },
        )
    }
}
