package it.faiilpieno.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

val AppTypography = Typography()

/** Cifre a larghezza fissa: i prezzi in colonna restano allineati. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

fun TextStyle.bold(): TextStyle = copy(fontWeight = FontWeight.Bold)
