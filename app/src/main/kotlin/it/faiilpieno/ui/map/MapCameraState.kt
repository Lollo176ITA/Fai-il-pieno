package it.faiilpieno.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap

/** Memoria della camera indipendente dalla MapView, anche durante il ripristino della navigazione. */
internal class MapCameraState(var position: CameraPosition? = null, var positionChosen: Boolean = false)

@Composable
internal fun rememberMapCameraState(): MapCameraState = rememberSaveable(
    saver = listSaver<MapCameraState, Any?>(
        save = { listOf(it.position, it.positionChosen) },
        restore = { MapCameraState(it[0] as CameraPosition?, it[1] as Boolean) },
    ),
) { MapCameraState() }

@Composable
internal fun PersistMapCamera(map: MapLibreMap?, camera: MapCameraState) {
    DisposableEffect(map, camera) {
        val moved = MapLibreMap.OnCameraMoveListener {
            camera.position = map?.cameraPosition
        }
        val started = MapLibreMap.OnCameraMoveStartedListener { reason ->
            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) camera.positionChosen = true
        }
        map?.addOnCameraMoveListener(moved)
        map?.addOnCameraMoveStartedListener(started)
        onDispose {
            map?.removeOnCameraMoveListener(moved)
            map?.removeOnCameraMoveStartedListener(started)
        }
    }
}

/** Inquadratura richiesta esplicitamente, mai applicata quando arrivano nuovi prezzi o percorsi. */
internal fun fitMapPoints(map: MapLibreMap, coordinates: List<LatLng>, padding: Int) {
    if (coordinates.isEmpty()) return
    if (coordinates.distinct().size < 2) {
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(coordinates.first(), 14.0))
    } else {
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(coordinates).build(), padding))
    }
}
