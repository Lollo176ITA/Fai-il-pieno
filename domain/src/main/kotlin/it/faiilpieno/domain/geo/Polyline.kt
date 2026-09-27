package it.faiilpieno.domain.geo

import it.faiilpieno.domain.model.GeoPoint
import kotlin.math.roundToLong

/**
 * Encoded polyline (algoritmo Google), il formato con cui OpenRouteService restituisce la
 * geometria del percorso. Precisione 5 decimali, quella usata da ORS senza quota altimetrica.
 */
object Polyline {
    private const val FACTOR = 1e5

    /** Lancia IllegalArgumentException se la stringa è troncata o contiene caratteri non validi. */
    fun decode(encoded: String): List<GeoPoint> {
        val points = ArrayList<GeoPoint>()
        var index = 0
        var lat = 0L
        var lon = 0L
        while (index < encoded.length) {
            val (dLat, afterLat) = readValue(encoded, index)
            val (dLon, afterLon) = readValue(encoded, afterLat)
            index = afterLon
            lat += dLat
            lon += dLon
            points += GeoPoint(lat / FACTOR, lon / FACTOR)
        }
        return points
    }

    fun encode(points: List<GeoPoint>): String = buildString {
        var prevLat = 0L
        var prevLon = 0L
        points.forEach { p ->
            val lat = (p.latitude * FACTOR).roundToLong()
            val lon = (p.longitude * FACTOR).roundToLong()
            writeValue(lat - prevLat)
            writeValue(lon - prevLon)
            prevLat = lat
            prevLon = lon
        }
    }

    private fun readValue(encoded: String, start: Int): Pair<Long, Int> {
        var index = start
        var result = 0L
        var shift = 0
        while (true) {
            require(index < encoded.length) { "Polyline troncata" }
            val chunk = encoded[index++].code - 63
            require(chunk in 0..63) { "Carattere non valido nella polyline" }
            result = result or ((chunk and 0x1f).toLong() shl shift)
            shift += 5
            if (chunk < 0x20) break
        }
        val value = if (result and 1L != 0L) (result shr 1).inv() else result shr 1
        return value to index
    }

    private fun StringBuilder.writeValue(value: Long) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= 0x20) {
            append(((0x20 or (v and 0x1f).toInt()) + 63).toChar())
            v = v shr 5
        }
        append((v.toInt() + 63).toChar())
    }
}
