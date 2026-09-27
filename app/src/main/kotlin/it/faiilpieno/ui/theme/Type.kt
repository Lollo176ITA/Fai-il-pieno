package it.faiilpieno.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

private val BaseTypography = Typography()
val AppTypography = Typography(
    headlineMedium = BaseTypography.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = BaseTypography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    displayMedium = BaseTypography.displayMedium.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
)

/** Cifre a larghezza fissa: i prezzi in colonna restano allineati. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

fun TextStyle.bold(): TextStyle = copy(fontWeight = FontWeight.Bold)
