package it.faiilpieno.ui.map

import android.graphics.RectF
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.faiilpieno.R
import it.faiilpieno.domain.commute.Commute
import it.faiilpieno.domain.geo.BoundingBox
import it.faiilpieno.domain.map.MapClusterer
import it.faiilpieno.domain.model.GeoPoint
import it.faiilpieno.domain.nearby.Offer
import it.faiilpieno.ui.components.rememberFullSheetState
import it.faiilpieno.ui.format.distanceText
import it.faiilpieno.ui.format.fuelAndModeLabel
import it.faiilpieno.ui.routes.commuteTitle
import it.faiilpieno.ui.routes.durationText
import it.faiilpieno.ui.station.StationDetailSheet
import it.faiilpieno.ui.theme.LocalPriceColors
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Point
import java.util.Locale
import kotlin.math.floor
import kotlin.math.min

private const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/liberty"
private const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"
private val ITALY = LatLng(42.5, 12.5)

/** Zoom per la posizione dell'utente e per un distributore scelto dall'elenco. */
private const val USER_ZOOM = 13.0
private const val DETAIL_ZOOM = 14.0

/** Stile OpenFreeMap adatto al tema, condiviso da tutte le mappe dell'app. */
internal fun mapStyleUrl(dark: Boolean): String = if (dark) STYLE_DARK else STYLE_LIGHT

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(onOpenCommute: (Long) -> Unit, viewModel: MapViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val priceColors = LocalPriceColors.current
    val scheme = MaterialTheme.colorScheme
    val markerStyle = remember(priceColors, scheme) {
        MarkerStyle(
            tones = mapOf(
                PillTone.CHEAPER to PillColors(priceColors.cheaperContainer, priceColors.onCheaperContainer),
                PillTone.PRICIER to PillColors(priceColors.pricierContainer, priceColors.onPricierContainer),
                PillTone.NEUTRAL to PillColors(priceColors.neutralContainer, priceColors.onNeutralContainer),
                PillTone.CLUSTER to PillColors(scheme.inverseSurface, scheme.inverseOnSurface),
            ),
            border = scheme.outline,
            selectedBorder = scheme.onSurface,
            dot = scheme.onSurface,
            dotStroke = scheme.surface,
        )
    }
    val halo = scheme.surface
    val routeColor = scheme.primary
    val otherRouteColor = scheme.tertiary
    val clusterTemplate = stringResource(R.string.map_cluster_label)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val touchPx = with(density) { 24.dp.toPx() }
    val fitPadding = with(density) { 64.dp.roundToPx() }

    var selectedStation by rememberSaveable { mutableStateOf<Long?>(null) }
    // Resta evidenziato anche dopo aver chiuso il dettaglio, finché non si tocca altrove.
    var highlighted by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedRoute by rememberSaveable { mutableStateOf<Long?>(null) }
    var showRoutes by rememberSaveable { mutableStateOf(false) }
    var showInfo by rememberSaveable { mutableStateOf(false) }
    val stripState = rememberLazyListState()
    val camera = rememberMapCameraState()
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    MapLifecycle(mapView)
    PersistMapCamera(map, camera)

    fun moveTo(point: GeoPoint, minZoom: Double) {
        val m = map ?: return
        camera.positionChosen = true
        m.easeCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), maxOf(m.cameraPosition.zoom, minZoom)))
    }

    val onViewport by rememberUpdatedState(viewModel::onViewportChanged)
    val onStationTapped by rememberUpdatedState { id: Long ->
        selectedStation = id
        highlighted = id
        val index = state.cheapest.indexOfFirst { it.station.id == id }
        if (index >= 0) scope.launch { stripState.animateScrollToItem(index) }
    }
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
            val m = boundMap ?: return@OnMapClickListener false
            val p = m.projection.toScreenLocation(latLng)
            val hit = RectF(p.x - touchPx, p.y - touchPx, p.x + touchPx, p.y + touchPx)
            // Prima le etichette (disegnate sopra), poi i puntini dei distributori coperti.
            val marker = m.queryRenderedFeatures(hit, LAYER_PILLS).firstOrNull()
                ?: m.queryRenderedFeatures(hit, LAYER_DOTS).firstOrNull()
            val route = m.queryRenderedFeatures(hit, LAYER_ROUTES)
                .firstNotNullOfOrNull { it.getNumberProperty("commuteId")?.toLong() }
            val center = marker?.geometry() as? Point
            when {
                marker?.getStringProperty("kind") == KIND_CLUSTER && center != null -> {
                    // Ogni tocco avvicina di due livelli, fino a mostrare i singoli distributori.
                    camera.positionChosen = true
                    val zoom = min(floor(m.cameraPosition.zoom) + 2, MapClusterer.CLUSTER_BELOW_ZOOM)
                    m.easeCamera(CameraUpdateFactory.newLatLngZoom(LatLng(center.latitude(), center.longitude()), zoom))
                    true
                }
                marker != null -> {
                    marker.getNumberProperty("id")?.toLong()?.let { onStationTapped(it) }
                    true
                }
                route != null -> { selectedRoute = route; true }
                else -> { highlighted = null; false }
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
    DisposableEffect(map, dark, markerStyle, density) {
        var active = true
        style = null
        map?.setStyle(Style.Builder().fromUri(mapStyleUrl(dark))) { loaded ->
            if (active) {
                addRouteLayers(loaded, halo.toArgb())
                addUserLayer(loaded)
                addMarkerLayers(loaded, markerStyle, density.density, density.fontScale)
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
            m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(user.latitude, user.longitude), USER_ZOOM))
            camera.position = m.cameraPosition
        }
    }
    LaunchedEffect(style, state.markers, state.nationalAverage, highlighted, markerStyle) {
        style?.getSourceAs<GeoJsonSource>(SOURCE_MARKERS)?.setGeoJson(
            markerFeatures(state.markers, state.nationalAverage, highlighted, markerStyle) { count, price ->
                String.format(Locale.ITALY, clusterTemplate, count, price)
            },
        )
    }
    LaunchedEffect(style, state.userLocation) {
        val user = state.userLocation ?: return@LaunchedEffect
        style?.setUserLocation(user.longitude, user.latitude)
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
        MapOverlay(
            state,
            selected = state.commutes.firstOrNull { it.id == selectedRoute },
            highlighted = highlighted,
            stripState = stripState,
            onRoutes = { showRoutes = true },
            onClearRoute = { selectedRoute = null },
            onFitRoute = ::focus,
            onOpenCommute = onOpenCommute,
            onInfo = { showInfo = true },
            onSelectOffer = { offer ->
                selectedStation = offer.station.id
                highlighted = offer.station.id
                offer.station.location?.let { moveTo(it, DETAIL_ZOOM) }
            },
            onLocate = {
                scope.launch {
                    val user = viewModel.locateUser() ?: return@launch
                    map?.let { m ->
                        camera.positionChosen = true
                        m.easeCamera(CameraUpdateFactory.newLatLngZoom(LatLng(user.latitude, user.longitude), USER_ZOOM))
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
    if (showInfo) MapInfoSheet(state.dataset, showLegend = true, onDismiss = { showInfo = false })
}

@Composable
private fun MapOverlay(
    state: MapUiState,
    selected: Commute?,
    highlighted: Long?,
    stripState: LazyListState,
    onRoutes: () -> Unit,
    onClearRoute: () -> Unit,
    onFitRoute: (Commute) -> Unit,
    onOpenCommute: (Long) -> Unit,
    onLocate: () -> Unit,
    onInfo: () -> Unit,
    onSelectOffer: (Offer) -> Unit,
) {
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 3.dp,
            modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
        ) {
            Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        fuelAndModeLabel(state.car.fuel, state.car.serviceMode),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    TextButton(onClick = onRoutes) {
                        Icon(painterResource(R.drawable.ic_route), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.tab_routes), Modifier.padding(start = 6.dp))
                    }
                }
                val hint = when {
                    state.dataset == null -> stringResource(R.string.map_no_data)
                    state.loading -> stringResource(R.string.map_loading)
                    state.emptyArea -> stringResource(R.string.map_empty_area)
                    state.cheapest.isEmpty() && state.markers.isNotEmpty() -> stringResource(R.string.map_zoom_in_hint)
                    else -> null
                }
                if (hint != null) {
                    Text(
                        hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp, bottom = 6.dp),
                    )
                }
                if (selected != null) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(commuteTitle(selected), style = MaterialTheme.typography.titleMedium, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        IconButton(onClick = onClearRoute) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_close)) }
                    }
                    selected.route?.let { route ->
                        Text(stringResource(R.string.map_route_summary, distanceText(route.distanceMeters), durationText(route.durationSeconds)), style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                        TextButton(onClick = { onFitRoute(selected) }, enabled = selected.route != null) { Text(stringResource(R.string.map_fit_route)) }
                        FilledTonalButton(onClick = { onOpenCommute(selected.id) }) { Text(stringResource(R.string.action_details)) }
                    }
                }
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                MapInfoButton(onClick = onInfo)
                Spacer(Modifier.weight(1f))
                FloatingActionButton(onClick = onLocate) {
                    Icon(painterResource(R.drawable.ic_my_location), contentDescription = stringResource(R.string.map_my_location))
                }
            }
            if (state.cheapest.isNotEmpty()) {
                CheapestStrip(state.cheapest, state.nationalAverage, state.userLocation, highlighted, stripState, onSelect = onSelectOffer)
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
