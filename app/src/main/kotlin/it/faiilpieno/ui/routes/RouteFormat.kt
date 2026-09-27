package it.faiilpieno.ui.routes

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import it.faiilpieno.R
import it.faiilpieno.data.ors.OrsException
import it.faiilpieno.domain.commute.Commute
import it.faiilpieno.domain.commute.DaysMask
import it.faiilpieno.domain.commute.PlaceKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val ITALIAN = Locale.ITALY

@DrawableRes
fun placeKindIcon(kind: PlaceKind): Int = when (kind) {
    PlaceKind.HOME -> R.drawable.ic_home
    PlaceKind.WORK -> R.drawable.ic_work
    PlaceKind.UNIVERSITY -> R.drawable.ic_school
    PlaceKind.OTHER -> R.drawable.ic_place
}

@Composable
fun placeKindLabel(kind: PlaceKind): String = stringResource(
    when (kind) {
        PlaceKind.HOME -> R.string.kind_home
        PlaceKind.WORK -> R.string.kind_work
        PlaceKind.UNIVERSITY -> R.string.kind_university
        PlaceKind.OTHER -> R.string.kind_other
    },
)

/** "Lun", "Mar"… */
fun dayShort(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, ITALIAN).replaceFirstChar { it.titlecase(ITALIAN) }

/** "lunedì" per TalkBack. */
fun dayFull(day: DayOfWeek): String = day.getDisplayName(TextStyle.FULL, ITALIAN)

/** "Lun–Ven", "Tutti i giorni" oppure "Lun · Mer · Ven". */
@Composable
fun daysSummary(days: Set<DayOfWeek>): String = when (days) {
    DayOfWeek.entries.toSet() -> stringResource(R.string.days_everyday)
    DaysMask.WEEKDAYS -> stringResource(R.string.days_weekdays)
    setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> stringResource(R.string.days_weekend)
    else -> days.sorted().joinToString(" · ", transform = ::dayShort)
}

/** "Oggi", "Domani" o il giorno della settimana ("Lunedì"). */
@Composable
fun whenLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> stringResource(R.string.when_today)
    today.plusDays(1) -> stringResource(R.string.when_tomorrow)
    else -> dayFull(date.dayOfWeek).replaceFirstChar { it.titlecase(ITALIAN) }
}

@Composable
fun durationText(seconds: Double): String {
    val minutes = (seconds / 60).roundToInt().coerceAtLeast(1)
    return if (minutes < 60) {
        stringResource(R.string.format_duration_min, minutes)
    } else {
        stringResource(R.string.format_duration_h, minutes / 60, minutes % 60)
    }
}

@Composable
fun commuteTitle(commute: Commute): String = stringResource(R.string.commute_title, commute.from.label, commute.to.label)

@Composable
fun commuteTitleSpoken(commute: Commute): String = stringResource(R.string.commute_title_a11y, commute.from.label, commute.to.label)

@Composable
fun routeErrorText(reason: OrsException.Reason): String = stringResource(
    when (reason) {
        OrsException.Reason.MISSING_KEY -> R.string.commute_error_key
        OrsException.Reason.NETWORK -> R.string.commute_error_network
        OrsException.Reason.UNAUTHORIZED -> R.string.commute_error_unauthorized
        OrsException.Reason.NO_ROUTE -> R.string.commute_error_no_route
        OrsException.Reason.SERVER -> R.string.commute_error_server
    },
)
