package com.blueway.app

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blueway.app.data.AppSettings
import com.blueway.app.data.CityData
import com.blueway.app.data.CityLoader
import com.blueway.app.data.DataSource
import com.blueway.app.data.LatLon
import com.blueway.app.data.LocalStore
import com.blueway.app.data.NoiseLevel
import com.blueway.app.data.Place
import com.blueway.app.data.PlaceCategory
import com.blueway.app.data.PlaceSearchClient
import com.blueway.app.data.Report
import com.blueway.app.data.ReportRules
import com.blueway.app.data.ReportStatus
import com.blueway.app.data.ReportType
import com.blueway.app.data.RouteMode
import com.blueway.app.data.RouteResult
import com.blueway.app.data.SavedRoute
import com.blueway.app.data.SharedReports
import com.blueway.app.data.SharedReportsException
import com.blueway.app.data.Zone
import com.blueway.app.data.normalizeText
import com.blueway.app.engine.RouteEngine
import com.blueway.app.engine.metersBetween
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

enum class Tab(val label: String) { HOME("Inicio"), ROUTES("Rutas"), MAPS("Mapas"), FAVORITES("Favoritos"), PROFILE("Perfil") }

sealed interface Screen {
    data object Onboarding : Screen
    data object Main : Screen
    data class Search(val forOrigin: Boolean, val voice: Boolean = false) : Screen
    data object Filters : Screen
    data object Alerts : Screen
    data object Legend : Screen
    data class ZoneDetail(val zone: Zone) : Screen
    data object Navigation : Screen
    data object NotificationSettings : Screen
    data object Help : Screen
    data object About : Screen
}

data class MapLayers(
    val noise: Boolean = true,
    val sensors: Boolean = true,
    val reports: Boolean = true,
    val green: Boolean = true,
)

sealed interface CameraCommand {
    data class Center(val point: LatLon, val zoom: Double? = null) : CameraCommand
    data class Fit(val points: List<LatLon>) : CameraCommand
    data object FollowReset : CameraCommand
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = LocalStore(app)

    var city by mutableStateOf<CityData?>(null); private set
    var loadError by mutableStateOf<String?>(null); private set
    var settings by mutableStateOf(store.loadSettings()); private set
    val backStack = mutableStateListOf<Screen>().apply {
        add(if (settings.onboarded) Screen.Main else Screen.Onboarding)
    }
    var tab by mutableStateOf(Tab.HOME)

    // Ubicación
    var userLocation by mutableStateOf<LatLon?>(null); private set
    var hasLocationPermission by mutableStateOf(false)

    // Planificación de ruta
    var destination by mutableStateOf<Place?>(null); private set
    var customOrigin by mutableStateOf<Place?>(null); private set
    var routes by mutableStateOf<List<RouteResult>>(emptyList()); private set
    var selectedMode by mutableStateOf(RouteMode.CALM); private set
    var computing by mutableStateOf(false); private set
    var routeError by mutableStateOf<String?>(null); private set
    /** Salida usada para calcular; explica en pantalla si es una salida de ejemplo. */
    var effectiveOrigin by mutableStateOf<Place?>(null); private set
    private var routeJob: Job? = null

    // Avisos
    val reports = mutableStateListOf<Report>()
    private val deviceId = store.deviceId()
    var lastSync by mutableStateOf<Long?>(null); private set
    var syncFailed by mutableStateOf(false); private set
    private var syncing = false
    private var foreground = true
    private val votes = store.loadVotes().toMutableSet()
    var selectedReport by mutableStateOf<Report?>(null)
    var reportDraftType by mutableStateOf<ReportType?>(null)
    var reportSheetOpen by mutableStateOf(false)
    /** Punto del mapa elegido por la persona con una pulsación larga. */
    var pressedPoint by mutableStateOf<LatLon?>(null)
    var pickingReportLocation by mutableStateOf(false)
    var mapCenter by mutableStateOf<LatLon?>(null)

    var reportDraftPoint by mutableStateOf<LatLon?>(null)

    var layers by mutableStateOf(MapLayers())
    var layersSheetOpen by mutableStateOf(false)
    /** Hora elegida en la pestaña Mapas para ver el ruido estimado; null es la hora actual. */
    var previewHour by mutableStateOf<Int?>(null)
    var message by mutableStateOf<String?>(null)
    val cameraCommands = Channel<CameraCommand>(Channel.CONFLATED)
    val cameraFlow = cameraCommands.receiveAsFlow()

    // Favoritos y recientes (solo en el teléfono)
    val favorites = mutableStateListOf<Place>().apply { addAll(store.loadPlaces("favorites")) }
    val recents = mutableStateListOf<Place>().apply { addAll(store.loadPlaces("recents")) }
    val savedRoutes = mutableStateListOf<SavedRoute>().apply { addAll(store.loadRoutes()) }

    // Navegación
    var navRoute by mutableStateOf<RouteResult?>(null); private set
    var navProgress by mutableStateOf(0.0); private set
    var navPosition by mutableStateOf<LatLon?>(null); private set
    var navSimulated by mutableStateOf(false); private set
    var navArrived by mutableStateOf(false); private set
    var navBanner by mutableStateOf<Report?>(null); private set
    var navOffRoute by mutableStateOf(false); private set
    private var navJob: Job? = null
    private var lastSpokenStep = -1
    private val notifiedReports = HashSet<String>()
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    val currentHour: Int get() = city?.noise?.currentHour() ?: 12

    init {
        // Los avisos de ejemplo solo tienen sentido si no hay servidor compartido.
        if (!SharedReports.enabled) reports.addAll(ReportRules.samples())
        reports.addAll(store.loadReports())
        if (SharedReports.enabled) {
            viewModelScope.launch {
                while (true) {
                    if (foreground) syncReports()
                    delay(SYNC_INTERVAL_MS)
                }
            }
        }
        viewModelScope.launch {
            try {
                city = withContext(Dispatchers.Default) { CityLoader.load(getApplication()) }
            } catch (e: Exception) {
                loadError = "No se han podido cargar los datos de Málaga: ${e.message}"
            }
        }
    }

    // --- Navegación entre pantallas ---
    fun navigate(screen: Screen) {
        backStack.add(screen)
    }

    fun back(): Boolean {
        if (backStack.size <= 1) return false
        val leavingNavigation = backStack.last() == Screen.Navigation
        if (leavingNavigation) stopNavigation()
        backStack.removeAt(backStack.lastIndex)
        // La posición habrá cambiado durante el trayecto: se vuelve a calcular y a encuadrar.
        if (leavingNavigation) recompute(fit = true)
        return true
    }

    fun finishOnboarding() {
        updateSettings { copy(onboarded = true) }
        backStack.clear()
        backStack.add(Screen.Main)
    }

    fun updateSettings(change: AppSettings.() -> AppSettings) {
        settings = settings.change()
        store.saveSettings(settings)
    }

    fun applyFilters(f: com.blueway.app.data.RouteFilters) {
        updateSettings { copy(filters = f) }
        recompute()
    }

    // --- Ubicación ---
    private var locationListener: LocationListener? = null

    @SuppressLint("MissingPermission")
    fun startLocation() {
        if (locationListener != null) return
        val lm = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        hasLocationPermission = true
        val listener = LocationListener { loc: Location -> onLocation(LatLon(loc.latitude, loc.longitude)) }
        locationListener = listener
        for (provider in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)) {
            if (runCatching { lm.isProviderEnabled(provider) }.getOrDefault(false)) {
                runCatching {
                    lm.getLastKnownLocation(provider)?.let { onLocation(LatLon(it.latitude, it.longitude)) }
                    lm.requestLocationUpdates(provider, 2000L, 3f, listener, Looper.getMainLooper())
                }
            }
        }
    }

    private fun onLocation(p: LatLon) {
        val first = userLocation == null
        userLocation = p
        if (first && destination != null && customOrigin == null) recompute()
        if (navRoute != null && !navSimulated) updateNavigation(p)
    }

    fun userInCoverage(): Boolean {
        val c = city ?: return false
        val u = userLocation ?: return false
        return c.graph.contains(u)
    }

    // --- Rutas ---
    private fun demoOrigin() = Place("Plaza de la Marina", "Salida de ejemplo", LatLon(36.7189, -4.4197))

    private fun resolveOrigin(): Place {
        customOrigin?.let { return it }
        if (userInCoverage()) return Place("Mi ubicación", "Según tu teléfono", userLocation!!)
        return demoOrigin()
    }

    fun chooseDestination(place: Place) {
        val c = city
        if (c != null && !c.graph.contains(place.point)) {
            message = "BlueWay solo cubre por ahora Málaga capital. ${place.name} queda fuera de la zona con datos."
            return
        }
        destination = place
        recents.removeAll { it.name == place.name }
        recents.add(0, place)
        while (recents.size > 6) recents.removeAt(recents.lastIndex)
        store.savePlaces("recents", recents)
        recompute(fit = true)
    }

    fun setOrigin(place: Place?) {
        val c = city
        if (place != null && c != null && !c.graph.contains(place.point)) {
            message = "Esa salida queda fuera de la zona con datos de Málaga."
            return
        }
        customOrigin = place
        recompute(fit = true)
    }

    fun clearRoute() {
        routeJob?.cancel()
        computing = false
        destination = null
        routes = emptyList()
        routeError = null
        effectiveOrigin = null
    }

    fun clearRecents() {
        recents.clear()
        store.savePlaces("recents", recents)
    }

    fun selectMode(mode: RouteMode) {
        selectedMode = mode
        selectedRoute()?.let { cameraCommands.trySend(CameraCommand.Fit(it.points)) }
    }

    fun selectedRoute(): RouteResult? = routes.firstOrNull { it.mode == selectedMode }

    fun recompute(fit: Boolean = false) {
        val dest = destination ?: return
        val c = city ?: return
        val origin = resolveOrigin()
        effectiveOrigin = origin
        computing = true
        routeError = null
        routeJob?.cancel()
        routeJob = viewModelScope.launch {
            val active = reports.toList()
            val filters = settings.filters
            val result = withContext(Dispatchers.Default) {
                c.engine.compute(origin.point, dest.point, filters, active, ReportRules.SENSORS)
            }
            computing = false
            if (result == null) {
                routes = emptyList()
                routeError = "No hemos encontrado un camino a pie con estas preferencias. Prueba a desactivar algún filtro."
            } else {
                routes = result
                if (fit) selectedRoute()?.let { cameraCommands.trySend(CameraCommand.Fit(it.points)) }
            }
        }
    }

    fun openSavedRoute(saved: SavedRoute) {
        customOrigin = saved.origin.takeIf { it.name != "Mi ubicación" }
        selectedMode = saved.mode
        tab = Tab.HOME
        chooseDestination(saved.destination)
    }

    fun saveCurrentRoute() {
        val dest = destination ?: return
        val origin = effectiveOrigin ?: return
        val name = "${origin.name} → ${dest.name}"
        if (savedRoutes.any { it.name == name && it.mode == selectedMode }) {
            message = "Esta ruta ya está guardada."
            return
        }
        savedRoutes.add(0, SavedRoute(UUID.randomUUID().toString(), name, origin, dest, selectedMode))
        store.saveRoutes(savedRoutes)
        message = "Ruta guardada."
    }

    fun deleteSavedRoute(saved: SavedRoute) {
        savedRoutes.remove(saved)
        store.saveRoutes(savedRoutes)
    }

    // --- Favoritos ---
    fun isFavorite(name: String) = favorites.any { it.name == name }

    fun toggleFavorite(place: Place) {
        val existing = favorites.firstOrNull { it.name == place.name }
        if (existing != null) favorites.remove(existing) else favorites.add(0, place)
        store.savePlaces("favorites", favorites)
    }

    // --- Búsqueda ---
    fun searchLocal(query: String): List<Place> {
        val c = city ?: return emptyList()
        val q = normalizeText(query)
        if (q.length < 2) return emptyList()
        val places = c.places.filter { normalizeText(it.name).contains(q) }
            .sortedBy { normalizeText(it.name).indexOf(q) }.take(8)
        val streets = c.streets.asSequence().filter { it.first.contains(q) }
            .sortedBy { it.first.indexOf(q) }.take(8).map { it.second }.toList()
        return (places + streets).distinctBy { it.name }.take(12)
    }

    suspend fun searchOnline(query: String): List<Place> {
        val c = city ?: return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching { PlaceSearchClient.search(query, c.graph) }.getOrDefault(emptyList())
        }.filter { c.graph.contains(it.point) }
    }

    fun placesIn(category: PlaceCategory): List<Place> =
        city?.places?.filter { it.category == category }?.sortedBy { it.name } ?: emptyList()

    fun placeFromMap(p: LatLon): Place {
        val c = city
        val street = c?.graph?.nearestEdge(p)?.let { c.graph.names[c.graph.edgeName[it]] }.orEmpty()
        return Place(street.ifEmpty { "Punto del mapa" }, "Elegido en el mapa", p)
    }

    // --- Zonas ---
    fun zoneAt(point: LatLon, title: String? = null): Zone? {
        val c = city ?: return null
        val edge = c.graph.nearestEdge(point, 300.0) ?: return null
        val hour = c.noise.currentHour()
        val effects = c.noise.reportEffects(reports)
        val db = c.noise.edgeDb(edge, hour, effects)
        val street = c.graph.names[c.graph.edgeName[edge]]
        val now = System.currentTimeMillis()
        val sensor = ReportRules.SENSORS.firstOrNull { metersBetween(it.point, point) < 150 }
        val nearReports = reports.filter { it.isActive(now) && metersBetween(it.point, point) < 150 }
        val curve = c.noise.dayCurve(edge)
        val quiet = quietHours(curve)
        val source = when {
            sensor != null -> DataSource.SIMULATED
            nearReports.isNotEmpty() -> DataSource.REPORTED
            else -> DataSource.ESTIMATED
        }
        val value = if (sensor != null) c.noise.simulatedSensorDb(sensor.point, hour, now, effects) ?: db else db
        val level = NoiseLevel.of(value)
        return Zone(
            title = title ?: when (level) {
                NoiseLevel.LOW -> "Zona tranquila"
                NoiseLevel.MODERATE -> "Ruido moderado"
                NoiseLevel.HIGH -> "Zona con ruido alto"
                NoiseLevel.VERY_HIGH -> "Zona de alto ruido"
            },
            subtitle = street.ifEmpty { "Málaga" },
            point = point,
            db = value,
            source = source,
            crowdPct = c.noise.crowdPct(edge, hour),
            benches = c.benches.within(point, 150.0).size,
            cafes = c.cafes.within(point, 200.0).size,
            trees = c.trees.within(point, 120.0).size,
            inGreen = (c.graph.edgeFlags[edge].toInt() and com.blueway.app.engine.FLAG_GREEN) != 0,
            curve = curve,
            quietHours = quiet,
            sensorNote = sensor?.let { "Sensor simulado: ${it.name}" },
            reports = nearReports,
        )
    }

    /** Franja diurna continua (7:00-22:00) con el ruido estimado más bajo. */
    private fun quietHours(curve: List<Int>): String {
        val day = 7..21
        val min = day.minOf { curve[it] }
        val threshold = min + 2
        var bestStart = day.first
        var bestLen = 0
        var start = -1
        for (h in day) {
            if (curve[h] <= threshold) {
                if (start == -1) start = h
                if (h - start + 1 > bestLen) {
                    bestStart = start
                    bestLen = h - start + 1
                }
            } else {
                start = -1
            }
        }
        return "%d:00 - %d:00".format(bestStart, bestStart + bestLen)
    }

    fun openZone(point: LatLon, title: String? = null) {
        val zone = zoneAt(point, title)
        if (zone == null) {
            message = "Sin datos: BlueWay no tiene información de este punto."
        } else {
            navigate(Screen.ZoneDetail(zone))
        }
    }

    // --- Avisos ---
    fun setForeground(value: Boolean) {
        val returning = value && !foreground
        foreground = value
        if (returning && SharedReports.enabled) viewModelScope.launch { syncReports() }
    }

    /** Sube los avisos pendientes y descarga los de todo el mundo. Sin conexión se conserva lo último conocido. */
    suspend fun syncReports() {
        if (syncing || !SharedReports.enabled) return
        syncing = true
        try {
            val now = System.currentTimeMillis()
            val pending = reports.filter { !it.synced && it.expiresAt > now }
            val rejected = HashSet<String>()
            val server = withContext(Dispatchers.IO) {
                for (report in pending) {
                    try {
                        SharedReports.create(deviceId, report)
                    } catch (e: SharedReportsException) {
                        // El servidor no lo acepta (fuera de zona, demasiados avisos): no se reintenta sin fin,
                        // salvo el límite temporal, que se resolverá solo.
                        if (e.code != "rate_limited" && e.code != "busy") rejected.add(report.id)
                    }
                }
                SharedReports.list(deviceId)
            }
            val before = activeReports().map { it.id to it.status() }.toSet()
            if (rejected.isNotEmpty()) message = "Un aviso no se ha podido publicar porque el servidor no lo ha aceptado."
            // Un pendiente aceptado ya viene del servidor con su id definitivo.
            val stillPending = pending.filter { p ->
                p.id !in rejected && server.none { s -> s.type == p.type && metersBetween(s.point, p.point) <= ReportRules.DUPLICATE_RADIUS }
            }
            reports.clear()
            reports.addAll(stillPending + server)
            selectedReport = selectedReport?.let { old -> reports.firstOrNull { it.id == old.id } }
            store.saveReports(reports)
            lastSync = System.currentTimeMillis()
            syncFailed = false
            val after = activeReports().map { it.id to it.status() }.toSet()
            if (before != after && navRoute == null) recompute()
        } catch (_: Exception) {
            syncFailed = true
        } finally {
            syncing = false
        }
    }

    fun activeReports(): List<Report> {
        val now = System.currentTimeMillis()
        return reports.filter { it.isActive(now) }
    }

    fun openReportSheet(point: LatLon? = null) {
        reportDraftType = null
        reportDraftPoint = point ?: userLocation?.takeIf { userInCoverage() }
        reportSheetOpen = true
    }

    fun closeReportSheet() {
        reportSheetOpen = false
        reportDraftType = null
    }

    private fun showMap() {
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        if (tab != Tab.HOME && tab != Tab.MAPS) tab = Tab.HOME
    }

    fun startPickingLocation() {
        reportSheetOpen = false
        showMap()
        pickingReportLocation = true
    }

    fun showOnMap(point: LatLon) {
        selectedReport = null
        showMap()
        cameraCommands.trySend(CameraCommand.Center(point, 16.5))
    }

    fun cancelPickingLocation() {
        pickingReportLocation = false
        reportSheetOpen = true
    }

    fun confirmPickedLocation() {
        val center = mapCenter
        val c = city
        if (center == null || c == null || !c.graph.contains(center)) {
            message = "Ese punto queda fuera de la zona con datos de Málaga."
            return
        }
        reportDraftPoint = center
        pickingReportLocation = false
        reportSheetOpen = true
    }

    fun submitReport(type: ReportType, point: LatLon, description: String) {
        if (SharedReports.enabled) {
            // Un sensor simulado nunca confirma un aviso que van a ver otras personas.
            val duplicate = ReportRules.findDuplicate(reports, type, point)
            if (duplicate?.mine == true) {
                message = "Ya enviaste un aviso igual cerca de aquí."
            } else {
                reports.add(0, ReportRules.create(type, point, description, false).copy(synced = false))
                store.saveReports(reports)
                message = if (duplicate != null) {
                    "Ya había un aviso igual cerca. Lo reforzamos con el tuyo."
                } else {
                    "Aviso enviado de forma anónima. Queda pendiente de confirmar."
                }
                viewModelScope.launch { syncReports() }
            }
            reportSheetOpen = false
            reportDraftType = null
            pickingReportLocation = false
            recompute()
            return
        }
        val c = city
        val duplicate = ReportRules.findDuplicate(reports, type, point)
        if (duplicate != null) {
            confirmInternal(duplicate, true)
            message = "Ya había un aviso igual cerca. Lo hemos reforzado con el tuyo."
        } else {
            val isNoiseType = type in setOf(ReportType.NOISE_EXTREME, ReportType.NOISE_HIGH)
            val hour = currentHour
            val corroborated = isNoiseType && c != null && ReportRules.SENSORS.any { s ->
                metersBetween(s.point, point) < 200 &&
                    (c.noise.simulatedSensorDb(s.point, hour, System.currentTimeMillis(), emptyMap()) ?: 0) >= 66
            }
            reports.add(0, ReportRules.create(type, point, description, corroborated))
            store.saveReports(reports)
            message = if (corroborated) {
                "Aviso publicado y confirmado por un sensor simulado cercano."
            } else {
                "Aviso guardado en este teléfono. Queda pendiente de confirmar."
            }
        }
        reportSheetOpen = false
        reportDraftType = null
        pickingReportLocation = false
        recompute()
    }

    private fun confirmInternal(report: Report, positive: Boolean) {
        val i = reports.indexOfFirst { it.id == report.id }
        if (i < 0) return
        val r = reports[i]
        val updated = if (positive) {
            // Una confirmación también alarga un poco la vida del aviso, sin superar la de su tipo.
            val now = System.currentTimeMillis()
            r.copy(
                confirmations = r.confirmations + 1,
                expiresAt = maxOf(r.expiresAt, minOf(now + r.type.expiresHours * 3_600_000L, r.expiresAt + 1_800_000L)),
            )
        } else {
            r.copy(rejections = r.rejections + 2)
        }
        reports[i] = updated
        if (selectedReport?.id == r.id) selectedReport = updated
        store.saveReports(reports)
    }

    fun canVote(report: Report) = !report.mine && !report.voted && report.synced && report.id !in votes

    fun voteReport(report: Report, stillThere: Boolean) {
        if (!canVote(report)) return
        if (SharedReports.enabled) {
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) { SharedReports.vote(deviceId, report.id, stillThere) }
                    message = if (stillThere) "Gracias. Hemos sumado tu confirmación." else "Gracias. Hemos sumado tu valoración."
                    syncReports()
                } catch (e: SharedReportsException) {
                    message = if (e.code == "already_voted") "Ya habías valorado este aviso." else "Este aviso ya no está disponible."
                    syncReports()
                } catch (_: Exception) {
                    message = "Sin conexión. Inténtalo de nuevo más tarde."
                }
            }
            return
        }
        votes.add(report.id)
        store.saveVotes(votes)
        confirmInternal(report, stillThere)
        message = if (stillThere) "Gracias. Hemos confirmado el aviso." else "Gracias. Hemos marcado el aviso como descartado."
        recompute()
    }

    fun removeReport(report: Report) {
        if (SharedReports.enabled && report.synced) {
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) { SharedReports.delete(deviceId, report.id) }
                    selectedReport = null
                    syncReports()
                } catch (_: Exception) {
                    message = "No se ha podido eliminar. Comprueba la conexión."
                }
            }
            return
        }
        reports.removeAll { it.id == report.id }
        selectedReport = null
        store.saveReports(reports)
        recompute()
    }

    fun reportLabel(status: ReportStatus) = status.label

    /** Avisos a lo largo de la ruta elegida o, sin ruta, los más cercanos. */
    fun alertsWithDistance(): List<Pair<Report, Int?>> {
        val route = selectedRoute()
        val base = userLocation?.takeIf { userInCoverage() } ?: route?.points?.firstOrNull()
        val all = reports.sortedBy { !it.isActive() }
        return all.map { it to base?.let { b -> metersBetween(b, it.point).toInt() } }
    }

    fun isReportOnRoute(report: Report): Boolean {
        val route = selectedRoute() ?: return false
        return route.points.any { metersBetween(it, report.point) < 70 }
    }

    // --- Navegación guiada ---
    fun startNavigation() {
        val route = selectedRoute() ?: return
        navRoute = route
        navProgress = 0.0
        navArrived = false
        navBanner = null
        navOffRoute = false
        lastSpokenStep = -1
        notifiedReports.clear()
        // Se simula el trayecto si la salida no es tu ubicación real.
        navSimulated = !(customOrigin == null && userInCoverage())
        navPosition = route.points.first()
        if (settings.voiceGuidance) ensureTts()
        navigate(Screen.Navigation)
        navJob?.cancel()
        if (navSimulated) {
            navJob = viewModelScope.launch {
                while (navRoute != null && !navArrived) {
                    delay(500)
                    val r = navRoute ?: break
                    val nextProgress = navProgress + RouteEngine.WALK_SPEED * 0.5 * SIMULATION_SPEED
                    applyProgress(r, nextProgress.coerceAtMost(r.meters), RouteEngine.pointAlong(r, nextProgress))
                }
            }
        } else {
            userLocation?.let { updateNavigation(it) }
        }
        speakCurrent()
    }

    fun stopNavigation() {
        navJob?.cancel()
        navJob = null
        navRoute = null
        navPosition = null
        navBanner = null
        tts?.stop()
    }

    private fun updateNavigation(p: LatLon) {
        val r = navRoute ?: return
        val (along, off) = RouteEngine.projectOnRoute(r, p, navProgress)
        navOffRoute = off > OFF_ROUTE_METERS
        if (navOffRoute) {
            navPosition = p
            rerouteFrom(p)
            return
        }
        applyProgress(r, maxOf(navProgress, along), p)
    }

    private var rerouting = false

    private fun rerouteFrom(p: LatLon) {
        val c = city ?: return
        val dest = destination ?: return
        if (rerouting) return
        rerouting = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                c.engine.compute(p, dest.point, settings.filters, reports.toList(), ReportRules.SENSORS)
            }
            rerouting = false
            val updated = result?.firstOrNull { it.mode == selectedMode } ?: return@launch
            routes = result
            navRoute = updated
            navProgress = 0.0
            navOffRoute = false
            lastSpokenStep = -1
            speakCurrent()
        }
    }

    private fun applyProgress(route: RouteResult, progress: Double, position: LatLon) {
        navProgress = progress
        navPosition = position
        if (route.meters - progress < ARRIVAL_METERS) {
            navArrived = true
            navJob?.cancel()
            if (settings.voiceGuidance) speak("Has llegado a tu destino.")
            return
        }
        speakCurrent()
        checkReportsAhead(position)
    }

    private fun checkReportsAhead(position: LatLon) {
        if (!settings.notifyReports) return
        val now = System.currentTimeMillis()
        val near = reports.firstOrNull {
            it.isActive(now) && it.type in settings.notifyTypes && it.id !in notifiedReports &&
                metersBetween(it.point, position) < 150
        } ?: return
        notifiedReports.add(near.id)
        navBanner = near
        viewModelScope.launch {
            delay(9000)
            if (navBanner?.id == near.id) navBanner = null
        }
    }

    fun dismissBanner() {
        navBanner = null
    }

    fun currentStepIndex(): Int {
        val r = navRoute ?: return 0
        val index = r.steps.indexOfLast { r.cumulative[it.startIndex] <= navProgress + 1 }
        return index.coerceAtLeast(0)
    }

    /** Cerca de un giro, la indicación principal anuncia ya la maniobra siguiente. */
    fun displayedStepIndex(): Int {
        val r = navRoute ?: return 0
        val current = currentStepIndex()
        return if (current < r.steps.lastIndex && metersToNextStep() <= ANNOUNCE_METERS) current + 1 else current
    }

    fun metersToNextStep(): Double {
        val r = navRoute ?: return 0.0
        val next = r.steps.getOrNull(currentStepIndex() + 1) ?: return 0.0
        return (r.cumulative[next.startIndex] - navProgress).coerceAtLeast(0.0)
    }

    fun toggleVoice() {
        val enabled = !settings.voiceGuidance
        updateSettings { copy(voiceGuidance = enabled) }
        if (enabled) {
            ensureTts()
            lastSpokenStep = -1
            speakCurrent()
        } else {
            tts?.stop()
        }
    }

    private fun speakCurrent() {
        if (!settings.voiceGuidance) return
        val r = navRoute ?: return
        val i = displayedStepIndex()
        if (i == lastSpokenStep) return
        lastSpokenStep = i
        speak(r.steps[i].text)
    }

    private fun ensureTts() {
        if (tts != null) return
        tts = TextToSpeech(getApplication()) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) tts?.language = Locale.forLanguageTag("es-ES")
            lastSpokenStep = -1
            speakCurrent()
        }
    }

    private fun speak(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "blueway")
    }

    override fun onCleared() {
        navJob?.cancel()
        tts?.shutdown()
        locationListener?.let {
            (getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(it)
        }
        super.onCleared()
    }

    companion object {
        const val SIMULATION_SPEED = 6.0
        private const val OFF_ROUTE_METERS = 45.0
        private const val ARRIVAL_METERS = 15.0
        private const val ANNOUNCE_METERS = 60.0
        private const val SYNC_INTERVAL_MS = 60_000L
    }
}
