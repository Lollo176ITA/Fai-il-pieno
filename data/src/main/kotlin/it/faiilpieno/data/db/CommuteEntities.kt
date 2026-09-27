package it.faiilpieno.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import it.faiilpieno.domain.commute.Commute
import it.faiilpieno.domain.commute.DaysMask
import it.faiilpieno.domain.commute.Place
import it.faiilpieno.domain.commute.PlaceKind
import it.faiilpieno.domain.commute.Route
import it.faiilpieno.domain.geo.Polyline
import it.faiilpieno.domain.model.GeoPoint
import java.time.Instant

@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val kind: PlaceKind,
    val lat: Double,
    val lon: Double,
    val address: String?,
)

/** Eliminando un luogo si eliminano anche i tragitti che lo usano. */
@Entity(
    tableName = "commutes",
    foreignKeys = [
        ForeignKey(entity = PlaceEntity::class, parentColumns = ["id"], childColumns = ["fromPlaceId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PlaceEntity::class, parentColumns = ["id"], childColumns = ["toPlaceId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("fromPlaceId"), Index("toPlaceId")],
)
data class CommuteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromPlaceId: Long,
    val toPlaceId: Long,
    val daysMask: Int,
    val roundTrip: Boolean,
    /** I campi del percorso restano null finché OpenRouteService non l'ha calcolato. */
    val distanceM: Double? = null,
    val durationS: Double? = null,
    /** Encoded polyline, precisione 5. */
    val polyline: String? = null,
    val computedAt: Instant? = null,
)

fun PlaceEntity.toDomain() = Place(id, label, kind, GeoPoint(lat, lon), address)

fun Place.toEntity() = PlaceEntity(id, label, kind, location.latitude, location.longitude, address)

/** null se uno dei due luoghi non esiste più. Una polyline illeggibile vale come percorso da ricalcolare. */
fun CommuteEntity.toDomain(places: Map<Long, Place>): Commute? {
    val from = places[fromPlaceId] ?: return null
    val to = places[toPlaceId] ?: return null
    val points = polyline?.let { runCatching { Polyline.decode(it) }.getOrNull() }?.takeIf { it.isNotEmpty() }
    val route = if (points != null && distanceM != null && durationS != null && computedAt != null) {
        Route(distanceM, durationS, points, computedAt)
    } else {
        null
    }
    return Commute(id, from, to, DaysMask.decode(daysMask), roundTrip, route)
}
