package com.blueway.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.blueway.app.R
import com.blueway.app.data.LatLon
import com.blueway.app.data.Place
import com.blueway.app.data.Report
import com.blueway.app.data.ReportStatus
import com.blueway.app.data.ReportType
import com.blueway.app.data.RouteResult
import com.blueway.app.data.Sensor
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import java.net.URI
import com.blueway.app.CameraCommand
import org.maplibre.android.geometry.LatLng

/** Lo que el mapa debe pintar. Se recalcula desde el estado de la app. */
data class MapContent(
    val routes: List<RouteResult> = emptyList(),
    val selected: RouteResult? = null,
    val destination: LatLon? = null,
    val user: LatLon? = null,
    val origin: LatLon? = null,
    val reports: List<Report> = emptyList(),
    val sensors: List<Sensor> = emptyList(),
    val quietZones: List<Place> = emptyList(),
    val showNoise: Boolean = true,
    val noiseOffset: Int = 0,
    val navigating: Boolean = false,
)

sealed interface MapMarkerTap {
    data class ReportTap(val id: String) : MapMarkerTap
    data class SensorTap(val id: String) : MapMarkerTap
    data class ZoneTap(val index: Int) : MapMarkerTap
}

private val MALAGA = LatLng(36.7203, -4.4195)

@Composable
fun BlueWayMap(
    content: MapContent,
    camera: Flow<CameraCommand>,
    reduceMotion: Boolean,
    contentPaddingBottomPx: Int,
    contentPaddingTopPx: Int,
    contentPaddingSidePx: Int,
    onMarker: (MapMarkerTap) -> Unit,
    onStreetTap: (LatLon) -> Unit,
    onLongPress: (LatLon) -> Unit,
    onCameraIdle: (LatLon) -> Unit,
    onDestinationMoved: (Offset?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { MapView(context).apply { onCreate(Bundle()) } }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val holder = remember { CallbackHolder() }
    holder.onMarker = onMarker
    holder.onStreetTap = onStreetTap
    holder.onLongPress = onLongPress
    holder.onCameraIdle = onCameraIdle
    holder.onDestinationMoved = onDestinationMoved
    holder.destination = content.destination

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(Unit) {
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            m.uiSettings.isCompassEnabled = false
            m.uiSettings.isLogoEnabled = false
            m.uiSettings.setAttributionMargins(16, 0, 0, contentPaddingBottomPx + 8)
            m.setMinZoomPreference(10.5)
            m.cameraPosition = CameraPosition.Builder().target(MALAGA).zoom(14.4).build()
            m.setStyle(Style.Builder().fromUri("asset://map_style.json")) { s ->
                setupStyle(context, s)
                style = s
            }
            m.addOnMapClickListener { point ->
                val screen = m.projection.toScreenLocation(point)
                val box = RectF(screen.x - 36, screen.y - 36, screen.x + 36, screen.y + 36)
                val hit = m.queryRenderedFeatures(box, LAYER_MARKERS).firstOrNull()
                if (hit != null) {
                    val kind = hit.getStringProperty("kind")
                    val id = hit.getStringProperty("id")
                    when (kind) {
                        "report" -> holder.onMarker(MapMarkerTap.ReportTap(id))
                        "sensor" -> holder.onMarker(MapMarkerTap.SensorTap(id))
                        "zone" -> holder.onMarker(MapMarkerTap.ZoneTap(id.toInt()))
                    }
                } else {
                    val tight = RectF(screen.x - 14, screen.y - 14, screen.x + 14, screen.y + 14)
                    if (m.queryRenderedFeatures(tight, LAYER_NOISE).isNotEmpty()) {
                        holder.onStreetTap(LatLon(point.latitude, point.longitude))
                    }
                }
                true
            }
            m.addOnMapLongClickListener { point ->
                holder.onLongPress(LatLon(point.latitude, point.longitude))
                true
            }
            m.addOnCameraIdleListener {
                m.cameraPosition.target?.let { holder.onCameraIdle(LatLon(it.latitude, it.longitude)) }
                holder.emitDestination(m)
            }
            m.addOnCameraMoveListener { holder.emitDestination(m) }
        }
    }

    LaunchedEffect(style, content) {
        style?.let { s -> render(s, content) }
        map?.let { holder.emitDestination(it) }
    }

    LaunchedEffect(map, reduceMotion) {
        val m = map ?: return@LaunchedEffect
        camera.collect { command ->
            when (command) {
                is CameraCommand.Center -> {
                    val target = LatLng(command.point.lat, command.point.lon)
                    val update = if (command.zoom != null) {
                        CameraUpdateFactory.newLatLngZoom(target, command.zoom)
                    } else {
                        CameraUpdateFactory.newLatLng(target)
                    }
                    if (reduceMotion) m.moveCamera(update) else m.easeCamera(update, 500)
                }
                is CameraCommand.Fit -> {
                    if (command.points.size >= 2) {
                        val bounds = LatLngBounds.Builder().apply {
                            command.points.forEach { include(LatLng(it.lat, it.lon)) }
                        }.build()
                        val update = CameraUpdateFactory.newLatLngBounds(
                            bounds, contentPaddingSidePx / 2, contentPaddingTopPx, contentPaddingSidePx, contentPaddingBottomPx,
                        )
                        if (reduceMotion) m.moveCamera(update) else m.easeCamera(update, 700)
                    }
                }
                CameraCommand.FollowReset -> {
                    val update = CameraUpdateFactory.newLatLngZoom(MALAGA, 14.4)
                    if (reduceMotion) m.moveCamera(update) else m.easeCamera(update, 500)
                }
            }
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private class CallbackHolder {
    var onMarker: (MapMarkerTap) -> Unit = {}
    var onStreetTap: (LatLon) -> Unit = {}
    var onLongPress: (LatLon) -> Unit = {}
    var onCameraIdle: (LatLon) -> Unit = {}
    var onDestinationMoved: (Offset?) -> Unit = {}
    var destination: LatLon? = null

    fun emitDestination(m: MapLibreMap) {
        val d = destination
        if (d == null) {
            onDestinationMoved(null)
            return
        }
        val p: PointF = m.projection.toScreenLocation(LatLng(d.lat, d.lon))
        onDestinationMoved(Offset(p.x, p.y))
    }
}

private const val SRC_NOISE = "bw-noise"
private const val SRC_ROUTE = "bw-route"
private const val SRC_ALT = "bw-route-alt"
private const val SRC_MARKERS = "bw-markers"
private const val SRC_USER = "bw-user"
private const val LAYER_NOISE = "bw-noise-layer"
private const val LAYER_MARKERS = "bw-markers-layer"

private fun setupStyle(context: Context, style: Style) {
    ReportType.entries.forEach { style.addImage(iconName(it, false), markerBitmap(context, it.icon, 0xFFFFFFFF.toInt())) }
    ReportType.entries.forEach { style.addImage(iconName(it, true), markerBitmap(context, it.icon, 0xFF0B8A5A.toInt())) }
    style.addImage("pin", ContextCompat.getDrawable(context, R.drawable.ic_marker_pin)!!.toBitmap(120, 150))
    style.addImage("tree", ContextCompat.getDrawable(context, R.drawable.ic_marker_tree)!!.toBitmap(100, 100))
    style.addImage("sensor", ContextCompat.getDrawable(context, R.drawable.ic_marker_sensor)!!.toBitmap(84, 84))

    val firstLabel = style.layers.firstOrNull { it is SymbolLayer }?.id
    fun below(layer: Layer) {
        if (firstLabel != null) style.addLayerBelow(layer, firstLabel) else style.addLayer(layer)
    }

    style.addSource(GeoJsonSource(SRC_NOISE, URI("asset://city/noise.geojson")))
    val noise = LineLayer(LAYER_NOISE, SRC_NOISE).withProperties(
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        PropertyFactory.lineOpacity(0.5f),
        PropertyFactory.lineWidth(
            Expression.interpolate(
                Expression.exponential(1.5f), Expression.zoom(),
                Expression.stop(11, 1.5f), Expression.stop(14, 6f), Expression.stop(16, 18f), Expression.stop(18, 46f),
            ),
        ),
        PropertyFactory.lineBlur(
            Expression.interpolate(
                Expression.linear(), Expression.zoom(),
                Expression.stop(11, 1f), Expression.stop(14, 3f), Expression.stop(16, 8f), Expression.stop(18, 18f),
            ),
        ),
    )
    noise.setProperties(PropertyFactory.lineColor(noiseColorExpression(0)))
    below(noise)

    style.addSource(GeoJsonSource(SRC_ALT))
    below(
        LineLayer("bw-alt-layer", SRC_ALT).withProperties(
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineColor("#7E8FB8"),
            PropertyFactory.lineOpacity(0.75f),
            PropertyFactory.lineWidth(5f),
            PropertyFactory.lineDasharray(arrayOf(1.2f, 1.6f)),
        ),
    )
    style.addSource(GeoJsonSource(SRC_ROUTE))
    below(
        LineLayer("bw-route-casing", SRC_ROUTE).withProperties(
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineColor("#FFFFFF"),
            PropertyFactory.lineWidth(11f),
        ),
    )
    below(
        LineLayer("bw-route-line", SRC_ROUTE).withProperties(
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineColor("#1678E4"),
            PropertyFactory.lineWidth(7.5f),
        ),
    )

    style.addSource(GeoJsonSource(SRC_USER))
    style.addLayer(
        CircleLayer("bw-user-halo", SRC_USER).withProperties(
            PropertyFactory.circleColor("#4DC5F6"),
            PropertyFactory.circleOpacity(0.28f),
            PropertyFactory.circleRadius(24f),
        ),
    )
    style.addLayer(
        CircleLayer("bw-user-dot", SRC_USER).withProperties(
            PropertyFactory.circleColor("#1678E4"),
            PropertyFactory.circleRadius(10f),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(4f),
        ),
    )

    style.addSource(GeoJsonSource(SRC_MARKERS))
    style.addLayer(
        SymbolLayer(LAYER_MARKERS, SRC_MARKERS).withProperties(
            PropertyFactory.iconImage(Expression.get("icon")),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconSize(
                Expression.interpolate(
                    Expression.linear(), Expression.zoom(),
                    Expression.stop(11, 0.4f), Expression.stop(13.5, 0.62f), Expression.stop(15.5, 0.85f), Expression.stop(17, 1f),
                ),
            ),
            PropertyFactory.iconAnchor(
                Expression.match(
                    Expression.get("icon"), Expression.literal(Property.ICON_ANCHOR_CENTER),
                    Expression.stop("pin", Expression.literal(Property.ICON_ANCHOR_BOTTOM)),
                ),
            ),
        ),
    )
}

private fun iconName(type: ReportType, confirmed: Boolean) = "report-${type.name}-${if (confirmed) "c" else "p"}"

/** Colores del mapa de calor con el ajuste de la hora actual. */
private fun noiseColorExpression(offset: Int): Expression = Expression.interpolate(
    Expression.linear(),
    Expression.sum(Expression.toNumber(Expression.get("d")), Expression.literal(offset)),
    Expression.stop(48, Expression.rgb(69, 201, 137)),
    Expression.stop(55, Expression.rgb(132, 214, 120)),
    Expression.stop(60, Expression.rgb(255, 216, 94)),
    Expression.stop(66, Expression.rgb(255, 163, 72)),
    Expression.stop(72, Expression.rgb(242, 84, 72)),
    Expression.stop(78, Expression.rgb(208, 28, 63)),
)

private fun lineFeature(points: List<LatLon>): JSONObject = JSONObject()
    .put("type", "Feature")
    .put("properties", JSONObject())
    .put(
        "geometry",
        JSONObject().put("type", "LineString").put(
            "coordinates",
            JSONArray(points.map { JSONArray().put(it.lon).put(it.lat) }),
        ),
    )

private fun pointFeature(p: LatLon, props: Map<String, String>): JSONObject = JSONObject()
    .put("type", "Feature")
    .put("properties", JSONObject(props))
    .put("geometry", JSONObject().put("type", "Point").put("coordinates", JSONArray().put(p.lon).put(p.lat)))

private fun collection(features: List<JSONObject>) =
    JSONObject().put("type", "FeatureCollection").put("features", JSONArray(features)).toString()

private fun render(style: Style, c: MapContent) {
    style.getLayer(LAYER_NOISE)?.setProperties(
        PropertyFactory.visibility(if (c.showNoise) Property.VISIBLE else Property.NONE),
        PropertyFactory.lineColor(noiseColorExpression(c.noiseOffset)),
    )
    style.getSourceAs<GeoJsonSource>(SRC_ROUTE)?.setGeoJson(
        collection(listOfNotNull(c.selected?.let { lineFeature(it.points) })),
    )
    style.getSourceAs<GeoJsonSource>(SRC_ALT)?.setGeoJson(
        collection(
            if (c.navigating) {
                emptyList()
            } else {
                c.routes.filter { it !== c.selected && !it.sameAsFastest }.map { lineFeature(it.points) }
            },
        ),
    )
    style.getSourceAs<GeoJsonSource>(SRC_USER)?.setGeoJson(
        collection(listOfNotNull(c.user?.let { pointFeature(it, emptyMap()) })),
    )
    val markers = ArrayList<JSONObject>()
    c.quietZones.forEachIndexed { i, zone ->
        markers.add(pointFeature(zone.point, mapOf("icon" to "tree", "kind" to "zone", "id" to i.toString())))
    }
    c.sensors.forEach {
        markers.add(pointFeature(it.point, mapOf("icon" to "sensor", "kind" to "sensor", "id" to it.id)))
    }
    c.reports.forEach {
        val confirmed = it.status() == ReportStatus.CONFIRMED
        markers.add(pointFeature(it.point, mapOf("icon" to iconName(it.type, confirmed), "kind" to "report", "id" to it.id)))
    }
    c.destination?.let { markers.add(pointFeature(it, mapOf("icon" to "pin", "kind" to "dest", "id" to "dest"))) }
    style.getSourceAs<GeoJsonSource>(SRC_MARKERS)?.setGeoJson(collection(markers))
}

/** Marcador de aviso: icono original con un aro verde si está confirmado. */
private fun markerBitmap(context: Context, icon: Int, ring: Int): Bitmap {
    val size = 112
    val out = createBitmap(size, size)
    val canvas = Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = 0x33000A40
    canvas.drawCircle(size / 2f, size / 2f + 2, size / 2f - 2, paint)
    paint.color = ring
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4, paint)
    paint.color = 0xFFFFFFFF.toInt()
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 10, paint)
    val src = ContextCompat.getDrawable(context, icon)!!.toBitmap(size - 24, size - 24)
    canvas.drawBitmap(src, 12f, 12f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    return out
}
