package it.faiilpieno.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import it.faiilpieno.R
import it.faiilpieno.ui.map.OSM_COPYRIGHT_URL

private data class Credit(@StringRes val title: Int, @StringRes val text: Int, val url: String)

private val credits = listOf(
    Credit(
        R.string.credits_prices_title, R.string.credits_prices_text,
        "https://www.mimit.gov.it/it/open-data/elenco-dataset/carburanti-prezzi-praticati-e-anagrafica-degli-impianti",
    ),
    Credit(R.string.credits_map_title, R.string.credits_map_text, OSM_COPYRIGHT_URL),
    Credit(R.string.credits_tiles_title, R.string.credits_tiles_text, "https://openfreemap.org/"),
    Credit(R.string.credits_routes_title, R.string.credits_routes_text, "https://openrouteservice.org/"),
    Credit(R.string.credits_engine_title, R.string.credits_engine_text, "https://maplibre.org/"),
)

/** Tutte le attribuzioni in un posto solo: fonti dei dati, mappa e servizi usati. */
@Composable
internal fun CreditsSection() {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        credits.forEach { credit ->
            Surface(
                onClick = { uriHandler.openUri(credit.url) },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(credit.title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                    )
                    Text(stringResource(credit.text), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
