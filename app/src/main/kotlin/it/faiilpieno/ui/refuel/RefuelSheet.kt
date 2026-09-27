package it.faiilpieno.ui.refuel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.faiilpieno.R
import it.faiilpieno.ui.components.ConnectedChoiceRow
import it.faiilpieno.ui.components.rememberFullSheetState
import it.faiilpieno.ui.format.Fmt
import it.faiilpieno.ui.format.quantityUnitLabel

/** Foglio "Ho fatto il pieno". [stationId] se si apre dal dettaglio di un distributore. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefuelSheet(stationId: Long?, onDismiss: () -> Unit, viewModel: RefuelViewModel = hiltViewModel()) {
    LaunchedEffect(stationId) { viewModel.start(stationId) }
    val form by viewModel.form.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberFullSheetState()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val result = saved
            when {
                !form.loaded -> Unit
                result != null -> SavedContent(result, form, viewModel, onDismiss)
                else -> FormContent(form, viewModel)
            }
        }
    }
}

@Composable
private fun FormContent(form: RefuelForm, viewModel: RefuelViewModel) {
    val unit = quantityUnitLabel(form.car.fuel)
    Text(
        form.stationBrand?.let { stringResource(R.string.refuel_title_at, it) } ?: stringResource(R.string.refuel_title),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.semantics { heading() },
    )
    ConnectedChoiceRow(
        options = listOf(true, false),
        selected = form.isFull,
        label = { stringResource(if (it) R.string.refuel_kind_full else R.string.refuel_kind_partial) },
        onSelect = viewModel::setFull,
        modifier = Modifier.fillMaxWidth(),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.refuel_when), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        ConnectedChoiceRow(
            options = listOf(false, true),
            selected = form.yesterday,
            label = { stringResource(if (it) R.string.refuel_when_yesterday else R.string.refuel_when_today) },
            onSelect = viewModel::setYesterday,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    OutlinedTextField(
        value = form.quantity,
        onValueChange = viewModel::setQuantity,
        label = { Text(stringResource(R.string.refuel_quantity)) },
        suffix = { Text(unit) },
        isError = form.quantityError,
        supportingText = { Text(stringResource(if (form.quantityError) R.string.refuel_invalid else R.string.refuel_quantity_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = form.amount,
        onValueChange = viewModel::setAmount,
        label = { Text(stringResource(R.string.refuel_amount)) },
        suffix = { Text("€") },
        isError = form.amountError,
        supportingText = if (form.amountError) {
            { Text(stringResource(R.string.refuel_invalid)) }
        } else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
    if (form.partialError) {
        Text(
            stringResource(R.string.refuel_partial_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    Button(onClick = viewModel::save, enabled = !form.saving, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(stringResource(R.string.refuel_save))
    }
}

@Composable
private fun SavedContent(saved: RefuelSaved, form: RefuelForm, viewModel: RefuelViewModel, onDismiss: () -> Unit) {
    val requestPermission = rememberNotificationPermission(viewModel::setAlerts)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(R.string.refuel_saved),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 12.dp).semantics { heading() },
            )
        }
        saved.outcome.litersFromAmount?.let {
            Text(
                stringResource(R.string.refuel_quantity_estimated, Fmt.oneDecimal(it), quantityUnitLabel(form.car.fuel)),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        saved.outcome.calibration?.let { calibration ->
            Text(
                stringResource(
                    R.string.refuel_calibrated,
                    consumptionText(calibration.consumptionPer100Km, form.car.consumptionUnit, form.car.fuel),
                    consumptionText(saved.outcome.previousConsumption, form.car.consumptionUnit, form.car.fuel),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        saved.estimate?.let {
            Text(stringResource(R.string.refuel_range_now, rangeText(it.kmToReserve)), style = MaterialTheme.typography.bodyLarge)
        }
    }

    if (saved.askAlerts) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_notifications), contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        stringResource(R.string.alerts_ask_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 8.dp).semantics { heading() },
                    )
                }
                Text(stringResource(R.string.alerts_ask_text), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = requestPermission, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.alerts_ask_yes))
                    }
                    TextButton(onClick = { viewModel.setAlerts(false) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.alerts_ask_no))
                    }
                }
            }
        }
    }
    OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.action_close))
    }
}
