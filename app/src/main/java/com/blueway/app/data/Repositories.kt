package com.blueway.app.data

import android.content.Context
import com.blueway.app.engine.CityGraph
import com.blueway.app.engine.NoiseModel
import com.blueway.app.engine.PointIndex
import com.blueway.app.engine.RouteEngine
import com.blueway.app.engine.metersBetween
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.UUID
import kotlin.math.roundToLong

/** Datos locales de la ciudad, cargados una vez al abrir la app. */
class CityData(
    val graph: CityGraph,
    val noise: NoiseModel,
    val engine: RouteEngine,
    val benches: PointIndex,
    val cafes: PointIndex,
    val trees: PointIndex,
    val places: List<Place>,
    val parks: List<Place>,
    /** Nombre normalizado de calle -> lugar representativo. */
    val streets: List<Pair<String, Place>>,
)

fun normalizeText(text: String): String =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").trim()

object CityLoader {
    fun load(context: Context): CityData {
        val assets = context.assets
        val graph = assets.open("city/graph.bwg").use { CityGraph.load(it) }
        val pois = JSONObject(assets.open("city/pois.json").bufferedReader().use { it.readText() })
        val benches = PointIndex(pois.getJSONArray("benches").toIntArray())
        val cafes = PointIndex(pois.getJSONArray("cafes").toIntArray())
        val trees = PointIndex(pois.getJSONArray("trees").toIntArray())
        val categories = mapOf(
            "education" to PlaceCategory.EDUCATION,
            "health" to PlaceCategory.HEALTH,
            "transport" to PlaceCategory.TRANSPORT,
            "culture" to PlaceCategory.CULTURE,
            "calm" to PlaceCategory.CALM,
        )
        val places = ArrayList<Place>()
        val rawPlaces = pois.getJSONArray("places")
        for (i in 0 until rawPlaces.length()) {
            val p = rawPlaces.getJSONObject(i)
            places.add(
                Place(
                    p.getString("n"), p.getString("d"), LatLon(p.getDouble("la"), p.getDouble("lo")),
                    categories[p.getString("c")] ?: PlaceCategory.OTHER,
                ),
            )
        }
        val parks = ArrayList<Place>()
        val rawParks = pois.getJSONArray("parks")
        for (i in 0 until rawParks.length()) {
            val p = rawParks.getJSONObject(i)
            parks.add(Place(p.getString("n"), "Zona verde", LatLon(p.getDouble("la"), p.getDouble("lo")), PlaceCategory.PARKS))
        }
        places.addAll(parks)

        val seen = HashSet<String>()
        val streets = ArrayList<Pair<String, Place>>()
        for (e in 0 until graph.edgeCount) {
            val name = graph.names[graph.edgeName[e]]
            if (name.isNotEmpty() && seen.add(name)) {
                streets.add(normalizeText(name) to Place(name, "Calle de Málaga", graph.edgeMidpoint(e)))
            }
        }
        val noise = NoiseModel(graph)
        return CityData(graph, noise, RouteEngine(graph, noise, benches, trees), benches, cafes, trees, places, parks, streets)
    }

    private fun JSONArray.toIntArray() = IntArray(length()) { getInt(it) }
}

/** Búsqueda por internet (Photon, datos de OpenStreetMap). Solo se envía el texto y la zona de Málaga. */
object PlaceSearchClient {
    fun search(query: String, city: CityGraph): List<Place> {
        val q = URLEncoder.encode(query, "UTF-8")
        val bbox = "${city.west},${city.south},${city.east},${city.north}"
        val conn = URL("https://photon.komoot.io/api/?q=$q&limit=6&lang=default&bbox=$bbox").openConnection() as HttpURLConnection
        conn.connectTimeout = 6000
        conn.readTimeout = 6000
        conn.setRequestProperty("User-Agent", "BlueWay-Android/0.1")
        return try {
            if (conn.responseCode != 200) return emptyList()
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val features = JSONObject(body).getJSONArray("features")
            (0 until features.length()).mapNotNull { i ->
                val f = features.getJSONObject(i)
                val p = f.getJSONObject("properties")
                val c = f.getJSONObject("geometry").getJSONArray("coordinates")
                val name = p.optString("name").ifEmpty { p.optString("street") }
                if (name.isEmpty()) return@mapNotNull null
                val detail = listOf(p.optString("street"), p.optString("housenumber"), p.optString("district"))
                    .filter { it.isNotEmpty() && it != name }.joinToString(", ").ifEmpty { "Málaga" }
                Place(name, detail, LatLon(c.getDouble(1), c.getDouble(0)))
            }
        } finally {
            conn.disconnect()
        }
    }
}

/** Preferencias, lugares y avisos guardados solo en el teléfono. No hay cuenta ni se guardan recorridos GPS. */
class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("blueway", Context.MODE_PRIVATE)

    fun loadSettings(): AppSettings {
        val d = AppSettings()
        val types = prefs.getStringSet("notifyTypes", null)
            ?.mapNotNull { n -> ReportType.entries.firstOrNull { it.name == n } }?.toSet() ?: d.notifyTypes
        return AppSettings(
            onboarded = prefs.getBoolean("onboarded", false),
            name = prefs.getString("name", d.name) ?: d.name,
            highContrast = prefs.getBoolean("highContrast", false),
            reduceMotion = prefs.getBoolean("reduceMotion", false),
            voiceGuidance = prefs.getBoolean("voice", false),
            notifyReports = prefs.getBoolean("notifyReports", true),
            notifyTypes = types,
            filters = RouteFilters(
                avoidHighNoise = prefs.getBoolean("fNoise", true),
                preferGreen = prefs.getBoolean("fGreen", true),
                avoidCrowds = prefs.getBoolean("fCrowd", true),
                shortest = prefs.getBoolean("fShort", false),
                accessible = prefs.getBoolean("fAccess", true),
                sensitivity = prefs.getInt("fSens", 2),
            ),
        )
    }

    fun saveSettings(s: AppSettings) {
        prefs.edit()
            .putBoolean("onboarded", s.onboarded)
            .putString("name", s.name)
            .putBoolean("highContrast", s.highContrast)
            .putBoolean("reduceMotion", s.reduceMotion)
            .putBoolean("voice", s.voiceGuidance)
            .putBoolean("notifyReports", s.notifyReports)
            .putStringSet("notifyTypes", s.notifyTypes.map { it.name }.toSet())
            .putBoolean("fNoise", s.filters.avoidHighNoise)
            .putBoolean("fGreen", s.filters.preferGreen)
            .putBoolean("fCrowd", s.filters.avoidCrowds)
            .putBoolean("fShort", s.filters.shortest)
            .putBoolean("fAccess", s.filters.accessible)
            .putInt("fSens", s.filters.sensitivity)
            .apply()
    }

    fun loadPlaces(key: String): List<Place> = runCatching {
        val a = JSONArray(prefs.getString(key, "[]"))
        (0 until a.length()).map { placeFrom(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun savePlaces(key: String, places: List<Place>) {
        prefs.edit().putString(key, JSONArray(places.map { placeTo(it) }).toString()).apply()
    }

    fun loadRoutes(): List<SavedRoute> = runCatching {
        val a = JSONArray(prefs.getString("routes", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            SavedRoute(
                o.getString("id"), o.getString("name"),
                placeFrom(o.getJSONObject("o")), placeFrom(o.getJSONObject("d")),
                RouteMode.valueOf(o.getString("m")),
            )
        }
    }.getOrDefault(emptyList())

    fun saveRoutes(routes: List<SavedRoute>) {
        val a = JSONArray(
            routes.map {
                JSONObject().put("id", it.id).put("name", it.name).put("o", placeTo(it.origin))
                    .put("d", placeTo(it.destination)).put("m", it.mode.name)
            },
        )
        prefs.edit().putString("routes", a.toString()).apply()
    }

    fun loadReports(): List<Report> = runCatching {
        val a = JSONArray(prefs.getString("reports", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Report(
                o.getString("id"), ReportType.valueOf(o.getString("t")),
                LatLon(o.getDouble("la"), o.getDouble("lo")), o.getString("d"),
                o.getLong("c"), o.getLong("e"), o.getInt("y"), o.getInt("n"), false, o.getBoolean("m"),
                voted = o.optBoolean("v", false),
                synced = o.optBoolean("s", false),
            )
        }
    }.getOrDefault(emptyList())

    /** Solo se guardan avisos propios o tocados por la persona; los de ejemplo se crean al abrir. */
    fun saveReports(reports: List<Report>) {
        val a = JSONArray(
            reports.filter { !it.isSample }.map {
                JSONObject().put("id", it.id).put("t", it.type.name).put("la", it.point.lat).put("lo", it.point.lon)
                    .put("d", it.description).put("c", it.createdAt).put("e", it.expiresAt)
                    .put("y", it.confirmations).put("n", it.rejections).put("m", it.mine)
                    .put("v", it.voted).put("s", it.synced)
            },
        )
        prefs.edit().putString("reports", a.toString()).apply()
    }

    /** Identificador aleatorio de esta instalación. No está ligado a ninguna cuenta ni dato personal. */
    fun deviceId(): String = prefs.getString("device", null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString("device", it).apply()
    }

    fun loadVotes(): Set<String> = prefs.getStringSet("votes", emptySet()) ?: emptySet()
    fun saveVotes(v: Set<String>) = prefs.edit().putStringSet("votes", v).apply()

    private fun placeTo(p: Place) = JSONObject().put("n", p.name).put("d", p.detail)
        .put("la", p.point.lat).put("lo", p.point.lon).put("c", p.category.name)

    private fun placeFrom(o: JSONObject) = Place(
        o.getString("n"), o.getString("d"), LatLon(o.getDouble("la"), o.getDouble("lo")),
        runCatching { PlaceCategory.valueOf(o.getString("c")) }.getOrDefault(PlaceCategory.OTHER),
    )
}

/** Reglas de los avisos: anti-duplicados, caducidad y confirmación (RF5, RF6, RF7). */
object ReportRules {
    const val DUPLICATE_RADIUS = 120.0
    const val MAX_DESCRIPTION = 120

    /** Ubicación aproximada (unos 11 m) para no guardar la posición exacta de quien avisa. */
    fun approximate(p: LatLon) = LatLon(
        (p.lat * 1e4).roundToLong() / 1e4,
        (p.lon * 1e4).roundToLong() / 1e4,
    )

    fun create(type: ReportType, point: LatLon, description: String, sensorCorroborates: Boolean): Report {
        val now = System.currentTimeMillis()
        return Report(
            id = UUID.randomUUID().toString(),
            type = type,
            point = approximate(point),
            description = description.trim().take(MAX_DESCRIPTION),
            createdAt = now,
            expiresAt = now + type.expiresHours * 3_600_000L,
            confirmations = if (sensorCorroborates) 2 else 1,
            rejections = 0,
            isSample = false,
            mine = true,
        )
    }

    fun findDuplicate(existing: List<Report>, type: ReportType, point: LatLon): Report? {
        val now = System.currentTimeMillis()
        return existing.firstOrNull {
            it.type == type && it.isActive(now) && metersBetween(it.point, point) <= DUPLICATE_RADIUS
        }
    }

    /** Avisos de ejemplo, con hora relativa a la apertura de la app. */
    fun samples(): List<Report> {
        val now = System.currentTimeMillis()
        fun sample(id: String, type: ReportType, lat: Double, lon: Double, text: String, minutes: Int, confirms: Int) = Report(
            "sample-$id", type, LatLon(lat, lon), text, now - minutes * 60_000L,
            now - minutes * 60_000L + type.expiresHours * 3_600_000L, confirms, 0, true, false,
        )
        return listOf(
            sample("works", ReportType.WORKS, 36.7192, -4.4213, "Aumento temporal del nivel de ruido en Calle Larios.", 2, 3),
            sample("event", ReportType.EVENT, 36.7211, -4.4220, "Concierto en Plaza de la Constitución.", 5, 2),
            sample("traffic", ReportType.TRAFFIC, 36.7174, -4.4262, "Más tráfico de lo habitual en la Alameda Principal.", 10, 1),
            sample("noise", ReportType.NOISE_HIGH, 36.7236, -4.4172, "Mucho ruido en Plaza de la Merced.", 12, 1),
        )
    }

    val SENSORS = listOf(
        Sensor("s1", "Plaza de la Merced", LatLon(36.7235, -4.4176)),
        Sensor("s2", "Calle Larios", LatLon(36.7195, -4.4213)),
        Sensor("s3", "Alameda Principal", LatLon(36.7173, -4.4240)),
        Sensor("s4", "Muelle Uno", LatLon(36.7160, -4.4138)),
        Sensor("s5", "Parque de Málaga", LatLon(36.7185, -4.4135)),
        Sensor("s6", "Avenida de Andalucía", LatLon(36.7148, -4.4330)),
        Sensor("s7", "La Malagueta", LatLon(36.7190, -4.4085)),
        Sensor("s8", "Calle Alcazabilla", LatLon(36.7209, -4.4170)),
    )
}
