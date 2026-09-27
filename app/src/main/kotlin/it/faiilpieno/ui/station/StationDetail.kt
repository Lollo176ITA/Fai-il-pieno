package it.faiilpieno.ui.station

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.faiilpieno.R
import it.faiilpieno.data.repository.PriceRepository
import it.faiilpieno.data.repository.StationDetail
import it.faiilpieno.domain.model.FuelPrice
import it.faiilpieno.domain.model.Station
import it.faiilpieno.domain.model.StationType
import it.faiilpieno.ui.components.PriceText
import it.faiilpieno.ui.components.rememberFullSheetState
import it.faiilpieno.ui.format.Fmt
import it.faiilpieno.ui.format.fuelLabel
import it.faiilpieno.ui.format.priceSpoken
import it.faiilpieno.ui.refuel.RefuelSheet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StationDetailViewModel @Inject constructor(private val prices: PriceRepository) : ViewModel() {
    private val _detail = MutableStateFlow<StationDetail?>(null)
    val detail: StateFlow<StationDetail?> = _detail.asStateFlow()

    fun load(id: Long) {
        if (_detail.value?.station?.id == id) return
        _detail.value = null
        viewModelScope.launch { _detail.value = prices.stationDetail(id) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationDetailSheet(stationId: Long, onDismiss: () -> Unit) {
    // "Ho fatto il pieno qui" sostituisce il dettaglio con il foglio del rifornimento.
    var refueling by rememberSaveable(stationId) { mutableStateOf(false) }
    if (refueling) {
        RefuelSheet(stationId = stationId, onDismiss = onDismiss)
        return
    }
    val viewModel: StationDetailViewModel = hiltViewModel()
    LaunchedEffect(stationId) { viewModel.load(stationId) }
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val context = LocalContext.current

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberFullSheetState()) {
        val d = detail ?: return@ModalBottomSheet
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StationHeader(d.station)
            Button(
                onClick = { openNavigation(context, d.station) },
                enabled = d.station.location != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Icon(painterResource(R.drawable.ic_directions), contentDescription = null, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.action_navigate), modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = { refueling = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(painterResource(R.drawable.ic_gas_station), contentDescription = null, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.refuel_here), modifier = Modifier.padding(start = 8.dp))
            }
            PriceTable(d)
        }
    }
}

@Composable
private fun StationHeader(station: Station) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(station.brand, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        if (station.name.isNotBlank()) {
            Text(station.name, style = MaterialTheme.typography.titleMedium)
        }
        Text(
            listOf(station.address, station.municipality, station.province).filter { it.isNotBlank() }.joinToString(", "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (station.type == StationType.AUTOSTRADALE) {
            Text(stringResource(R.string.detail_highway), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

@Composable
private fun PriceTable(detail: StationDetail) {
    if (detail.prices.isEmpty()) {
        Text(stringResource(R.string.detail_no_prices), style = MaterialTheme.typography.bodyLarge)
        return
    }
    val (base, other) = detail.prices.partition { it.isBase }
    if (base.isNotEmpty()) {
        Text(stringResource(R.string.detail_prices), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        base.forEach { PriceRow(it, it.fuelLabel(), detail) }
    }
    if (other.isNotEmpty()) {
        HorizontalDivider()
        Text(stringResource(R.string.detail_other_fuels), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        other.forEach { PriceRow(it, it.fuelRaw, detail) }
    }
}

@Composable
private fun FuelPrice.fuelLabel(): String = fuelLabel(category)

@Composable
private fun PriceRow(price: FuelPrice, label: String, detail: StationDetail) {
    val stale = detail.freshSince?.let { price.communicatedAt < it } ?: false
    val suspect = price in detail.suspect
    val mode = stringResource(if (price.isSelf) R.string.mode_self else R.string.mode_servito)
    val note = when {
        suspect -> stringResource(R.string.detail_suspect)
        stale -> stringResource(R.string.detail_stale, Fmt.shortDate(price.communicatedAt))
        else -> stringResource(R.string.detail_updated, Fmt.shortDate(price.communicatedAt))
    }
    val warn = stale || suspect
    val priceLabel = priceSpoken(price.priceMilli, price.category)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clearAndSetSemantics { contentDescription = "$label $mode: $priceLabel, $note" },
    ) {
        Column(Modifier.weight(1f)) {
            Text("$label · $mode", style = MaterialTheme.typography.bodyLarge)
            Text(
                note,
                style = MaterialTheme.typography.labelMedium,
                color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PriceText(price.priceMilli, price.category, style = MaterialTheme.typography.titleLarge)
    }
}

/** Apre il navigatore preferito dell'utente (Google Maps, OsmAnd, Waze...) con l'intent geo:. */
fun openNavigation(context: Context, station: Station) {
    val location = station.location ?: return
    val label = Uri.encode("${station.brand} ${station.name}".trim())
    val uri = "geo:0,0?q=${location.latitude},${location.longitude}($label)".toUri()
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        // Nessuna app di mappe installata: non c'è altro da fare.
    }
}
