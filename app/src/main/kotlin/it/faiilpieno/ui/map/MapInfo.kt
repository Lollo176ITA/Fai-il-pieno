package it.faiilpieno.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import it.faiilpieno.R
import it.faiilpieno.domain.model.DatasetInfo
import it.faiilpieno.ui.components.rememberFullSheetState
import it.faiilpieno.ui.format.Fmt
import it.faiilpieno.ui.theme.LocalPriceColors
import it.faiilpieno.ui.theme.tabular

internal const val OSM_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"

/**
 * Pulsante ⓘ sulla mappa: apre legenda e attribuzioni. Tiene la mappa pulita pur lasciando
 * i crediti di OpenStreetMap raggiungibili dalla mappa stessa, come chiede la licenza ODbL.
 */
@Composable
internal fun MapInfoButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconButton(onClick = onClick, modifier = modifier) {
        Icon(painterResource(R.drawable.ic_info), contentDescription = stringResource(R.string.map_info))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapInfoSheet(dataset: DatasetInfo?, showLegend: Boolean, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberFullSheetState()) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.map_info), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            if (showLegend) {
                val colors = LocalPriceColors.current
                LegendRow("▼", colors.cheaperContainer, colors.onCheaperContainer, stringResource(R.string.map_legend_cheaper))
                LegendRow("▲", colors.pricierContainer, colors.onPricierContainer, stringResource(R.string.map_legend_pricier))
                LegendRow("", colors.neutralContainer, colors.onNeutralContainer, stringResource(R.string.map_legend_neutral))
                HorizontalDivider()
            }
            if (dataset != null) {
                Text(stringResource(R.string.car_data_updated, Fmt.dayMonthYear(dataset.extractionDate)), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.source_attribution), style = MaterialTheme.typography.bodyMedium)
            }
            Text(stringResource(R.string.map_info_credits), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { uriHandler.openUri(OSM_COPYRIGHT_URL) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.map_info_osm_link))
            }
            Text(
                stringResource(R.string.map_info_more),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LegendRow(arrow: String, container: Color, content: Color, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = text },
    ) {
        Surface(color = container, contentColor = content, shape = RoundedCornerShape(50)) {
            Text(
                listOf(arrow, stringResource(R.string.map_legend_sample)).filter { it.isNotEmpty() }.joinToString(" "),
                style = MaterialTheme.typography.labelLarge.tabular(),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
