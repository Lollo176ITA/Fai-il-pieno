package it.faiilpieno.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import it.faiilpieno.R
import it.faiilpieno.domain.brand.BrandGroup
import it.faiilpieno.ui.components.rememberFullSheetState
import it.faiilpieno.ui.format.brandGroupLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrandFilterSheet(selected: Set<BrandGroup>, onApply: (Set<BrandGroup>) -> Unit, onDismiss: () -> Unit) {
    var choice by remember { mutableStateOf(selected) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberFullSheetState()) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.filter_brands_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.size(8.dp))
            BrandGroup.entries.forEach { group ->
                val checked = group in choice
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(value = checked, role = Role.Checkbox) { choice = if (it) choice + group else choice - group },
                ) {
                    Checkbox(checked = checked, onCheckedChange = null)
                    Text(brandGroupLabel(group), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
                }
            }
            Spacer(Modifier.size(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { onApply(emptySet()) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.filter_brands_all))
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onApply(choice) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.filter_apply))
                }
            }
        }
    }
}
