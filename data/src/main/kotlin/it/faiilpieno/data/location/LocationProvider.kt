package it.faiilpieno.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import android.os.CancellationSignal
import dagger.hilt.android.qualifiers.ApplicationContext
import it.faiilpieno.domain.model.GeoPoint
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

sealed interface LocationResult {
    data class Found(val point: GeoPoint) : LocationResult
    data object PermissionDenied : LocationResult
    data object ServicesOff : LocationResult
    data object Unavailable : LocationResult
}

/**
 * Posizione in primo piano con il LocationManager di sistema, senza Google Play Services.
 * Basta la posizione approssimativa: la ricerca è su qualche km.
 */
@Singleton
class LocationProvider @Inject constructor(@param:ApplicationContext private val context: Context) {

    private val manager = context.getSystemService(LocationManager::class.java)

    fun hasPermission(): Boolean = listOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ).any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    @SuppressLint("MissingPermission")
    suspend fun current(): LocationResult {
        if (!hasPermission()) return LocationResult.PermissionDenied
        if (!LocationManagerCompat.isLocationEnabled(manager)) return LocationResult.ServicesOff

        val providers = enabledProviders()
        providers.mapNotNull { manager.getLastKnownLocation(it) }
            .filter { ageMillis(it) < MAX_LAST_KNOWN_AGE_MS }
            .minByOrNull { it.accuracy }
            ?.let { return LocationResult.Found(it.toPoint()) }

        val provider = providers.firstOrNull() ?: return LocationResult.ServicesOff
        val fresh = withTimeoutOrNull(TIMEOUT_MS) { requestSingle(provider) }
            ?: providers.mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time }
        return fresh?.let { LocationResult.Found(it.toPoint()) } ?: LocationResult.Unavailable
    }

    private fun enabledProviders(): List<String> {
        val preferred = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }
        val available = manager.getProviders(true)
        return preferred.filter { it in available }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestSingle(provider: String): Location? = suspendCancellableCoroutine { cont ->
        val signal = CancellationSignal()
        cont.invokeOnCancellation { signal.cancel() }
        LocationManagerCompat.getCurrentLocation(
            manager, provider, signal, ContextCompat.getMainExecutor(context),
        ) { location -> if (cont.isActive) cont.resume(location) }
    }

    private fun ageMillis(location: Location): Long =
        (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000

    private fun Location.toPoint() = GeoPoint(latitude, longitude)

    private companion object {
        /** Per cercare distributori entro qualche km basta una posizione di pochi minuti fa. */
        const val MAX_LAST_KNOWN_AGE_MS = 10 * 60 * 1000L
        const val TIMEOUT_MS = 10_000L
    }
}
