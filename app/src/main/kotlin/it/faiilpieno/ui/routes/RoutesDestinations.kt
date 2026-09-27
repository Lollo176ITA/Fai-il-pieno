package it.faiilpieno.ui.routes

import kotlinx.serialization.Serializable

@Serializable data object RoutesRoute

/** [placeId] = 0 per un luogo nuovo; [kind] preimposta il tipo (per esempio "Aggiungi Casa"). */
@Serializable data class PlaceEditRoute(val placeId: Long = 0, val kind: String? = null)

/** [commuteId] = 0 per un tragitto nuovo. */
@Serializable data class CommuteEditRoute(val commuteId: Long = 0)

@Serializable data class CommuteDetailRoute(val commuteId: Long)
