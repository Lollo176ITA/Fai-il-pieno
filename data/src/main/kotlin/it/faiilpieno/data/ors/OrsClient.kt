package it.faiilpieno.data.ors

import it.faiilpieno.data.BuildConfig
import it.faiilpieno.domain.commute.RoutePreferences
import it.faiilpieno.domain.model.GeoPoint
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Percorso restituito da OpenRouteService: geometria come encoded polyline (precisione 5). */
data class OrsRoute(val distanceMeters: Double, val durationSeconds: Double, val encodedPolyline: String)

data class AddressSuggestion(val label: String, val point: GeoPoint)

class OrsException(val reason: Reason, message: String) : IOException(message) {
    enum class Reason {
        /** Manca ORS_API_KEY in local.properties. */
        MISSING_KEY,
        /** Nessuna connessione o timeout. */
        NETWORK,
        /** Chiave rifiutata o quota esaurita. */
        UNAUTHORIZED,
        /** Nessuna strada vicino a uno dei due luoghi, o luoghi non collegati. */
        NO_ROUTE,
        /** Errore del server o risposta illeggibile. */
        SERVER,
    }
}

/**
 * Client delle API HeiGIT: OpenRouteService per i percorsi, Pelias per gli indirizzi.
 * Dal 2026 l'host è api.heigit.org (api.openrouteservice.org è dismesso).
 */
@Singleton
class OrsClient @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
) {
    private val apiKey: String get() = BuildConfig.ORS_API_KEY

    val hasKey: Boolean get() = apiKey.isNotBlank()

    /** Percorso in auto da [from] a [to]. */
    suspend fun directions(from: GeoPoint, to: GeoPoint, preferences: RoutePreferences = RoutePreferences()): OrsRoute {
        val body = json.encodeToString(
            DirectionsRequest.serializer(),
            DirectionsRequest(
                coordinates = listOf(listOf(from.longitude, from.latitude), listOf(to.longitude, to.latitude)),
                // Casa e lavoro possono essere lontani dalla strada (cortili, parcheggi): 1 km di tolleranza.
                radiuses = listOf(SNAP_RADIUS_M, SNAP_RADIUS_M),
                options = DirectionsOptions(preferences.apiFeatures),
            ),
        )
        val request = Request.Builder()
            .url("$BASE_URL/openrouteservice/v2/directions/driving-car/json")
            .post(body.toRequestBody(JSON))
            .build()
        val response = decode(DirectionsResponse.serializer(), execute(request))
        val route = response.routes.firstOrNull() ?: throw OrsException(OrsException.Reason.NO_ROUTE, "Nessun percorso")
        if (route.geometry.isBlank()) throw OrsException(OrsException.Reason.SERVER, "Percorso senza geometria")
        return OrsRoute(route.summary.distance, route.summary.duration, route.geometry)
    }

    /**
     * Indirizzi in Italia che corrispondono a [text], preferendo quelli vicini a [focus].
     * Si usa "search" e non "autocomplete": interpreta meglio gli indirizzi completi
     * ("Via Dante 10 Milano"), che sono il caso tipico per casa e lavoro.
     */
    suspend fun searchAddress(text: String, focus: GeoPoint?): List<AddressSuggestion> {
        val url = peliasUrl("search")
            .addQueryParameter("text", text)
            .addQueryParameter("boundary.country", "IT")
            .addQueryParameter("lang", "it")
            .addQueryParameter("size", SUGGESTIONS.toString())
            .apply {
                focus?.let {
                    addQueryParameter("focus.point.lat", it.latitude.toString())
                    addQueryParameter("focus.point.lon", it.longitude.toString())
                }
            }
            .build()
        return geocode(url)
    }

    /** Indirizzo più vicino a [point], o null se non ce n'è uno. */
    suspend fun reverse(point: GeoPoint): AddressSuggestion? {
        val url = peliasUrl("reverse")
            .addQueryParameter("point.lat", point.latitude.toString())
            .addQueryParameter("point.lon", point.longitude.toString())
            .addQueryParameter("lang", "it")
            .addQueryParameter("size", "1")
            .build()
        return geocode(url).firstOrNull()
    }

    private suspend fun geocode(url: HttpUrl): List<AddressSuggestion> {
        val response = decode(FeatureCollection.serializer(), execute(Request.Builder().url(url).get().build()))
        return response.features.mapNotNull { feature ->
            val coordinates = feature.geometry?.coordinates ?: return@mapNotNull null
            if (coordinates.size < 2) return@mapNotNull null
            val label = feature.properties.label ?: feature.properties.name ?: return@mapNotNull null
            AddressSuggestion(label, GeoPoint(coordinates[1], coordinates[0]))
        }
    }

    private fun peliasUrl(endpoint: String): HttpUrl.Builder = "$BASE_URL/pelias/v1/$endpoint".toHttpUrl().newBuilder()

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        if (!hasKey) throw OrsException(OrsException.Reason.MISSING_KEY, "ORS_API_KEY mancante")
        val authorized = request.newBuilder()
            .header("Authorization", apiKey)
            .header("Accept", "application/json, application/geo+json")
            .build()
        val (code, text) = try {
            client.newCall(authorized).execute().use { it.code to it.body.string() }
        } catch (e: IOException) {
            throw OrsException(OrsException.Reason.NETWORK, e.message ?: "Errore di rete")
        }
        when {
            code in 200..299 -> text
            code == 401 || code == 403 || code == 429 -> throw OrsException(OrsException.Reason.UNAUTHORIZED, "HTTP $code")
            // 2004: percorso troppo lungo; 2009: luoghi non collegati; 2010: nessuna strada vicino al punto.
            code == 400 || code == 404 -> {
                val errorCode = runCatching { json.decodeFromString(ErrorResponse.serializer(), text).error?.code }.getOrNull()
                if (errorCode in NO_ROUTE_CODES) throw OrsException(OrsException.Reason.NO_ROUTE, "Errore ORS $errorCode")
                throw OrsException(OrsException.Reason.SERVER, "HTTP $code")
            }
            else -> throw OrsException(OrsException.Reason.SERVER, "HTTP $code")
        }
    }

    /** Una risposta che non rispetta lo schema atteso vale come errore del server. */
    private fun <T> decode(deserializer: DeserializationStrategy<T>, text: String): T = try {
        json.decodeFromString(deserializer, text)
    } catch (e: SerializationException) {
        throw OrsException(OrsException.Reason.SERVER, "Risposta illeggibile")
    } catch (e: IllegalArgumentException) {
        throw OrsException(OrsException.Reason.SERVER, "Risposta illeggibile")
    }

    @Serializable
    private data class DirectionsRequest(
        val coordinates: List<List<Double>>,
        val radiuses: List<Int>,
        val options: DirectionsOptions,
        val instructions: Boolean = false,
        val preference: String = "recommended",
    )

    @Serializable
    internal data class DirectionsOptions(@SerialName("avoid_features") val avoidFeatures: List<String>)

    @Serializable
    private data class DirectionsResponse(val routes: List<RouteJson> = emptyList())

    @Serializable
    private data class RouteJson(val summary: SummaryJson = SummaryJson(), val geometry: String = "")

    /** Per un percorso di lunghezza nulla ORS omette distanza e durata. */
    @Serializable
    private data class SummaryJson(val distance: Double = 0.0, val duration: Double = 0.0)

    @Serializable
    private data class ErrorResponse(val error: ErrorJson? = null)

    @Serializable
    private data class ErrorJson(val code: Int? = null)

    @Serializable
    private data class FeatureCollection(val features: List<FeatureJson> = emptyList())

    @Serializable
    private data class FeatureJson(val geometry: GeometryJson? = null, val properties: PropertiesJson = PropertiesJson())

    @Serializable
    private data class GeometryJson(val coordinates: List<Double> = emptyList())

    @Serializable
    private data class PropertiesJson(val label: String? = null, val name: String? = null)

    private companion object {
        const val BASE_URL = "https://api.heigit.org"
        const val SNAP_RADIUS_M = 1_000
        const val SUGGESTIONS = 6
        val NO_ROUTE_CODES = setOf(2004, 2009, 2010)
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
