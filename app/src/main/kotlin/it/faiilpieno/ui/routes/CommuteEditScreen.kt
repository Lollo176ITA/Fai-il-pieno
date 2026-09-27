package it.faiilpieno.ui.routes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.faiilpieno.R
import it.faiilpieno.domain.commute.Place
import java.time.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommuteEditScreen(
    onBack: () -> Unit,
    onSaved: (commuteId: Long, isNew: Boolean) -> Unit,
    onDeleted: () -> Unit,
    viewModel: CommuteEditViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CommuteEditEvent.Saved -> onSaved(event.commuteId, event.isNew)
                CommuteEditEvent.Deleted -> onDeleted()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (form.isNew) R.string.commute_new_title else R.string.commute_edit_title)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        if (!form.loaded) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            PlacePicker(stringResource(R.string.commute_from), form.places, form.fromId, viewModel::setFrom)
            PlacePicker(stringResource(R.string.commute_to), form.places, form.toId, viewModel::setTo)
            if (form.samePlaceError) ErrorText(stringResource(R.string.commute_same_place))

            DaysPicker(form.days, viewModel::toggleDay)
            if (form.noDaysError) ErrorText(stringResource(R.string.commute_no_days))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .toggleable(value = form.roundTrip, role = Role.Switch, onValueChange = viewModel::setRoundTrip),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.commute_round_trip_label), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.commute_round_trip_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = form.roundTrip, onCheckedChange = null, modifier = Modifier.padding(start = 16.dp))
            }

            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.commute_save))
            }
            if (!form.isNew) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.commute_delete), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    if (confirmDelete) {
        val from = form.places.firstOrNull { it.id == form.fromId }?.label.orEmpty()
        val to = form.places.firstOrNull { it.id == form.toId }?.label.orEmpty()
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.commute_delete_title, stringResource(R.string.commute_title, from, to))) },
            text = { Text(stringResource(R.string.commute_delete_text)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun PlacePicker(title: String, places: List<Place>, selectedId: Long?, onSelect: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            places.forEach { place ->
                val selected = place.id == selectedId
                FilterChip(
                    selected = selected,
                    onClick = { onSelect(place.id) },
                    label = { Text(place.label, style = MaterialTheme.typography.labelLarge) },
                    leadingIcon = {
                        Icon(
                            painterResource(if (selected) R.drawable.ic_check else placeKindIcon(place.kind)),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    modifier = Modifier.heightIn(min = 48.dp).semantics { role = Role.RadioButton },
                )
            }
        }
    }
}

@Composable
private fun DaysPicker(days: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.commute_days), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DayOfWeek.entries.forEach { day ->
                val selected = day in days
                val spoken = dayFull(day)
                FilterChip(
                    selected = selected,
                    onClick = { onToggle(day) },
                    label = { Text(dayShort(day), style = MaterialTheme.typography.labelLarge) },
                    leadingIcon = if (selected) {
                        { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics {
                            role = Role.Checkbox
                            contentDescription = spoken
                        },
                )
            }
        }
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}
