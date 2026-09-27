package it.faiilpieno.ui.car

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import it.faiilpieno.R
import it.faiilpieno.domain.model.FuelCategory
import it.faiilpieno.domain.tank.TankEstimate
import it.faiilpieno.ui.format.Fmt
import it.faiilpieno.ui.format.quantityUnitLabel
import it.faiilpieno.ui.refuel.RefuelSheet
import it.faiilpieno.ui.refuel.rangeText
import it.faiilpieno.ui.refuel.rememberNotificationPermission
import it.faiilpieno.ui.refuel.reserveDateText
import kotlin.math.roundToInt

/** Card "Serbatoio": carburante stimato, quando si va in riserva, pulsante "Ho fatto il pieno". */
@Composable
fun TankCard(state: TankState, fuel: FuelCategory, onAlertsChange: (Boolean) -> Unit, onUndo: () -> Unit) {
    var showRefuel by rememberSaveable { mutableStateOf(false) }
    var confirmUndo by rememberSaveable { mutableStateOf(false) }
    val estimate = state.estimate

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.tank_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            if (estimate == null) {
                Text(stringResource(R.string.tank_no_estimate), style = MaterialTheme.typography.bodyLarge)
            } else {
                Gauge(estimate, fuel)
            }
            Button(onClick = { showRefuel = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Icon(painterResource(R.drawable.ic_gas_station), contentDescription = null, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.refuel_action), modifier = Modifier.padding(start = 8.dp))
            }
            if (state.hasRefuels) {
                TextButton(onClick = { confirmUndo = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.refuel_undo))
                }
            }
            HorizontalDivider()
            AlertsSwitch(state.alerts, onAlertsChange)
        }
    }

    if (showRefuel) RefuelSheet(stationId = null, onDismiss = { showRefuel = false })
    if (confirmUndo) {
        AlertDialog(
            onDismissRequest = { confirmUndo = false },
            title = { Text(stringResource(R.string.refuel_undo_title)) },
            text = { Text(stringResource(R.string.refuel_undo_text)) },
            confirmButton = {
                TextButton(onClick = { confirmUndo = false; onUndo() }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmUndo = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun Gauge(estimate: TankEstimate, fuel: FuelCategory) {
    val inReserve = estimate.levelLiters <= estimate.reserveLiters
    val percent = (estimate.fraction * 100).roundToInt()
    val gaugeDescription = stringResource(R.string.tank_a11y, percent)
    LinearProgressIndicator(
        progress = { estimate.fraction.toFloat() },
        color = if (inReserve) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clearAndSetSemantics { contentDescription = gaugeDescription },
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.tank_level, Fmt.editable(estimate.levelLiters.roundToInt().toDouble()), quantityUnitLabel(fuel)),
            style = MaterialTheme.typography.headlineSmall,
        )
        if (inReserve) {
            // Colore e icona insieme: l'informazione non dipende solo dal colore.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                Text(
                    stringResource(R.string.tank_in_reserve),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        } else {
            Text(stringResource(R.string.tank_range, rangeText(estimate.kmToReserve)), style = MaterialTheme.typography.bodyLarge)
            Text(
                estimate.reserveDate?.let { reserveDateText(it) } ?: stringResource(R.string.tank_reserve_unknown),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Text(
            stringResource(R.string.tank_last_full, Fmt.dayMonth(estimate.lastFullDate)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AlertsSwitch(alerts: Boolean?, onChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    // Il permesso può essere tolto dalle impostazioni di sistema: si ricontrolla al ritorno.
    var systemEnabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    LifecycleResumeEffect(Unit) {
        systemEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        onPauseOrDispose { }
    }
    val requestPermission = rememberNotificationPermission { granted ->
        systemEnabled = granted
        onChange(granted)
    }
    val checked = alerts == true && systemEnabled
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch) { on -> if (on) requestPermission() else onChange(false) },
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.alerts_switch), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.alerts_switch_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null, modifier = Modifier.padding(start = 16.dp))
    }
    if (alerts == true && !systemEnabled) {
        Text(stringResource(R.string.alerts_denied), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
}
