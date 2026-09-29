package it.faiilpieno.ui.map

import android.graphics.RectF
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.rememberCoroutineScope
import it.faiilpieno.domain.commute.Commute
import it.faiilpieno.ui.routes.commuteTitle
import it.faiilpieno.ui.routes.durationText
import it.faiilpieno.ui.format.distanceText
import it.faiilpieno.ui.components.rememberFullSheetState
import kotlinx.coroutines.launch
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.geojson.LineString
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.faiilpieno.R
import it.faiilpieno.domain.geo.BoundingBox
import it.faiilpieno.domain.model.GeoPoint
import it.faiilpieno.domain.nearby.Offer
import it.faiilpieno.domain.pricing.deltaCents
import it.faiilpieno.ui.format.Fmt
import it.faiilpieno.ui.format.fuelAndModeLabel
import it.faiilpieno.ui.station.StationDetailSheet
import it.faiilpieno.ui.theme.LocalPriceColors
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/liberty"
private const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"

/** Stile OpenFreeMap adatto al tema, condiviso da tutte le mappe dell'app. */
internal fun mapStyleUrl(dark: Boolean): String = if (dark) STYLE_DARK else STYLE_LIGHT
private const val SOURCE_ROUTES = "saved-routes"
private const val LAYER_ROUTES = "saved-routes-lines"
private const val SOURCE_STATIONS = "stations"
private const val SOURCE_USER = "user"
private const val LAYER_CIRCLES = "stations-circles"
private const val LAYER_LABELS = "stations-labels"
private const val LAYER_USER = "user-dot"
private val ITALY = LatLng(42.5, 12.5)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(onOpenCommute: (Long) -> Unit, viewModel: MapViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val priceColors = LocalPriceColors.current
    val neutral = MaterialTheme.colorScheme.secondary
    val halo = MaterialTheme.colorScheme.surface
    val routeColor = MaterialTheme.colorScheme.primary
    val otherRouteColor = MaterialTheme.colorScheme.tertiary
    val scope = rememberCoroutineScope()
    val touchPx = with(LocalDensity.current) { 24.dp.toPx() }
    val fitPadding = with(LocalDensity.current) { 64.dp.roundToPx() }

    var selectedStation by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedRoute by rememberSaveable { mutableStateOf<Long?>(null) }
    var showRoutes by rememberSaveable { mutableStateOf(false) }
    val camera = rememberMapCameraState()
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    MapLifecycle(mapView)
    PersistMapCamera(map, camera)

    val onViewport by rememberUpdatedState(viewModel::onViewportChanged)
    DisposableEffect(mapView) {
        var active = true
        var boundMap: MapLibreMap? = null
        val idle = MapLibreMap.OnCameraIdleListener {
            boundMap?.let { m ->
                camera.position = m.cameraPosition
                val b = m.projection.visibleRegion.latLngBounds
                onViewport(BoundingBox(b.latitudeSouth, b.latitudeNorth, b.longitudeWest, b.longitudeEast), m.cameraPosition.zoom)
            }
        }
        val clicked = MapLibreMap.OnMapClickListener { latLng ->
            val m = boundMap
            if (m == null) false else {
                val p = m.projection.toScreenLocation(latLng)
                val hit = RectF(p.x - touchPx, p.y - touchPx, p.x + touchPx, p.y + touchPx)
                val station = m.queryRenderedFeatures(hit, LAYER_CIRCLES, LAYER_LABELS)
                    .firstNotNullOfOrNull { it.getNumberProperty("id")?.toLong() }
                val route = m.queryRenderedFeatures(hit, LAYER_ROUTES)
                    .firstNotNullOfOrNull { it.getNumberProperty("commuteId")?.toLong() }
                when {
                    station != null -> { selectedStation = station; true }
                    route != null -> { selectedRoute = route; true }
                    else -> false
                }
            }
        }
        mapView.getMapAsync { m ->
            if (active) {
                boundMap = m
                m.uiSettings.setRotateGesturesEnabled(false)
                m.uiSettings.setTiltGesturesEnabled(false)
                m.uiSettings.setAttributionEnabled(false)
                m.uiSettings.setLogoEnabled(false)
                m.addOnCameraIdleListener(idle)
                m.addOnMapClickListener(clicked)
                m.moveCamera(camera.position?.let(CameraUpdateFactory::newCameraPosition)
                    ?: CameraUpdateFactory.newLatLngZoom(ITALY, 5.0))
                map = m
            }
        }
        onDispose {
            active = false
            boundMap?.removeOnCameraIdleListener(idle)
            boundMap?.removeOnMapClickListener(clicked)
        }
    }
    // Cambiare tema ricarica solo lo stile, senza duplicare listener o spostare la camera.
    DisposableEffect(map, dark, halo) {
        var active = true
        style = null
        map?.setStyle(Style.Builder().fromUri(mapStyleUrl(dark))) { loaded ->
            if (active) {
                addRouteLayers(loaded, halo.toArgb())
                addStationLayers(loaded, halo.toArgb(), if (dark) Color.White.toArgb() else Color(0xFF1C1B1F).toArgb())
                style = loaded
                map?.let { m ->
                    val b = m.projection.visibleRegion.latLngBounds
                    onViewport(BoundingBox(b.latitudeSouth, b.latitudeNorth, b.longitudeWest, b.longitudeEast), m.cameraPosition.zoom)
                }
            }
        }
        onDispose { active = false }
    }

    // Solo il primo fix può centrare automaticamente: un gesto manuale ha sempre precedenza.
    LaunchedEffect(map, state.userLocation) {
        val m = map ?: return@LaunchedEffect
        val user = state.userLocation ?: return@LaunchedEffect
        if (!camera.positionChosen) {
            camera.positionChosen = true
            m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(user.latitude, user.longitude), 13.0))
            camera.position = m.cameraPosition
        }
    }
    LaunchedEffect(style, state.offers, state.nationalAverage, priceColors, neutral) {
        style?.getSourceAs<GeoJsonSource>(SOURCE_STATIONS)?.setGeoJson(
            toFeatures(state.offers, state.nationalAverage, priceColors.cheaper, priceColors.pricier, neutral),
        )
    }
    LaunchedEffect(style, state.userLocation) {
        val user = state.userLocation ?: return@LaunchedEffect
        style?.getSourceAs<GeoJsonSource>(SOURCE_USER)?.setGeoJson(Point.fromLngLat(user.longitude, user.latitude))
    }
    LaunchedEffect(style, state.commutes, selectedRoute, routeColor, otherRouteColor) {
        style?.getSourceAs<GeoJsonSource>(SOURCE_ROUTES)?.setGeoJson(
            routeFeatures(state.commutes, selectedRoute, routeColor, otherRouteColor),
        )
    }
    fun focus(commute: Commute) {
        val m = map ?: return
        val route = commute.route ?: return
        camera.positionChosen = true
        fitMapPoints(m, route.points.map { LatLng(it.latitude, it.longitude) }, fitPadding)
        camera.position = m.cameraPosition
    }
    val mapDescription = stringResource(R.string.map_a11y)
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize().semantics { contentDescription = mapDescription })
        MapOverlay(state,
            selected = state.commutes.firstOrNull { it.id == selectedRoute },
            onRoutes = { showRoutes = true }, onClearRoute = { selectedRoute = null },
            onFitRoute = ::focus, onOpenCommute = onOpenCommute,
            onLocate = {
                scope.launch {
                    val user = viewModel.locateUser() ?: return@launch
                    map?.let { m ->
                        camera.positionChosen = true
                        m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(user.latitude, user.longitude), 13.0))
                        camera.position = m.cameraPosition
                    }
                }
            },
        )
    }
    if (showRoutes) ModalBottomSheet(onDismissRequest = { showRoutes = false }, sheetState = rememberFullSheetState()) {
        LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            item { Text(stringResource(R.string.map_saved_routes), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(24.dp)) }
            if (state.commutes.isEmpty()) item {
                Text(stringResource(R.string.map_routes_empty), modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
            }
            items(state.commutes, key = { it.id }) { commute ->
                Surface(onClick = {
                    showRoutes = false
                    if (commute.route == null) onOpenCommute(commute.id)
                    else { selectedRoute = commute.id; focus(commute) }
                }) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(commuteTitle(commute), style = MaterialTheme.typography.titleMedium)
                        Text(commute.route?.let { stringResource(R.string.map_route_summary, distanceText(it.distanceMeters), durationText(it.durationSeconds)) }
                            ?: stringResource(R.string.commute_not_computed), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                HorizontalDivider()
            }
        }
    }
    selectedStation?.let { id -> StationDetailSheet(id, onDismiss = { selectedStation = null }) }
}

@Composable
private fun MapOverlay(
    state: MapUiState, selected: Commute?, onRoutes: () -> Unit, onClearRoute: () -> Unit,
    onFitRoute: (Commute) -> Unit, onOpenCommute: (Long) -> Unit, onLocate: () -> Unit,
) {
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 3.dp,
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(fuelAndModeLabel(state.car.fuel, state.car.serviceMode), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = onRoutes) {
                        Icon(painterResource(R.drawable.ic_route), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.tab_routes), Modifier.padding(start = 6.dp))
                    }
                }
                val hint = when {
                    state.dataset == null -> stringResource(R.string.map_no_data)
                    state.zoomTooLow -> stringResource(R.string.map_zoom_in_hint)
                    else -> "▼ " + stringResource(R.string.map_legend_below) + "   ▲ " + stringResource(R.string.map_legend_above)
                }
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (selected != null) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(commuteTitle(selected), style = MaterialTheme.typography.titleMedium, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        IconButton(onClick = onClearRoute) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_close)) }
                    }
                    selected.route?.let { route ->
                        Text(stringResource(R.string.map_route_summary, distanceText(route.distanceMeters), durationText(route.durationSeconds)), style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onFitRoute(selected) }, enabled = selected.route != null) { Text(stringResource(R.string.map_fit_route)) }
                        FilledTonalButton(onClick = { onOpenCommute(selected.id) }) { Text(stringResource(R.string.action_details)) }
                    }
                }
            }
        }

        FloatingActionButton(onClick = onLocate, modifier = Modifier.align(Alignment.BottomEnd)) {
            Icon(painterResource(R.drawable.ic_my_location), contentDescription = stringResource(R.string.map_my_location))
        }

        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                state.dataset?.let {
                    Text(stringResource(R.string.source_short, Fmt.dayMonth(it.extractionDate)), style = MaterialTheme.typography.labelSmall)
                }
                Text(stringResource(R.string.map_attribution), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Collega il ciclo di vita della MapView a quello della schermata. */
@Composable
internal fun MapLifecycle(mapView: MapView) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { mapView.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> { mapView.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> { mapView.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> { mapView.onStop(); started = false }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }
}

private fun addStationLayers(style: Style, haloColor: Int, textColor: Int) {
    style.addSource(GeoJsonSource(SOURCE_STATIONS, FeatureCollection.fromFeatures(emptyList())))
    style.addSource(GeoJsonSource(SOURCE_USER, FeatureCollection.fromFeatures(emptyList())))
    style.addLayer(
        CircleLayer(LAYER_CIRCLES, SOURCE_STATIONS).withProperties(
            PropertyFactory.circleRadius(7f),
            PropertyFactory.circleColor(Expression.toColor(Expression.get("color"))),
            PropertyFactory.circleStrokeColor(haloColor),
            PropertyFactory.circleStrokeWidth(2f),
            // Chiave più alta = disegnato sopra: i più economici restano visibili.
            PropertyFactory.circleSortKey(Expression.get("drawOrder")),
        ),
    )
    style.addLayer(
        SymbolLayer(LAYER_LABELS, SOURCE_STATIONS).withProperties(
            PropertyFactory.textField(Expression.get("label")),
            PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
            PropertyFactory.textSize(13f),
            PropertyFactory.textColor(textColor),
            PropertyFactory.textHaloColor(haloColor),
            PropertyFactory.textHaloWidth(2f),
            PropertyFactory.textAnchor("bottom"),
            PropertyFactory.textOffset(arrayOf(0f, -0.7f)),
            // Chiave più bassa = piazzata per prima: in caso di collisione vince il prezzo più basso.
            PropertyFactory.symbolSortKey(Expression.get("placement")),
        ),
    )
    style.addLayer(
        CircleLayer(LAYER_USER, SOURCE_USER).withProperties(
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleColor(Color(0xFF1A73E8).toArgb()),
            PropertyFactory.circleStrokeColor(Color.White.toArgb()),
            PropertyFactory.circleStrokeWidth(3f),
        ),
    )
}

/**
 * Etichetta "▼ 1,799" / "▲ 1,899": la freccia porta l'informazione sopra/sotto media
 * anche per chi non distingue i colori.
 */
private fun toFeatures(offers: List<Offer>, nationalAverage: Int?, cheaper: Color, pricier: Color, neutral: Color): FeatureCollection =
    FeatureCollection.fromFeatures(
        offers.mapNotNull { offer ->
            val location: GeoPoint = offer.station.location ?: return@mapNotNull null
            val delta = nationalAverage?.let { deltaCents(offer.price.priceMilli, it) } ?: 0
            val (arrow, color) = when {
                delta <= -1 -> "▼ " to cheaper
                delta >= 1 -> "▲ " to pricier
                else -> "" to neutral
            }
            Feature.fromGeometry(Point.fromLngLat(location.longitude, location.latitude)).apply {
                addNumberProperty("id", offer.station.id)
                addStringProperty("label", arrow + Fmt.priceNumber(offer.price.priceMilli))
                addStringProperty("color", String.format("#%06X", color.toArgb() and 0xFFFFFF))
                addNumberProperty("drawOrder", -offer.price.priceMilli)
                addNumberProperty("placement", offer.price.priceMilli)
            }
        },
    )

private fun addRouteLayers(style: Style, haloColor: Int) {
    style.addSource(GeoJsonSource(SOURCE_ROUTES, FeatureCollection.fromFeatures(emptyList())))
    style.addLayer(LineLayer("saved-routes-halo", SOURCE_ROUTES).withProperties(
        PropertyFactory.lineColor(haloColor), PropertyFactory.lineWidth(10f),
        PropertyFactory.lineCap("round"), PropertyFactory.lineJoin("round"),
    ))
    style.addLayer(LineLayer(LAYER_ROUTES, SOURCE_ROUTES).withProperties(
        PropertyFactory.lineColor(Expression.toColor(Expression.get("color"))),
        PropertyFactory.lineWidth(Expression.get("width")),
        PropertyFactory.lineSortKey(Expression.get("order")),
        PropertyFactory.lineCap("round"), PropertyFactory.lineJoin("round"),
    ))
}

private fun routeFeatures(commutes: List<Commute>, selected: Long?, primary: Color, secondary: Color): FeatureCollection =
    FeatureCollection.fromFeatures(commutes.mapNotNull { commute ->
        val points = commute.route?.points?.takeIf { it.size >= 2 } ?: return@mapNotNull null
        val active = commute.id == selected
        Feature.fromGeometry(LineString.fromLngLats(points.map { Point.fromLngLat(it.longitude, it.latitude) })).apply {
            addNumberProperty("commuteId", commute.id)
            addStringProperty("color", String.format("#%06X", (if (active) primary else secondary).toArgb() and 0xFFFFFF))
            addNumberProperty("width", if (active) 7f else 4f)
            addNumberProperty("order", if (active) 1 else 0)
        }
    })
