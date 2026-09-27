package it.faiilpieno.domain.geo

import it.faiilpieno.domain.model.GeoPoint

/** Punto a [meters] da [start] nella direzione [bearingDeg] (formula sferica). */
internal fun destination(start: GeoPoint, meters: Double, bearingDeg: Double): GeoPoint {
    val r = 6_371_008.8
    val d = meters / r
    val b = Math.toRadians(bearingDeg)
    val lat1 = Math.toRadians(start.latitude)
    val lon1 = Math.toRadians(start.longitude)
    val lat2 = Math.asin(Math.sin(lat1) * Math.cos(d) + Math.cos(lat1) * Math.sin(d) * Math.cos(b))
    val lon2 = lon1 + Math.atan2(Math.sin(b) * Math.sin(d) * Math.cos(lat1), Math.cos(d) - Math.sin(lat1) * Math.sin(lat2))
    return GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lon2))
}
