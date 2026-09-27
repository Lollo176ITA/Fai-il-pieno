package it.faiilpieno.ui.refuel

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import it.faiilpieno.R
import it.faiilpieno.domain.model.CarProfile
import it.faiilpieno.domain.model.ConsumptionUnit
import it.faiilpieno.domain.model.FuelCategory
import it.faiilpieno.ui.format.Fmt
import it.faiilpieno.ui.routes.dayFull
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/** "6,5 l/100 km" o "15,4 km/l", nell'unità scelta dall'utente. */
@Composable
fun consumptionText(per100Km: Double, unit: ConsumptionUnit, fuel: FuelCategory): String {
    val kg = fuel.isSoldByKg
    val value = if (unit == ConsumptionUnit.KM_PER_UNIT) CarProfile.per100KmToKmPerUnit(per100Km) else per100Km
    val label = stringResource(
        when (unit) {
            ConsumptionUnit.KM_PER_UNIT -> if (kg) R.string.car_unit_km_per_kg else R.string.car_unit_km_per_liter
            ConsumptionUnit.PER_100_KM -> if (kg) R.string.car_unit_kg_per_100 else R.string.car_unit_liters_per_100
        },
    )
    return stringResource(R.string.format_value_unit, Fmt.oneDecimal(value), label)
}

/** Autonomia arrotondata: è una stima, i decimali darebbero una precisione che non c'è. */
@Composable
fun rangeText(km: Double): String {
    val rounded = if (km >= 100) (km / 10).roundToInt() * 10 else km.roundToInt()
    return stringResource(R.string.format_km, rounded.toString())
}

/** "In riserva domani", "In riserva giovedì", "In riserva il 14 ottobre". */
@Composable
fun reserveDateText(date: LocalDate, today: LocalDate = LocalDate.now()): String {
    val days = ChronoUnit.DAYS.between(today, date)
    return when {
        days <= 0 -> stringResource(R.string.tank_reserve_today)
        days == 1L -> stringResource(R.string.tank_reserve_tomorrow)
        days < 7 -> stringResource(R.string.tank_reserve_on, dayFull(date.dayOfWeek))
        else -> stringResource(R.string.tank_reserve_on_date, DAY_MONTH.format(date))
    }
}

private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.ITALY)

/**
 * Chiede il permesso per le notifiche dove serve (Android 13+) e riporta se sono consentite.
 * Sulle versioni precedenti le notifiche sono già permesse.
 */
@Composable
fun rememberNotificationPermission(onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onResult)
    return {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            onResult(true)
        } else {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
