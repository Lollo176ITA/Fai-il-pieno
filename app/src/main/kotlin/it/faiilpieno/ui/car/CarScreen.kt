package it.faiilpieno.ui.car

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.faiilpieno.R
import it.faiilpieno.data.work.SyncStatus
import it.faiilpieno.domain.model.ConsumptionUnit
import it.faiilpieno.domain.model.FuelCategory
import it.faiilpieno.domain.model.ServiceMode
import it.faiilpieno.ui.components.ConnectedChoiceRow
import it.faiilpieno.ui.format.Fmt
import it.faiilpieno.ui.format.fuelLabel
import it.faiilpieno.ui.format.modeLabel
import it.faiilpieno.ui.format.quantityUnitLabel

@Composable
fun CarScreen(viewModel: CarViewModel = hiltViewModel()) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val dataInfo by viewModel.dataInfo.collectAsStateWithLifecycle()
    val tank by viewModel.tank.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val savedMessage = stringResource(R.string.car_saved)
    LaunchedEffect(Unit) { viewModel.saved.collect { snackbar.showSnackbar(savedMessage) } }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                stringResource(R.string.car_title),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.semantics { heading() },
            )
            if (tank.loaded) TankCard(tank, form.fuel, onAlertsChange = { viewModel.setAlerts(it) }, onUndo = { viewModel.undoLastRefuel() })
            if (form.loaded) CarForm(form, viewModel)
            DataSection(dataInfo, onRefresh = viewModel::refreshData)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun CarForm(form: CarForm, viewModel: CarViewModel) {
    val kg = form.fuel.isSoldByKg

    Section(stringResource(R.string.car_fuel)) {
        // FlowRow invece di una riga fissa: con il testo ingrandito i chip vanno a capo.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FuelCategory.selectable.forEach { fuel ->
                val selected = fuel == form.fuel
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.setFuel(fuel) },
                    label = { Text(fuelLabel(fuel), style = MaterialTheme.typography.labelLarge) },
                    leadingIcon = if (selected) {
                        { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null,
                    modifier = Modifier.heightIn(min = 48.dp).semantics { role = Role.RadioButton },
                )
            }
        }
    }

    Section(stringResource(R.string.car_tank)) {
        OutlinedTextField(
            value = form.tank,
            onValueChange = viewModel::setTank,
            suffix = { Text(quantityUnitLabel(form.fuel)) },
            isError = form.tankError,
            supportingText = {
                Text(stringResource(if (form.tankError) R.string.car_invalid_number else R.string.car_tank_hint))
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Section(stringResource(R.string.car_consumption)) {
        ConnectedChoiceRow(
            options = ConsumptionUnit.entries,
            selected = form.unit,
            label = { unitLabel(it, kg) },
            onSelect = viewModel::setUnit,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.size(8.dp))
        OutlinedTextField(
            value = form.consumption,
            onValueChange = viewModel::setConsumption,
            suffix = { Text(unitLabel(form.unit, kg)) },
            isError = form.consumptionError,
            supportingText = {
                Text(stringResource(if (form.consumptionError) R.string.car_invalid_number else R.string.car_consumption_hint))
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (form.fuel.hasServiceModes) Section(stringResource(R.string.car_service)) {
        ConnectedChoiceRow(
            options = ServiceMode.entries,
            selected = form.serviceMode,
            label = { modeLabel(it) },
            onSelect = viewModel::setServiceMode,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(stringResource(R.string.car_save))
    }
}

@Composable
private fun unitLabel(unit: ConsumptionUnit, kg: Boolean): String = stringResource(
    when (unit) {
        ConsumptionUnit.KM_PER_UNIT -> if (kg) R.string.car_unit_km_per_kg else R.string.car_unit_km_per_liter
        ConsumptionUnit.PER_100_KM -> if (kg) R.string.car_unit_kg_per_100 else R.string.car_unit_liters_per_100
    },
)

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
private fun DataSection(state: DataInfoState, onRefresh: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.car_data_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            val info = state.dataset
            if (info != null) {
                Text(stringResource(R.string.car_data_updated, Fmt.dayMonthYear(info.extractionDate)), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.car_data_stats, Fmt.count(info.stationCount), Fmt.count(info.priceCount)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(stringResource(R.string.car_data_none), style = MaterialTheme.typography.bodyLarge)
            }
            Text(stringResource(R.string.source_attribution), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.car_data_license),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val running = state.sync == SyncStatus.Running
            FilledTonalButton(onClick = onRefresh, enabled = !running, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(painterResource(R.drawable.ic_refresh), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(if (running) R.string.warning_updating else R.string.car_data_refresh),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
