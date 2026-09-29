package it.faiilpieno.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import it.faiilpieno.domain.commute.Commute
import it.faiilpieno.domain.map.MapMarker
import it.faiilpieno.domain.pricing.deltaCents
import it.faiilpieno.ui.format.Fmt
import org.maplibre.android.maps.ImageContent
import org.maplibre.android.maps.ImageStretches
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.ceil

internal const val SOURCE_MARKERS = "markers"
internal const val LAYER_DOTS = "markers-dots"
internal const val LAYER_PILLS = "markers-pills"
internal const val SOURCE_ROUTES = "saved-routes"
internal const val LAYER_ROUTES = "saved-routes-lines"
private const val LAYER_ROUTES_HALO = "saved-routes-halo"
private const val SOURCE_USER = "user"
private const val LAYER_USER = "user-dot"

internal const val KIND_STATION = "station"
internal const val KIND_CLUSTER = "cluster"

private const val PILL_RADIUS_DP = 9f
private const val BORDER_DP = 1f
private const val SELECTED_BORDER_DP = 2.5f

/** Soglia oltre la quale un prezzo è sopra o sotto la media, come nei badge della scheda Oggi. */
private const val NEUTRAL_CENTS = 1

internal enum class PillTone { CHEAPER, PRICIER, NEUTRAL, CLUSTER }

internal data class PillColors(val container: Color, val content: Color)

/** Colori delle etichette sulla mappa: gli stessi (a contrasto AA) dei badge della scheda Oggi. */
@Immutable
internal data class MarkerStyle(
    val tones: Map<PillTone, PillColors>,
    val border: Color,
    val selectedBorder: Color,
    val dot: Color,
    val dotStroke: Color,
)

private fun imageName(tone: PillTone, selected: Boolean) = "pill-" + tone.name.lowercase() + if (selected) "-sel" else ""

/**
 * Etichette a pillola: il prezzo sta su uno sfondo colorato con la freccia ▼/▲, e un puntino
 * segna il punto esatto. Tra etichette sovrapposte vince la più economica; le altre restano
 * come puntini toccabili.
 */
internal fun addMarkerLayers(style: Style, markerStyle: MarkerStyle, density: Float, fontScale: Float) {
    val radius = ceil(PILL_RADIUS_DP * density)
    val size = (2 * radius + 2).toInt()
    val images = HashMap<String, Bitmap>()
    PillTone.entries.forEach { tone ->
        val colors = markerStyle.tones.getValue(tone)
        images[imageName(tone, false)] = pillBitmap(size, radius, density * BORDER_DP, colors.container, markerStyle.border)
        images[imageName(tone, true)] = pillBitmap(size, radius, density * SELECTED_BORDER_DP, colors.container, markerStyle.selectedBorder)
    }
    // Si allunga solo la parte centrale: gli angoli arrotondati restano intatti.
    val stretch = listOf(ImageStretches(radius, radius + 2))
    style.addImages(images, stretch, stretch, ImageContent(0f, 0f, size.toFloat(), size.toFloat()))

    style.addSource(GeoJsonSource(SOURCE_MARKERS, FeatureCollection.fromFeatures(emptyList())))
    style.addLayer(
        CircleLayer(LAYER_DOTS, SOURCE_MARKERS).withProperties(
            PropertyFactory.circleRadius(Expression.get("dot")),
            PropertyFactory.circleColor(markerStyle.dot.toArgb()),
            PropertyFactory.circleStrokeColor(markerStyle.dotStroke.toArgb()),
            PropertyFactory.circleStrokeWidth(1.5f),
        ),
    )
    style.addLayer(
        SymbolLayer(LAYER_PILLS, SOURCE_MARKERS).withProperties(
            PropertyFactory.iconImage(Expression.get("icon")),
            PropertyFactory.iconTextFit("both"),
            PropertyFactory.iconTextFitPadding(arrayOf(3f, 8f, 3f, 8f)),
            PropertyFactory.textField(Expression.get("label")),
            PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
            // Segue il testo ingrandito, ma non oltre 1,5× per non coprire tutta la mappa.
            PropertyFactory.textSize(13f * fontScale.coerceIn(1f, 1.5f)),
            PropertyFactory.textColor(Expression.toColor(Expression.get("fg"))),
            PropertyFactory.textAnchor("bottom"),
            PropertyFactory.textOffset(arrayOf(0f, -0.75f)),
            // Chiave più bassa = piazzata per prima: in caso di collisione vince il prezzo più basso.
            PropertyFactory.symbolSortKey(Expression.get("sort")),
        ),
    )
}

private fun pillBitmap(size: Int, radius: Float, stroke: Float, fill: Color, border: Color): Bitmap {
    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)
    val rect = RectF(stroke / 2, stroke / 2, size - stroke / 2, size - stroke / 2)
    val corner = radius - stroke / 2
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = fill.toArgb()
    canvas.drawRoundRect(rect, corner, corner, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = stroke
    paint.color = border.toArgb()
    canvas.drawRoundRect(rect, corner, corner, paint)
    return bitmap
}

/**
 * Un distributore: "▼ 1,729" colorato rispetto alla media nazionale (la freccia porta la stessa
 * informazione del colore). Un gruppo: "12 · da 1,729", con il prezzo del più economico.
 */
internal fun markerFeatures(
    markers: List<MapMarker>,
    nationalAverage: Int?,
    selected: Long?,
    style: MarkerStyle,
    clusterLabel: (count: Int, price: String) -> String,
): FeatureCollection = FeatureCollection.fromFeatures(
    markers.mapNotNull { marker ->
        val price = marker.cheapest.price.priceMilli
        when (marker) {
            is MapMarker.Single -> {
                val location = marker.cheapest.station.location ?: return@mapNotNull null
                val id = marker.cheapest.station.id
                val delta = nationalAverage?.let { deltaCents(price, it) } ?: 0
                val (arrow, tone) = when {
                    delta <= -NEUTRAL_CENTS -> "▼ " to PillTone.CHEAPER
                    delta >= NEUTRAL_CENTS -> "▲ " to PillTone.PRICIER
                    else -> "" to PillTone.NEUTRAL
                }
                val isSelected = id == selected
                Feature.fromGeometry(Point.fromLngLat(location.longitude, location.latitude)).apply {
                    addStringProperty("kind", KIND_STATION)
                    addNumberProperty("id", id)
                    addStringProperty("label", arrow + Fmt.priceNumber(price))
                    addStringProperty("icon", imageName(tone, isSelected))
                    addStringProperty("fg", hex(style.tones.getValue(tone).content))
                    addNumberProperty("dot", if (isSelected) 5f else 3.5f)
                    addNumberProperty("sort", if (isSelected) -1 else price)
                }
            }
            is MapMarker.Cluster -> Feature.fromGeometry(Point.fromLngLat(marker.center.longitude, marker.center.latitude)).apply {
                addStringProperty("kind", KIND_CLUSTER)
                addStringProperty("label", clusterLabel(marker.count, Fmt.priceNumber(price)))
                addStringProperty("icon", imageName(PillTone.CLUSTER, false))
                addStringProperty("fg", hex(style.tones.getValue(PillTone.CLUSTER).content))
                addNumberProperty("dot", 3.5f)
                addNumberProperty("sort", price)
            }
        }
    },
)

internal fun addUserLayer(style: Style) {
    style.addSource(GeoJsonSource(SOURCE_USER, FeatureCollection.fromFeatures(emptyList())))
    style.addLayer(
        CircleLayer(LAYER_USER, SOURCE_USER).withProperties(
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleColor(Color(0xFF1A73E8).toArgb()),
            PropertyFactory.circleStrokeColor(Color.White.toArgb()),
            PropertyFactory.circleStrokeWidth(3f),
        ),
    )
}

internal fun Style.setUserLocation(longitude: Double, latitude: Double) {
    getSourceAs<GeoJsonSource>(SOURCE_USER)?.setGeoJson(Point.fromLngLat(longitude, latitude))
}

internal fun addRouteLayers(style: Style, haloColor: Int) {
    style.addSource(GeoJsonSource(SOURCE_ROUTES, FeatureCollection.fromFeatures(emptyList())))
    style.addLayer(LineLayer(LAYER_ROUTES_HALO, SOURCE_ROUTES).withProperties(
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

internal fun routeFeatures(commutes: List<Commute>, selected: Long?, primary: Color, secondary: Color): FeatureCollection =
    FeatureCollection.fromFeatures(commutes.mapNotNull { commute ->
        val points = commute.route?.points?.takeIf { it.size >= 2 } ?: return@mapNotNull null
        val active = commute.id == selected
        Feature.fromGeometry(LineString.fromLngLats(points.map { Point.fromLngLat(it.longitude, it.latitude) })).apply {
            addNumberProperty("commuteId", commute.id)
            addStringProperty("color", hex(if (active) primary else secondary))
            addNumberProperty("width", if (active) 7f else 4f)
            addNumberProperty("order", if (active) 1 else 0)
        }
    })

private fun hex(color: Color): String = String.format("#%06X", color.toArgb() and 0xFFFFFF)
