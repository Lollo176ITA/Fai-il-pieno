package it.faiilpieno.ui.routes

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.faiilpieno.R
import it.faiilpieno.data.ors.AddressSuggestion
import it.faiilpieno.data.ors.OrsException
import it.faiilpieno.domain.commute.PlaceKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceEditScreen(onBack: () -> Unit, viewModel: PlaceEditViewModel = hiltViewModel()) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.onPermissionResult()
    }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                PlaceEvent.Saved, PlaceEvent.Deleted -> onBack()
                PlaceEvent.RequestPermission -> permissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
                )
            }
        }
    }

    // Un luogo nuovo parte con il nome del tipo scelto ("Casa", "Lavoro"…), modificabile.
    val defaultNames = PlaceKind.entries.associateWith { if (it == PlaceKind.OTHER) "" else placeKindLabel(it) }
    LaunchedEffect(form.loaded) {
        if (form.loaded && form.isNew && form.name.isBlank()) viewModel.setKind(form.kind, defaultNames)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (form.isNew) R.string.place_new_title else R.string.place_edit_title)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        if (!form.loaded) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            KindSection(form.kind, defaultNames, viewModel)
            OutlinedTextField(
                value = form.name,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(R.string.place_name)) },
                placeholder = { Text(stringResource(R.string.place_name_hint)) },
                isError = form.nameError,
                supportingText = if (form.nameError) {
                    { Text(stringResource(R.string.place_missing_name)) }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            LocationSection(form, viewModel)
            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.place_save))
            }
            if (!form.isNew) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.place_delete), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.place_delete_title, form.name)) },
            text = {
                Text(
                    if (form.usedByCommutes > 0) {
                        pluralStringResource(R.plurals.place_delete_text, form.usedByCommutes, form.usedByCommutes)
                    } else {
                        stringResource(R.string.place_delete_unused)
                    },
                )
            },
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
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
    }
}

@Composable
private fun KindSection(selected: PlaceKind, defaults: Map<PlaceKind, String>, viewModel: PlaceEditViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.place_kind), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PlaceKind.entries.forEach { kind ->
                FilterChip(
                    selected = kind == selected,
                    onClick = { viewModel.setKind(kind, defaults) },
                    label = { Text(placeKindLabel(kind), style = MaterialTheme.typography.labelLarge) },
                    leadingIcon = {
                        Icon(
                            painterResource(if (kind == selected) R.drawable.ic_check else placeKindIcon(kind)),
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
private fun LocationSection(form: PlaceForm, viewModel: PlaceEditViewModel) {
    // Scelto il luogo, la tastiera si chiude e lascia vedere il pulsante Salva.
    val focusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.place_where), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })

        form.location?.let {
            val label = form.address ?: stringResource(R.string.place_current_position)
            val description = stringResource(R.string.place_selected_a11y, label)
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_place), contentDescription = null)
                    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }

        OutlinedTextField(
            value = form.query,
            onValueChange = viewModel::setQuery,
            label = { Text(stringResource(R.string.place_search)) },
            placeholder = { Text(stringResource(R.string.place_search_hint)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
            isError = form.locationError,
            supportingText = if (form.locationError) {
                { Text(stringResource(R.string.place_missing_location)) }
            } else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            modifier = Modifier.fillMaxWidth(),
        )
        SearchResults(form.search, onChoose = {
            focusManager.clearFocus()
            viewModel.choose(it)
        })

        FilledTonalButton(
            onClick = {
                focusManager.clearFocus()
                viewModel.useCurrentLocation()
            },
            enabled = !form.locating,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Icon(painterResource(R.drawable.ic_my_location), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                stringResource(if (form.locating) R.string.place_locating else R.string.place_use_location),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        form.locateError?.let { error ->
            Text(
                stringResource(if (error == LocateError.PERMISSION) R.string.place_permission_denied else R.string.place_location_error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun SearchResults(state: SearchState, onChoose: (AddressSuggestion) -> Unit) {
    val message: String? = when (state) {
        SearchState.Idle -> null
        SearchState.Loading -> stringResource(R.string.place_searching)
        is SearchState.Results -> if (state.suggestions.isEmpty()) stringResource(R.string.place_search_empty) else null
        is SearchState.Failed -> stringResource(
            when (state.reason) {
                OrsException.Reason.MISSING_KEY -> R.string.place_search_no_key
                OrsException.Reason.NETWORK -> R.string.place_search_offline
                else -> R.string.place_search_error
            },
        )
    }
    if (message != null) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (state is SearchState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    if (state is SearchState.Results && state.suggestions.isNotEmpty()) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column {
                state.suggestions.forEachIndexed { index, suggestion ->
                    if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { onChoose(suggestion) }
                            .heightIn(min = 56.dp)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_place), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(suggestion.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        }
    }
}
