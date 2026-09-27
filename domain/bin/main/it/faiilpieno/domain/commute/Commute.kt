package it.faiilpieno.domain.commute

import it.faiilpieno.domain.model.GeoPoint
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

enum class PlaceKind { HOME, WORK, UNIVERSITY, OTHER }

data class Place(
    val id: Long,
    val label: String,
    val kind: PlaceKind,
    val location: GeoPoint,
    val address: String?,
)

/** Percorso calcolato una sola volta con OpenRouteService e salvato in locale. */
data class Route(
    val distanceMeters: Double,
    val durationSeconds: Double,
    val points: List<GeoPoint>,
    val computedAt: Instant,
)

data class Commute(
    val id: Long,
    val from: Place,
    val to: Place,
    val days: Set<DayOfWeek>,
    /** Anche il ritorno: si assume che segua la stessa strada. */
    val roundTrip: Boolean,
    /** null finché il percorso non è stato calcolato (per esempio se si era offline). */
    val route: Route?,
)

/** Giorni della settimana in un intero: bit 0 = lunedì … bit 6 = domenica. */
object DaysMask {
    fun encode(days: Set<DayOfWeek>): Int = days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

    fun decode(mask: Int): Set<DayOfWeek> = DayOfWeek.entries.filterTo(sortedSetOf()) { mask and (1 shl (it.value - 1)) != 0 }

    val WEEKDAYS: Set<DayOfWeek> = DayOfWeek.entries.filterTo(sortedSetOf()) { it != DayOfWeek.SATURDAY && it != DayOfWeek.SUNDAY }
}

object CommuteSchedule {
    /** Primo giorno, a partire da [from] incluso, in cui si fa il tragitto; null se non ha giorni. */
    fun nextOccurrence(days: Set<DayOfWeek>, from: LocalDate): LocalDate? {
        if (days.isEmpty()) return null
        return generateSequence(from) { it.plusDays(1) }.take(7).first { it.dayOfWeek in days }
    }
}
