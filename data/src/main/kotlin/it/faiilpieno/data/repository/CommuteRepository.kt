package it.faiilpieno.data.repository

import it.faiilpieno.data.db.CommuteDao
import it.faiilpieno.data.db.CommuteEntity
import it.faiilpieno.data.db.PlaceEntity
import it.faiilpieno.data.db.toDomain
import it.faiilpieno.data.db.toEntity
import it.faiilpieno.data.ors.AddressSuggestion
import it.faiilpieno.data.ors.OrsClient
import it.faiilpieno.data.ors.OrsException
import it.faiilpieno.data.prefs.PreferencesRepository
import it.faiilpieno.domain.brand.BrandGroup
import it.faiilpieno.domain.commute.Commute
import it.faiilpieno.domain.commute.CommuteRanker
import it.faiilpieno.domain.commute.CommuteResult
import it.faiilpieno.domain.commute.CommuteSort
import it.faiilpieno.domain.commute.DaysMask
import it.faiilpieno.domain.commute.Place
import it.faiilpieno.domain.commute.Route
import it.faiilpieno.domain.commute.RouteOffer
import it.faiilpieno.domain.geo.RouteCorridor
import it.faiilpieno.domain.model.CarProfile
import it.faiilpieno.domain.model.GeoPoint
import java.time.DayOfWeek
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Stato del calcolo di un percorso in corso o fallito. Assente = nessun calcolo in sospeso. */
sealed interface RouteJob {
    data object Running : RouteJob
    data class Failed(val reason: OrsException.Reason) : RouteJob
}

@Singleton
class CommuteRepository @Inject constructor(
    private val dao: CommuteDao,
    private val ors: OrsClient,
    private val prices: PriceRepository,
    private val prefs: PreferencesRepository,
) {
    /** I calcoli dei percorsi continuano anche se l'utente lascia la schermata. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _routeJobs = MutableStateFlow<Map<Long, RouteJob>>(emptyMap())
    val routeJobs: StateFlow<Map<Long, RouteJob>> = _routeJobs.asStateFlow()

    val hasApiKey: Boolean get() = ors.hasKey

    val places: Flow<List<Place>> = dao.observePlaces().map { rows ->
        rows.map(PlaceEntity::toDomain).sortedWith(compareBy({ it.kind.ordinal }, { it.label.lowercase() }))
    }

    private val generations = ConcurrentHashMap<Long, Long>()
    private val sequence = AtomicLong()
    private val routeWrites = Mutex()

    init {
        // Si ricalcolano solo i percorsi salvati con altre preferenze, o in corso con quelle vecchie:
        // i tragitti mai calcolati o falliti aspettano l'utente, anche all'avvio o dal worker.
        scope.launch {
            prefs.routePreferences.collect { preferences ->
                dao.observeCommutes().first()
                    .filter { (it.polyline != null && it.routeAvoidMask != preferences.mask) || _routeJobs.value[it.id] == RouteJob.Running }
                    .forEach { requestRoute(it.id, force = true) }
            }
        }
    }

    val commutes: Flow<List<Commute>> = combine(dao.observeCommutes(), places, prefs.routePreferences) { rows, places, preferences ->
        val byId = places.associateBy { it.id }
        rows.mapNotNull { row ->
            row.toDomain(byId)?.let { if (row.routeAvoidMask == preferences.mask) it else it.copy(route = null) }
        }
    }

    fun commute(id: Long): Flow<Commute?> = commutes.map { list -> list.firstOrNull { it.id == id } }

    suspend fun place(id: Long): Place? = dao.place(id)?.toDomain()

    /**
     * Salva il luogo e ne restituisce l'id. Se la posizione è cambiata, i percorsi che lo usano
     * non sono più validi: vengono cancellati e ricalcolati.
     */
    suspend fun savePlace(place: Place): Long {
        if (place.id == 0L) return dao.insertPlace(place.toEntity())
        val old = dao.place(place.id)
        dao.updatePlace(place.toEntity())
        if (old != null && (old.lat != place.location.latitude || old.lon != place.location.longitude)) {
            dao.commutesUsing(place.id).forEach { commute ->
                dao.clearRoute(commute.id)
                requestRoute(commute.id, force = true)
            }
        }
        return place.id
    }

    suspend fun commutesUsing(placeId: Long): Int = dao.commutesUsing(placeId).size

    /** Elimina il luogo e, a cascata, i tragitti che lo usano. */
    suspend fun deletePlace(id: Long) = dao.deletePlace(id)

    suspend fun saveCommute(id: Long, fromPlaceId: Long, toPlaceId: Long, days: Set<DayOfWeek>, roundTrip: Boolean): Long {
        val mask = DaysMask.encode(days)
        if (id == 0L) {
            val newId = dao.insertCommute(CommuteEntity(fromPlaceId = fromPlaceId, toPlaceId = toPlaceId, daysMask = mask, roundTrip = roundTrip))
            requestRoute(newId)
            return newId
        }
        val old = dao.commute(id)
        dao.updateCommuteSettings(id, fromPlaceId, toPlaceId, mask, roundTrip)
        if (old == null || old.fromPlaceId != fromPlaceId || old.toPlaceId != toPlaceId || old.polyline == null) {
            dao.clearRoute(id)
            requestRoute(id, force = true)
        }
        return id
    }

    suspend fun deleteCommute(id: Long) {
        generations[id] = sequence.incrementAndGet()
        dao.deleteCommute(id)
        _routeJobs.update { it - id }
    }

    /** Calcola il percorso con OpenRouteService, una volta sola, e lo salva in locale. */
    @Synchronized
    fun requestRoute(commuteId: Long, force: Boolean = false) {
        if (!force && _routeJobs.value[commuteId] == RouteJob.Running) return
        val generation = sequence.incrementAndGet()
        generations[commuteId] = generation
        _routeJobs.update { it + (commuteId to RouteJob.Running) }
        scope.launch {
            val outcome = runCatching {
                val preferences = prefs.routePreferences.first()
                val commute = dao.commute(commuteId) ?: return@runCatching
                val from = dao.place(commute.fromPlaceId) ?: return@runCatching
                val to = dao.place(commute.toPlaceId) ?: return@runCatching
                val route = ors.directions(GeoPoint(from.lat, from.lon), GeoPoint(to.lat, to.lon), preferences)
                // An older response must never replace a route requested with newer options.
                routeWrites.withLock {
                    if (generations[commuteId] == generation && prefs.routePreferences.first() == preferences) {
                        dao.updateRoute(commuteId, route.distanceMeters, route.durationSeconds, route.encodedPolyline, Instant.now(), preferences.mask)
                    }
                }
            }
            val error = outcome.exceptionOrNull()
            if (error is CancellationException) throw error
            if (generations[commuteId] == generation) {
                _routeJobs.update { jobs ->
                    if (error == null) jobs - commuteId
                    else jobs + (commuteId to RouteJob.Failed((error as? OrsException)?.reason ?: OrsException.Reason.SERVER))
                }
            }
        }
    }

    /** Distributori entro [bufferMeters] dal percorso, con un prezzo aggiornato del carburante dell'auto. */
    suspend fun offersAlong(route: Route, car: CarProfile, bufferMeters: Double): List<RouteOffer> {
        val corridor = withContext(Dispatchers.Default) { RouteCorridor(route.points, bufferMeters) }
        val candidates = prices.offersInArea(corridor.boundingBox, car.fuel, car.serviceMode)
        return withContext(Dispatchers.Default) {
            candidates.mapNotNull { offer ->
                val position = offer.station.location?.let(corridor::locate) ?: return@mapNotNull null
                RouteOffer(offer.station, offer.price, position)
            }
        }
    }

    /** Distributori lungo il tragitto con il risparmio netto; null se il percorso non è ancora calcolato. */
    suspend fun advise(
        commute: Commute,
        car: CarProfile,
        bufferMeters: Double,
        brands: Set<BrandGroup>,
        sort: CommuteSort = CommuteSort.PRICE,
    ): CommuteResult? {
        val route = commute.route ?: return null
        val offers = offersAlong(route, car, bufferMeters)
        return CommuteRanker.rank(offers, prices.nationalAverage(car.fuel, car.serviceMode), car, sort, brands)
    }

    suspend fun searchAddress(text: String, focus: GeoPoint?): List<AddressSuggestion> = ors.searchAddress(text, focus)

    suspend fun addressOf(point: GeoPoint): String? = ors.reverse(point)?.label
}
