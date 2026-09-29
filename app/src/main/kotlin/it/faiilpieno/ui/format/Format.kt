package it.faiilpieno.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import it.faiilpieno.R
import it.faiilpieno.domain.brand.BrandGroup
import it.faiilpieno.domain.model.FuelCategory
import it.faiilpieno.domain.model.ServiceMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

object Fmt {
    private val locale: Locale = Locale.ITALY
    private val dayMonth = DateTimeFormatter.ofPattern("d MMMM", locale)
    private val dayMonthYear = DateTimeFormatter.ofPattern("d MMMM yyyy", locale)
    private val shortDate = DateTimeFormatter.ofPattern("d/M", locale)

    /** 1859 → "1,859" */
    fun priceNumber(milli: Int): String = String.format(locale, "%.3f", milli / 1000.0)

    /** 3.2 → "3,20" */
    fun euros(value: Double): String = String.format(locale, "%.2f", value)

    fun oneDecimal(value: Double): String = String.format(locale, "%.1f", value)

    /** Numero "umano" per i campi di testo: 45.0 → "45", 6.5 → "6,5". */
    fun editable(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else String.format(locale, "%.1f", value)

    fun count(value: Int): String = String.format(locale, "%,d", value)

    fun dayMonth(date: LocalDate): String = dayMonth.format(date)

    fun dayMonthYear(date: LocalDate): String = dayMonthYear.format(date)

    fun shortDate(instant: Instant): String = shortDate.format(instant.atZone(ZoneId.systemDefault()))

    /** Accetta sia la virgola sia il punto come separatore decimale. */
    fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
}

@Composable
fun distanceText(meters: Double): String = when {
    meters < 1_000 -> stringResource(R.string.format_meters, ((meters / 10).roundToInt() * 10).coerceAtLeast(10))
    else -> stringResource(R.string.format_km, Fmt.oneDecimal(meters / 1000))
}

@Composable
fun kmText(km: Double): String = stringResource(R.string.format_km, Fmt.oneDecimal(km))

@Composable
fun eurosText(value: Double): String = stringResource(R.string.format_euro, Fmt.euros(value))

@Composable
fun unitPriceLabel(category: FuelCategory): String =
    stringResource(if (category.isSoldByKg) R.string.unit_per_kg else R.string.unit_per_liter)

@Composable
fun quantityUnitLabel(category: FuelCategory): String =
    stringResource(if (category.isSoldByKg) R.string.unit_kg else R.string.unit_liters)

@Composable
fun priceText(milli: Int, category: FuelCategory): String =
    stringResource(R.string.format_price, Fmt.priceNumber(milli), unitPriceLabel(category))

/** Versione per TalkBack: "1,739 euro al litro" invece di "1,739 €/l". */
@Composable
fun priceSpoken(milli: Int, category: FuelCategory): String = stringResource(
    if (category.isSoldByKg) R.string.price_spoken_kg else R.string.price_spoken_liter,
    Fmt.priceNumber(milli),
)

@Composable
fun fuelLabel(category: FuelCategory): String = stringResource(
    when (category) {
        FuelCategory.BENZINA -> R.string.fuel_benzina
        FuelCategory.GASOLIO -> R.string.fuel_gasolio
        FuelCategory.GPL -> R.string.fuel_gpl
        FuelCategory.METANO -> R.string.fuel_metano
        FuelCategory.ALTRO -> R.string.fuel_altro
    },
)

@Composable
fun modeLabel(mode: ServiceMode): String =
    stringResource(if (mode == ServiceMode.SELF) R.string.mode_self else R.string.mode_servito)

@Composable
fun brandGroupLabel(group: BrandGroup): String = group.canonical ?: stringResource(R.string.brand_altri)

/** Differenza dalla media nazionale letta da TalkBack: "8 centesimi sotto la media nazionale". */
@Composable
fun deltaSpoken(delta: Int): String = when {
    delta <= -1 -> pluralStringResource(R.plurals.delta_below_a11y, -delta, -delta)
    delta >= 1 -> pluralStringResource(R.plurals.delta_above_a11y, delta, delta)
    else -> stringResource(R.string.delta_equal_a11y)
}
