package it.faiilpieno.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import it.faiilpieno.R
import it.faiilpieno.domain.model.FuelCategory
import it.faiilpieno.ui.format.Fmt
import it.faiilpieno.ui.format.unitPriceLabel
import it.faiilpieno.ui.theme.LocalPriceColors
import it.faiilpieno.ui.theme.tabular
import kotlin.math.abs

/** Soglia sotto la quale la differenza dalla media è considerata "in media". */
private const val NEUTRAL_CENTS = 1

/**
 * "−8 cent" / "+5 cent" rispetto alla media nazionale: colore, icona a freccia e testo insieme,
 * così l'informazione non dipende mai solo dal colore.
 */
@Composable
fun PriceDeltaBadge(deltaCents: Int, modifier: Modifier = Modifier, large: Boolean = false) {
    val colors = LocalPriceColors.current
    val magnitude = abs(deltaCents)
    val (container, content, icon) = when {
        deltaCents <= -NEUTRAL_CENTS -> Triple(colors.cheaperContainer, colors.onCheaperContainer, R.drawable.ic_arrow_down)
        deltaCents >= NEUTRAL_CENTS -> Triple(colors.pricierContainer, colors.onPricierContainer, R.drawable.ic_arrow_up)
        else -> Triple(colors.neutralContainer, colors.onNeutralContainer, R.drawable.ic_equal)
    }
    val text = when {
        deltaCents <= -NEUTRAL_CENTS -> stringResource(R.string.delta_below, magnitude)
        deltaCents >= NEUTRAL_CENTS -> stringResource(R.string.delta_above, magnitude)
        else -> stringResource(R.string.delta_equal)
    }
    val description = when {
        deltaCents <= -NEUTRAL_CENTS -> pluralStringResource(R.plurals.delta_below_a11y, magnitude, magnitude)
        deltaCents >= NEUTRAL_CENTS -> pluralStringResource(R.plurals.delta_above_a11y, magnitude, magnitude)
        else -> stringResource(R.string.delta_equal_a11y)
    }
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(50),
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = if (large) 12.dp else 8.dp, vertical = if (large) 6.dp else 3.dp),
        ) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(if (large) 18.dp else 14.dp))
            Text(text, style = if (large) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium)
        }
    }
}

/** Prezzo con le cifre grandi e l'unità piccola accanto: "1,859 €/l". */
@Composable
fun PriceText(
    milli: Int,
    category: FuelCategory,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        Text(Fmt.priceNumber(milli), style = style.tabular())
        Text(
            " " + unitPriceLabel(category),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 3.dp),
        )
    }
}
