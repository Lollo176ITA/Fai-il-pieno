package it.faiilpieno.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import it.faiilpieno.R
import it.faiilpieno.data.prefs.PreferencesRepository
import it.faiilpieno.domain.commute.RouteAvoidance
import it.faiilpieno.domain.commute.RoutePreferences
import it.faiilpieno.ui.format.distanceText

@Composable
fun RoutePreferencesCard(
    preferences: RoutePreferences,
    onAvoidanceChange: (RouteAvoidance, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showTitle: Boolean = true,
    buffer: Int? = null,
    onBufferChange: (Int) -> Unit = {},
) {
    Card(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (showTitle) Text(stringResource(R.string.route_preferences), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            RouteAvoidance.entries.forEach { feature ->
                val checked = feature in preferences.avoid
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = { onAvoidanceChange(feature, it) }),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(stringResource(when (feature) {
                        RouteAvoidance.HIGHWAYS -> R.string.route_avoid_highways
                        RouteAvoidance.TOLLWAYS -> R.string.route_avoid_tolls
                        RouteAvoidance.FERRIES -> R.string.route_avoid_ferries
                    }), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = checked, onCheckedChange = null, enabled = enabled)
                }
            }
            if (buffer != null) {
                Text(stringResource(R.string.commute_buffer), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreferencesRepository.ROUTE_BUFFER_OPTIONS.forEach { meters ->
                        FilterChip(selected = meters == buffer, enabled = enabled, onClick = { onBufferChange(meters) },
                            label = { Text(distanceText(meters.toDouble())) })
                    }
                }
            }
        }
    }
}
