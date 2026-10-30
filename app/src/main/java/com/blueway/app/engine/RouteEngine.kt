package com.blueway.app.engine

import com.blueway.app.data.LatLon
import com.blueway.app.data.NoiseLevel
import com.blueway.app.data.Report
import com.blueway.app.data.RouteFilters
import com.blueway.app.data.RouteMode
import com.blueway.app.data.RouteResult
import com.blueway.app.data.RouteStep
import com.blueway.app.data.Sensor
import com.blueway.app.data.StepKind
import java.util.BitSet
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** Índice simple de puntos para contar bancos, cafeterías o arbolado cerca de una posición. */
class PointIndex(flat: IntArray) {
    private val cells = HashMap<Long, MutableList<LatLon>>()

    init {
        var i = 0
        while (i + 1 < flat.size) {
            val p = LatLon(flat[i] / 1e5, flat[i + 1] / 1e5)
            cells.getOrPut(key(p)) { ArrayList(2) }.add(p)
            i += 2
        }
    }

    fun within(p: LatLon, meters: Double): List<LatLon> {
        val out = ArrayList<LatLon>()
        val cx = cx(p)
        val cy = cy(p)
        val reach = (meters / CELL).toInt() + 1
        for (i in -reach..reach) for (j in -reach..reach) {
            cells[pack(cx + i, cy + j)]?.forEach { if (metersBetween(p, it) <= meters) out.add(it) }
        }
        return out
    }

    private fun cx(p: LatLon) = Math.floor(p.lat * 110574.0 / CELL).toInt()
    private fun cy(p: LatLon) = Math.floor(p.lon * 89300.0 / CELL).toInt()
    private fun key(p: LatLon) = pack(cx(p), cy(p))
    private fun pack(x: Int, y: Int) = (x.toLong() shl 32) xor (y.toLong() and 0xffffffffL)

    private companion object {
        const val CELL = 50.0
    }
}

class RouteEngine(
    private val graph: CityGraph,
    private val noise: NoiseModel,
    private val benches: PointIndex,
    private val trees: PointIndex,
) {
    /** Calcula las tres rutas. Devuelve null si no hay camino peatonal entre los dos puntos. */
    fun compute(
        origin: LatLon,
        destination: LatLon,
        filters: RouteFilters,
        reports: List<Report>,
        sensors: List<Sensor>,
        hour: Int = noise.currentHour(),
    ): List<RouteResult>? {
        val start = graph.nearestNode(origin) ?: return null
        val goal = graph.nearestNode(destination) ?: return null
        val effects = noise.reportEffects(reports)

        val fastPath = search(start, goal, RouteMode.FAST, filters, hour, effects) ?: return null
        val calmPath = search(start, goal, RouteMode.CALM, filters, hour, effects) ?: fastPath
        val quietPath = search(start, goal, RouteMode.QUIETEST, filters, hour, effects) ?: calmPath

        val fast = build(RouteMode.FAST, fastPath, start, origin, destination, hour, effects, reports, sensors, null, false)
        val calm = build(RouteMode.CALM, calmPath, start, origin, destination, hour, effects, reports, sensors, fast.noiseLoad, calmPath == fastPath)
        val quiet = build(RouteMode.QUIETEST, quietPath, start, origin, destination, hour, effects, reports, sensors, fast.noiseLoad, quietPath == fastPath)
        return listOf(calm, fast, quiet)
    }

    /** Coste por metro de una arista según el modo de ruta y las preferencias. */
    private fun edgeCost(edge: Int, mode: RouteMode, filters: RouteFilters, hour: Int, effects: Map<Int, Int>): Double {
        val flags = graph.edgeFlags[edge].toInt()
        val length = graph.edgeLen[edge].toDouble()
        if (filters.accessible && (flags and FLAG_BAD_WHEELCHAIR) != 0) return Double.POSITIVE_INFINITY
        var factor = 1.0
        if (filters.accessible && (flags and FLAG_ROUGH) != 0) factor += 0.6
        if ((flags and FLAG_STEPS) != 0) factor += 0.8
        if (mode == RouteMode.FAST) return length * factor

        val base = if (mode == RouteMode.QUIETEST) 7.0 else 3.2
        val sensitivity = 0.6 + filters.sensitivity * 0.45
        val shortest = if (filters.shortest) 0.35 else 1.0
        val weight = base * sensitivity * shortest * (if (filters.avoidHighNoise) 1.0 else 0.4)

        val db = noise.edgeDb(edge, hour, effects)
        val excess = max(0.0, (db - 50) / 20.0)
        factor += weight * excess.pow(2.0)
        if (filters.avoidCrowds) factor += (noise.crowdPct(edge, hour) / 100.0) * 1.2 * shortest
        // Las zonas verdes abaratan el coste, sin dejar que sea menor que la distancia real.
        if (filters.preferGreen && (flags and FLAG_GREEN) != 0) factor = max(1.0, factor - 0.5 * shortest)
        return length * factor
    }

    /** A* con la distancia como cota inferior del coste, ya que ningún tramo cuesta menos que su longitud. */
    private fun search(
        start: Int,
        goal: Int,
        mode: RouteMode,
        filters: RouteFilters,
        hour: Int,
        effects: Map<Int, Int>,
    ): List<Int>? {
        if (start == goal) return emptyList()
        val n = graph.nodeCount
        val cost = DoubleArray(n) { Double.POSITIVE_INFINITY }
        val viaEdge = IntArray(n) { -1 }
        val closed = BitSet(n)
        val queue = java.util.PriorityQueue<DoubleArray>(1024, compareBy { it[0] })
        val goalPoint = graph.nodePoint(goal)
        cost[start] = 0.0
        queue.add(doubleArrayOf(metersBetween(graph.nodePoint(start), goalPoint), start.toDouble()))
        while (queue.isNotEmpty()) {
            val node = queue.poll()!![1].toInt()
            if (closed[node]) continue
            closed[node] = true
            if (node == goal) break
            for (i in graph.adjStart[node] until graph.adjStart[node + 1]) {
                val edge = graph.adjEdge[i]
                val next = if (graph.edgeFrom[edge] == node) graph.edgeTo[edge] else graph.edgeFrom[edge]
                if (closed[next]) continue
                val step = edgeCost(edge, mode, filters, hour, effects)
                if (step == Double.POSITIVE_INFINITY) continue
                val total = cost[node] + step
                if (total < cost[next]) {
                    cost[next] = total
                    viaEdge[next] = edge
                    queue.add(doubleArrayOf(total + metersBetween(graph.nodePoint(next), goalPoint), next.toDouble()))
                }
            }
        }
        if (viaEdge[goal] == -1) return null
        val path = ArrayList<Int>()
        var node = goal
        while (node != start) {
            val edge = viaEdge[node]
            path.add(edge)
            node = if (graph.edgeFrom[edge] == node) graph.edgeTo[edge] else graph.edgeFrom[edge]
        }
        path.reverse()
        return path
    }

    private fun build(
        mode: RouteMode,
        path: List<Int>,
        startNode: Int,
        origin: LatLon,
        destination: LatLon,
        hour: Int,
        effects: Map<Int, Int>,
        reports: List<Report>,
        sensors: List<Sensor>,
        referenceLoad: Double?,
        sameAsFastest: Boolean,
    ): RouteResult {
        val points = ArrayList<LatLon>()
        points.add(origin)
        val startPoint = graph.nodePoint(startNode)
        if (metersBetween(origin, startPoint) > 2) points.add(startPoint)

        val metersByLevel = DoubleArray(NoiseLevel.entries.size)
        var load = 0.0
        var dbSum = 0.0
        var peak = 0
        var totalMeters = 0.0
        val parks = HashSet<Int>()
        var node = startNode
        // Para los pasos: índice de inicio de cada arista dentro de la geometría.
        val edgeStartIndex = IntArray(path.size)
        for ((i, edge) in path.withIndex()) {
            edgeStartIndex[i] = points.size - 1
            graph.appendEdgePoints(edge, node, points)
            node = if (graph.edgeFrom[edge] == node) graph.edgeTo[edge] else graph.edgeFrom[edge]
            val length = graph.edgeLen[edge].toDouble()
            val db = noise.edgeDb(edge, hour, effects)
            totalMeters += length
            dbSum += db * length
            peak = max(peak, db)
            metersByLevel[NoiseLevel.of(db).ordinal] += length
            load += length * max(0.0, db - 45.0)
            val park = graph.edgePark[edge]
            if (park >= 0) parks.add(park)
        }
        if (metersBetween(points.last(), destination) > 2) points.add(destination)

        val cumulative = DoubleArray(points.size)
        for (i in 1 until points.size) cumulative[i] = cumulative[i - 1] + metersBetween(points[i - 1], points[i])
        val meters = cumulative.last()
        val seconds = meters / WALK_SPEED

        // Equipamiento real de OpenStreetMap a lo largo de la ruta, muestreado cada 40 m.
        val benchSeen = HashSet<LatLon>()
        var shade = 0
        var inShade = false
        var nextSample = 0.0
        for (i in points.indices) {
            if (cumulative[i] < nextSample) continue
            nextSample = cumulative[i] + 40.0
            benchSeen.addAll(benches.within(points[i], 25.0))
            val shaded = trees.within(points[i], 18.0).size >= 2
            if (shaded && !inShade) shade++
            inShade = shaded
        }

        val now = System.currentTimeMillis()
        val onRoute = reports.count { r -> r.isActive(now) && isNearRoute(r.point, points, cumulative, 70.0) }
        val covered = points.indices.filter { i -> i % 4 == 0 }
        val nearSensor = if (covered.isEmpty()) 0 else
            covered.count { i -> sensors.any { metersBetween(it.point, points[i]) < 150 } } * 100 / covered.size

        val reduction = referenceLoad?.let { ref ->
            if (ref <= 0) 0 else ((1 - load / ref) * 100).roundToInt()
        }
        return RouteResult(
            mode = mode,
            points = points,
            cumulative = cumulative,
            meters = meters,
            seconds = seconds,
            steps = buildSteps(path, startNode, edgeStartIndex, points, cumulative),
            averageDb = if (totalMeters > 0) (dbSum / totalMeters).roundToInt() else 0,
            peakDb = peak,
            metersByLevel = metersByLevel,
            noiseLoad = load,
            noiseReductionPct = reduction,
            greenAreas = parks.size,
            benches = benchSeen.size,
            shadeStretches = shade,
            reportsOnRoute = onRoute,
            nearSensorPct = nearSensor,
            sameAsFastest = sameAsFastest,
        )
    }

    private fun isNearRoute(p: LatLon, points: List<LatLon>, cumulative: DoubleArray, meters: Double): Boolean {
        var next = 0.0
        for (i in points.indices) {
            if (cumulative[i] < next) continue
            next = cumulative[i] + 25
            if (metersBetween(p, points[i]) < meters) return true
        }
        return false
    }

    private data class Leg(var name: String, var meters: Double, var startIndex: Int, var firstEdge: Int, var lastEdge: Int)

    private fun buildSteps(
        path: List<Int>,
        startNode: Int,
        edgeStart: IntArray,
        points: List<LatLon>,
        cumulative: DoubleArray,
    ): List<RouteStep> {
        val total = cumulative.lastOrNull() ?: 0.0
        if (path.isEmpty()) {
            return listOf(RouteStep(StepKind.ARRIVE, "Llegada a tu destino", total, total / WALK_SPEED, 0))
        }
        // Agrupa tramos consecutivos de la misma calle; los tramos sin nombre cortos se absorben.
        val legs = ArrayList<Leg>()
        for ((i, edge) in path.withIndex()) {
            val name = graph.names[graph.edgeName[edge]]
            val last = legs.lastOrNull()
            val length = graph.edgeLen[edge].toDouble()
            if (last != null && (name == last.name || name.isEmpty() && length < 40)) {
                last.meters += length
                last.lastEdge = i
            } else if (last != null && last.name.isEmpty() && last.meters < 40) {
                last.name = name
                last.meters += length
                last.lastEdge = i
            } else {
                legs.add(Leg(name, length, edgeStart[i], i, i))
            }
        }
        val steps = ArrayList<RouteStep>()
        for ((i, leg) in legs.withIndex()) {
            val where = leg.name.ifEmpty { "el camino peatonal" }
            val kind: StepKind
            val text: String
            if (i == 0) {
                kind = StepKind.START
                text = "Sigue por $where"
            } else {
                val turn = turnAngle(points, leg.startIndex, cumulative)
                kind = when {
                    turn > 60 -> StepKind.RIGHT
                    turn > 25 -> StepKind.SLIGHT_RIGHT
                    turn < -60 -> StepKind.LEFT
                    turn < -25 -> StepKind.SLIGHT_LEFT
                    else -> StepKind.STRAIGHT
                }
                text = when (kind) {
                    StepKind.RIGHT -> "Gira a la derecha por $where"
                    StepKind.LEFT -> "Gira a la izquierda por $where"
                    StepKind.SLIGHT_RIGHT -> "Ve hacia la derecha por $where"
                    StepKind.SLIGHT_LEFT -> "Ve hacia la izquierda por $where"
                    else -> "Continúa por $where"
                }
            }
            steps.add(RouteStep(kind, text, leg.meters, leg.meters / WALK_SPEED, leg.startIndex))
        }
        steps.add(RouteStep(StepKind.ARRIVE, "Llegada a tu destino", 0.0, 0.0, points.size - 1))
        return steps
    }

    private fun turnAngle(points: List<LatLon>, index: Int, cumulative: DoubleArray): Double {
        var before = index
        while (before > 0 && cumulative[index] - cumulative[before] < 12) before--
        var after = index
        while (after < points.size - 1 && cumulative[after] - cumulative[index] < 12) after++
        if (before == index || after == index) return 0.0
        val a = bearing(points[before], points[index])
        val b = bearing(points[index], points[after])
        var diff = b - a
        while (diff > 180) diff -= 360
        while (diff < -180) diff += 360
        return diff
    }

    private fun bearing(a: LatLon, b: LatLon): Double {
        val dLon = (b.lon - a.lon) * PI / 180
        val la1 = a.lat * PI / 180
        val la2 = b.lat * PI / 180
        return atan2(sin(dLon) * cos(la2), cos(la1) * sin(la2) - sin(la1) * cos(la2) * cos(dLon)) * 180 / PI
    }

    companion object {
        /** Unos 4,5 km/h: ritmo tranquilo a pie. */
        const val WALK_SPEED = 1.25

        /** Devuelve la distancia recorrida al proyectar una posición sobre la ruta, y su separación en metros. */
        fun projectOnRoute(route: RouteResult, p: LatLon, fromMeters: Double): Pair<Double, Double> {
            var bestAlong = fromMeters
            var bestDist = Double.MAX_VALUE
            val pts = route.points
            val kx = 111320.0 * cos(p.lat * PI / 180)
            val ky = 110574.0
            for (i in 1 until pts.size) {
                // Solo se busca hacia delante (con un margen), para no saltar a un tramo paralelo ya hecho.
                if (route.cumulative[i] < fromMeters - 30) continue
                val ax = (pts[i - 1].lon - p.lon) * kx
                val ay = (pts[i - 1].lat - p.lat) * ky
                val bx = (pts[i].lon - p.lon) * kx
                val by = (pts[i].lat - p.lat) * ky
                val dx = bx - ax
                val dy = by - ay
                val len2 = dx * dx + dy * dy
                val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
                val d = kotlin.math.hypot(ax + t * dx, ay + t * dy)
                if (d < bestDist) {
                    bestDist = d
                    bestAlong = route.cumulative[i - 1] + t * (route.cumulative[i] - route.cumulative[i - 1])
                }
            }
            return bestAlong to bestDist
        }

        fun pointAlong(route: RouteResult, meters: Double): LatLon {
            val c = route.cumulative
            val target = meters.coerceIn(0.0, c.last())
            var i = 1
            while (i < c.size - 1 && c[i] < target) i++
            val span = c[i] - c[i - 1]
            val t = if (span <= 0) 0.0 else (target - c[i - 1]) / span
            val a = route.points[i - 1]
            val b = route.points[i]
            return LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
        }
    }
}
