package it.faiilpieno.ui.routes

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import it.faiilpieno.R
import it.faiilpieno.domain.commute.Route
import it.faiilpieno.ui.map.MapLifecycle
import it.faiilpieno.ui.map.MapCameraState
import it.faiilpieno.ui.map.PersistMapCamera
import it.faiilpieno.ui.map.fitMapPoints
import it.faiilpieno.ui.map.mapStyleUrl
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** The map keeps native pan/zoom gestures; the fit button restores the route overview. */
@Composable
internal fun RouteMap(route: Route, camera: MapCameraState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val color = MaterialTheme.colorScheme.primary.toArgb()
    val halo = MaterialTheme.colorScheme.surface.toArgb()
    val view = remember { MapView(context).apply { onCreate(null) } }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    MapLifecycle(view)
    PersistMapCamera(map, camera)
    val points = remember(route.points) { route.points.map { Point.fromLngLat(it.longitude, it.latitude) } }
    fun fit(m: MapLibreMap) {
        camera.positionChosen = true
        fitMapPoints(m, route.points.map { LatLng(it.latitude, it.longitude) }, 64)
        camera.position = m.cameraPosition
    }
    DisposableEffect(view, route.points, dark, color) {
        var active = true
        view.getMapAsync { m ->
            if (active) {
                camera.position?.let { m.moveCamera(CameraUpdateFactory.newCameraPosition(it)) }
                map = m
                m.uiSettings.setTiltGesturesEnabled(false)
                m.setStyle(Style.Builder().fromUri(mapStyleUrl(dark))) { style ->
                    if (active && points.size >= 2) {
                        style.addSource(GeoJsonSource("route-line", LineString.fromLngLats(points)))
                        style.addLayer(LineLayer("route-halo", "route-line").withProperties(lineColor(halo), lineWidth(9f), lineCap("round"), lineJoin("round")))
                        style.addLayer(LineLayer("route-path", "route-line").withProperties(lineColor(color), lineWidth(5f), lineCap("round"), lineJoin("round")))
                        style.addSource(GeoJsonSource("route-ends", FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(points.first()), Feature.fromGeometry(points.last())))))
                        style.addLayer(CircleLayer("route-endpoints", "route-ends").withProperties(circleColor(color), circleRadius(7f), circleStrokeColor(halo), circleStrokeWidth(3f)))
                    }
                    if (active) view.post { if (active && camera.position == null) fit(m) }
                }
            }
        }
        onDispose { active = false }
    }
    val description = stringResource(R.string.route_map_a11y)
    Box(modifier.height(280.dp).clip(MaterialTheme.shapes.extraLarge)) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize().semantics { contentDescription = description })
        FilledTonalIconButton(onClick = { map?.let { fit(it) } }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            Icon(painterResource(R.drawable.ic_route), stringResource(R.string.route_map_fit))
        }
    }
}
