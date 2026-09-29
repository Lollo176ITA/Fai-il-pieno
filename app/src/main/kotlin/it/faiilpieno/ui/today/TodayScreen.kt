package it.faiilpieno.ui.today

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.faiilpieno.R
import it.faiilpieno.data.work.SyncStatus
import it.faiilpieno.domain.nearby.NearbyResult
import it.faiilpieno.domain.nearby.RankedOffer
import it.faiilpieno.domain.nearby.SortMode
import it.faiilpieno.domain.tank.ReserveAlert
import it.faiilpieno.ui.components.ConnectedChoiceRow
import it.faiilpieno.ui.components.EmptyState
import it.faiilpieno.ui.components.InfoBanner
import it.faiilpieno.ui.components.LoadingState
import it.faiilpieno.ui.components.PriceDeltaBadge
import it.faiilpieno.ui.components.PriceText
import it.faiilpieno.ui.format.Fmt
import it.faiilpieno.ui.format.deltaSpoken
import it.faiilpieno.ui.format.distanceText
import it.faiilpieno.ui.format.eurosText
import it.faiilpieno.ui.format.fuelAndModeLabel
import it.faiilpieno.ui.format.fuelLabel
import it.faiilpieno.ui.format.kmText
import it.faiilpieno.ui.format.priceSpoken
import it.faiilpieno.ui.format.quantityUnitLabel
import it.faiilpieno.ui.refuel.RefuelSheet
import it.faiilpieno.ui.routes.CommuteAdviceCard
import it.faiilpieno.ui.routes.commuteTitle
import it.faiilpieno.ui.routes.commuteTitleSpoken
import it.faiilpieno.ui.routes.whenLabel
import it.faiilpieno.ui.station.StationDetailSheet
import it.faiilpieno.ui.station.openNavigation
import it.faiilpieno.ui.theme.bold
import it.faiilpieno.ui.theme.tabular
import java.time.LocalDate
import kotlin.math.roundToInt

private val locationPermissions = arrayOf(
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ACCESS_FINE_LOCATION,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(onOpenCar: () -> Unit, onOpenCommute: (Long) -> Unit, viewModel: TodayViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedStation by rememberSaveable { mutableStateOf<Long?>(null) }
    var showRefuel by rememberSaveable { mutableStateOf(false) }

    val results = state.results

    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }

    PullToRefreshBox(
        isRefreshing = (results as? ResultsState.Ready)?.isRefreshing == true,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") {
                Header(state, onRefuel = { showRefuel = true })
            }
            banners(state, viewModel, onOpenCar, onRefuel = { showRefuel = true })
            state.commuteAdvice?.let { advice ->
                item(key = "commute") {
                    CommuteCard(advice, onDetails = { selectedStation = it }, onOpenCommute = onOpenCommute)
                }
            }
            body(state, results, viewModel, onSelect = { selectedStation = it })
        }
    }

    selectedStation?.let { id -> StationDetailSheet(id, onDismiss = { selectedStation = null }) }
    if (showRefuel) RefuelSheet(stationId = null, onDismiss = { showRefuel = false })

}

@Composable
private fun Header(state: TodayUiState, onRefuel: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        val fuel = fuelAndModeLabel(state.car.fuel, state.car.serviceMode)
        Text(
            // La data dei prezzi resta sempre in vista: sono quelli delle 8 del giorno indicato.
            state.dataset?.let { stringResource(R.string.today_header, fuel, Fmt.dayMonth(it.extractionDate)) } ?: fuel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
        )
        TextButton(onClick = onRefuel, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.refuel_action), Modifier.padding(start = 4.dp))
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.banners(
    state: TodayUiState,
    viewModel: TodayViewModel,
    onOpenCar: () -> Unit,
    onRefuel: () -> Unit,
) {
    val info = state.dataset ?: return
    val date = Fmt.dayMonth(info.extractionDate)
    when {
        !state.isOnline && state.isDataStale -> item(key = "offline") {
            InfoBanner(R.drawable.ic_cloud_off, stringResource(R.string.warning_offline, date), Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        state.sync == SyncStatus.Running -> item(key = "updating") {
            InfoBanner(R.drawable.ic_refresh, stringResource(R.string.warning_updating), Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        state.isDataStale -> item(key = "stale") {
            InfoBanner(
                R.drawable.ic_warning,
                stringResource(R.string.warning_stale_data, date),
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                actionLabel = stringResource(R.string.action_retry),
                onAction = viewModel::retryDownload,
            )
        }
    }
    state.tank?.takeIf { ReserveAlert.shouldAlert(it, LocalDate.now(), ReserveAlert.BANNER_DAYS) }?.let { tank ->
        item(key = "reserve") {
            val days = tank.daysToReserve(LocalDate.now()) ?: 0
            InfoBanner(
                R.drawable.ic_warning,
                when {
                    days <= 0 -> stringResource(R.string.tank_in_reserve)
                    days == 1L -> stringResource(R.string.today_reserve_tomorrow)
                    else -> pluralStringResource(R.plurals.today_reserve_days, days.toInt(), days.toInt())
                },
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                actionLabel = stringResource(R.string.refuel_action),
                onAction = onRefuel,
            )
        }
    }
    if (!state.car.isConfigured) {
        item(key = "setup-car") {
            InfoBanner(
                R.drawable.ic_car,
                stringResource(R.string.setup_car_hint),
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                actionLabel = stringResource(R.string.action_setup_car),
                onAction = onOpenCar,
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.body(
    state: TodayUiState,
    results: ResultsState,
    viewModel: TodayViewModel,
    onSelect: (Long) -> Unit,
) {
    if (state.dataset == null) {
        item(key = "no-data") { NoDataState(state.sync, viewModel::retryDownload) }
        return
    }
    when (state.location) {
        LocationState.Locating -> {
            item(key = "locating") { LoadingState(stringResource(R.string.state_locating)) }
            return
        }
        LocationState.PermissionNeeded -> {
            item(key = "permission") { PermissionState(onResult = viewModel::refreshLocation) }
            return
        }
        LocationState.ServicesOff -> {
            item(key = "gps-off") {
                val context = LocalContext.current
                EmptyState(
                    R.drawable.ic_location_off,
                    stringResource(R.string.state_location_off_title),
                    stringResource(R.string.state_location_off_text),
                    actionLabel = stringResource(R.string.action_enable_location),
                    onAction = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                )
            }
            return
        }
        LocationState.Unavailable -> {
            item(key = "no-location") {
                EmptyState(
                    R.drawable.ic_location_off,
                    stringResource(R.string.state_location_unavailable_title),
                    stringResource(R.string.state_location_unavailable_text),
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = viewModel::refreshLocation,
                )
            }
            return
        }
        is LocationState.Found -> Unit
    }

    when (results) {
        ResultsState.Idle, ResultsState.Loading -> item(key = "loading") { LoadingState(stringResource(R.string.state_locating)) }
        is ResultsState.Ready -> {
            val result = results.result
            if (result.offers.isEmpty()) {
                item(key = "empty") { EmptyResults(state, results.radiusMeters, onClearBrands = { viewModel.setBrands(emptySet()) }) }
                return
            }
            item(key = "hero") { HeroCard(result, state, onDetails = onSelect) }
            item(key = "list-header") {
                ListHeader(result.offers.size, results.radiusMeters, state, viewModel::setSortMode)
            }
            items(result.offers, key = { "s" + it.offer.station.id }) { ranked ->
                StationRow(ranked, onClick = { onSelect(ranked.offer.station.id) })
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun NoDataState(sync: SyncStatus, onRetry: () -> Unit) {
    when (sync) {
        SyncStatus.Idle, SyncStatus.Running -> LoadingState(
            stringResource(R.string.state_downloading_title),
            detail = stringResource(R.string.state_downloading_text),
        )
        SyncStatus.WaitingForNetwork -> EmptyState(
            R.drawable.ic_cloud_off,
            stringResource(R.string.state_offline_title),
            stringResource(R.string.state_offline_text),
            actionLabel = stringResource(R.string.action_retry),
            onAction = onRetry,
        )
        is SyncStatus.Failed -> EmptyState(
            R.drawable.ic_warning,
            stringResource(R.string.state_download_failed_title),
            stringResource(
                if (sync.reason == SyncStatus.Reason.FORMAT) R.string.state_format_error_text
                else R.string.state_download_failed_text,
            ),
            actionLabel = stringResource(R.string.action_retry),
            onAction = onRetry,
        )
    }
}

@Composable
private fun PermissionState(onResult: () -> Unit) {
    val activity = LocalActivity.current
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        asked = true
        onResult()
    }
    // Dopo un rifiuto definitivo il sistema non mostra più la richiesta: resta solo la via delle impostazioni.
    val permanentlyDenied = asked && activity != null &&
        !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
    if (permanentlyDenied) {
        EmptyState(
            R.drawable.ic_location_off,
            stringResource(R.string.state_permission_title),
            stringResource(R.string.state_permission_denied_text),
            actionLabel = stringResource(R.string.action_open_settings),
            onAction = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            },
        )
    } else {
        EmptyState(
            R.drawable.ic_my_location,
            stringResource(R.string.state_permission_title),
            stringResource(R.string.state_permission_text),
            actionLabel = stringResource(R.string.action_allow_location),
            onAction = { launcher.launch(locationPermissions) },
        )
    }
}

@Composable
private fun EmptyResults(state: TodayUiState, radiusMeters: Double, onClearBrands: () -> Unit) {
    val radius = distanceText(radiusMeters)
    if (state.search.brands.isNotEmpty()) {
        EmptyState(
            R.drawable.ic_filter,
            stringResource(R.string.state_empty_title),
            stringResource(R.string.state_empty_brand_text, radius),
            actionLabel = stringResource(R.string.action_clear_brands),
            onAction = onClearBrands,
        )
    } else {
        EmptyState(
            R.drawable.ic_gas_station,
            stringResource(R.string.state_empty_title),
            stringResource(R.string.state_empty_text, radius, fuelLabel(state.car.fuel)),
        )
    }
}

@Composable
private fun HeroCard(result: NearbyResult, state: TodayUiState, onDetails: (Long) -> Unit) {
    val recommended = result.recommended
    val shown: RankedOffer = recommended ?: result.offers.minBy { it.offer.distanceMeters }
    val station = shown.offer.station
    val price = shown.offer.price
    val context = LocalContext.current

    val priceLabel = priceSpoken(price.priceMilli, price.category)
    val distance = distanceText(shown.offer.distanceMeters)
    val savingText = if (recommended != null) stringResource(R.string.hero_saving, eurosText(recommended.netSavingEur)) else stringResource(R.string.hero_no_saving)
    val heroDescription = stringResource(R.string.hero_a11y, station.brand, distance, priceLabel, savingText)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.clearAndSetSemantics { contentDescription = heroDescription }) {
                Text(
                    stringResource(if (recommended != null) R.string.hero_label else R.string.hero_label_nearest).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.size(4.dp))
                Text(station.brand, style = MaterialTheme.typography.headlineSmall)
                Text(
                    listOf(station.name, station.address).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(stringResource(R.string.hero_distance, distance))
                        if (shown.detourKm >= 0.1) append(" · ").append(stringResource(R.string.hero_detour, kmText(shown.detourKm)))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.size(8.dp))
                FlowRow(
                    verticalArrangement = Arrangement.Center,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    PriceText(price.priceMilli, price.category, style = MaterialTheme.typography.displayMedium)
                    shown.nationalDeltaCents?.let { PriceDeltaBadge(it, large = true) }
                }
                Spacer(Modifier.size(8.dp))
                if (recommended != null) {
                    Text(savingText, style = MaterialTheme.typography.headlineMedium.bold().tabular())
                    Text(
                        stringResource(
                            R.string.hero_saving_detail,
                            result.quantity.roundToInt().toString(),
                            quantityUnitLabel(price.category),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(savingText, style = MaterialTheme.typography.bodyLarge)
                }
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
        }
    }
}

/** Consiglio sul tragitto abituale: sta sopra al distributore vicino perché è il più utile. */
@Composable
private fun CommuteCard(advice: CommuteAdvice, onDetails: (Long) -> Unit, onOpenCommute: (Long) -> Unit) {
    val day = whenLabel(advice.date)
    CommuteAdviceCard(
        label = stringResource(R.string.today_commute_label, day, commuteTitle(advice.commute)),
        spokenLabel = stringResource(R.string.today_commute_label, day, commuteTitleSpoken(advice.commute)),
        ranked = advice.best,
        result = advice.result,
        fromLabel = advice.commute.from.label,
        onDetails = onDetails,
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        secondaryActionLabel = stringResource(R.string.today_commute_open),
        onSecondaryAction = { onOpenCommute(advice.commute.id) },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ListHeader(count: Int, radiusMeters: Double, state: TodayUiState, onSort: (SortMode) -> Unit) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)) {
        Text(
            stringResource(R.string.list_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            pluralStringResource(
                R.plurals.list_subtitle,
                count,
                count,
                distanceText(radiusMeters),
                fuelAndModeLabel(state.car.fuel, state.car.serviceMode),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(12.dp))
        ConnectedChoiceRow(
            options = SortMode.entries,
            selected = state.search.sortMode,
            label = { stringResource(if (it == SortMode.PRICE) R.string.sort_price else R.string.sort_distance) },
            onSelect = onSort,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StationRow(ranked: RankedOffer, onClick: () -> Unit) {
    val station = ranked.offer.station
    val price = ranked.offer.price
    val distance = distanceText(ranked.offer.distanceMeters)
    val priceLabel = priceSpoken(price.priceMilli, price.category)
    val deltaText = ranked.nationalDeltaCents?.let { deltaSpoken(it) } ?: ""
    val description = stringResource(R.string.station_row_a11y, station.brand, priceLabel, distance, deltaText)

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
            Text(distance, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PriceText(price.priceMilli, price.category, style = MaterialTheme.typography.headlineSmall)
            ranked.nationalDeltaCents?.let { PriceDeltaBadge(it) }
        }
    }
}
