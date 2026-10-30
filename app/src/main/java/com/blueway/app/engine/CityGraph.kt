package com.blueway.app.engine

import com.blueway.app.data.LatLon
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.zip.GZIPInputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot

const val FLAG_STEPS = 1
const val FLAG_GREEN = 2
const val FLAG_ROUGH = 4
const val FLAG_BAD_WHEELCHAIR = 8

/** Distancia aproximada en metros, suficiente a escala de una ciudad. */
fun metersBetween(a: LatLon, b: LatLon): Double = metersBetween(a.lat, a.lon, b.lat, b.lon)

fun metersBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val kx = 111320.0 * cos((lat1 + lat2) / 2 * PI / 180)
    return hypot((lat1 - lat2) * 110574.0, (lon1 - lon2) * kx)
}

/** Grafo peatonal de Málaga cargado desde assets (ver tools/build_city_data.py). */
class CityGraph private constructor(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
    val nodeLat: IntArray,
    val nodeLon: IntArray,
    val edgeFrom: IntArray,
    val edgeTo: IntArray,
    val edgeLen: FloatArray,
    val edgeName: IntArray,
    val edgePark: IntArray,
    val edgeDb: ByteArray,
    val edgeCrowd: ByteArray,
    val edgeFlags: ByteArray,
    private val geomOffset: IntArray,
    private val geom: IntArray,
    val names: List<String>,
) {
    val nodeCount get() = nodeLat.size
    val edgeCount get() = edgeFrom.size

    // Adyacencia compacta (CSR)
    val adjStart = IntArray(nodeCount + 1)
    val adjEdge: IntArray

    private val nodeGrid = HashMap<Long, IntArray>()
    private val edgeGrid = HashMap<Long, IntArray>()

    init {
        for (e in 0 until edgeCount) {
            adjStart[edgeFrom[e] + 1]++
            adjStart[edgeTo[e] + 1]++
        }
        for (i in 0 until nodeCount) adjStart[i + 1] += adjStart[i]
        adjEdge = IntArray(edgeCount * 2)
        val fill = adjStart.copyOf()
        for (e in 0 until edgeCount) {
            adjEdge[fill[edgeFrom[e]]++] = e
            adjEdge[fill[edgeTo[e]]++] = e
        }
        val nodeBuckets = HashMap<Long, MutableList<Int>>()
        for (n in 0 until nodeCount) {
            nodeBuckets.getOrPut(cellKey(nodeLat[n] / 1e5, nodeLon[n] / 1e5)) { ArrayList(4) }.add(n)
        }
        nodeBuckets.forEach { (k, v) -> nodeGrid[k] = v.toIntArray() }
        val edgeBuckets = HashMap<Long, MutableList<Int>>()
        for (e in 0 until edgeCount) {
            val mid = edgeMidpoint(e)
            edgeBuckets.getOrPut(cellKey(mid.lat, mid.lon)) { ArrayList(4) }.add(e)
        }
        edgeBuckets.forEach { (k, v) -> edgeGrid[k] = v.toIntArray() }
    }

    fun contains(p: LatLon) = p.lat in south..north && p.lon in west..east

    fun nodePoint(n: Int) = LatLon(nodeLat[n] / 1e5, nodeLon[n] / 1e5)

    fun edgeMidpoint(e: Int): LatLon {
        val a = nodePoint(edgeFrom[e])
        val b = nodePoint(edgeTo[e])
        val start = geomOffset[e]
        val end = geomOffset[e + 1]
        if (end > start) {
            val m = (start + end) / 2
            return LatLon(geom[m * 2] / 1e5, geom[m * 2 + 1] / 1e5)
        }
        return LatLon((a.lat + b.lat) / 2, (a.lon + b.lon) / 2)
    }

    /** Puntos de la arista en el sentido indicado, sin repetir el nodo inicial. */
    fun appendEdgePoints(e: Int, fromNode: Int, out: MutableList<LatLon>) {
        val start = geomOffset[e]
        val end = geomOffset[e + 1]
        if (edgeFrom[e] == fromNode) {
            for (i in start until end) out.add(LatLon(geom[i * 2] / 1e5, geom[i * 2 + 1] / 1e5))
            out.add(nodePoint(edgeTo[e]))
        } else {
            for (i in end - 1 downTo start) out.add(LatLon(geom[i * 2] / 1e5, geom[i * 2 + 1] / 1e5))
            out.add(nodePoint(edgeFrom[e]))
        }
    }

    fun nearestNode(p: LatLon, maxMeters: Double = 800.0): Int? = nearestIn(nodeGrid, p, maxMeters) { nodePoint(it) }

    fun nearestEdge(p: LatLon, maxMeters: Double = 150.0): Int? = nearestIn(edgeGrid, p, maxMeters) { edgeMidpoint(it) }

    /** Recorre las aristas cuyo punto medio está dentro del radio. */
    inline fun forEachEdgeNear(p: LatLon, radius: Double, action: (Int, Double) -> Unit) {
        for (e in edgesInRange(p, radius)) {
            val d = metersBetween(p, edgeMidpoint(e))
            if (d <= radius) action(e, d)
        }
    }

    fun edgesInRange(p: LatLon, radius: Double): List<Int> {
        val out = ArrayList<Int>()
        val reach = (radius / CELL_METERS).toInt() + 1
        val (cx, cy) = cellOf(p.lat, p.lon)
        for (i in -reach..reach) for (j in -reach..reach) {
            edgeGrid[key(cx + i, cy + j)]?.forEach { out.add(it) }
        }
        return out
    }

    private inline fun nearestIn(grid: Map<Long, IntArray>, p: LatLon, maxMeters: Double, point: (Int) -> LatLon): Int? {
        val (cx, cy) = cellOf(p.lat, p.lon)
        val maxRing = (maxMeters / CELL_METERS).toInt() + 1
        var best = -1
        var bestDist = maxMeters
        for (ring in 0..maxRing) {
            for (i in -ring..ring) for (j in -ring..ring) {
                if (maxOf(kotlin.math.abs(i), kotlin.math.abs(j)) != ring) continue
                grid[key(cx + i, cy + j)]?.forEach { id ->
                    val d = metersBetween(p, point(id))
                    if (d < bestDist) {
                        bestDist = d
                        best = id
                    }
                }
            }
            // Cualquier celda más lejana queda ya fuera de la mejor distancia encontrada.
            if (best >= 0 && (ring - 1) * CELL_METERS > bestDist) break
        }
        return best.takeIf { it >= 0 }
    }

    private fun cellOf(lat: Double, lon: Double): Pair<Int, Int> = Pair(
        floor(lat * 110574.0 / CELL_METERS).toInt(),
        floor(lon * KX / CELL_METERS).toInt(),
    )

    private fun cellKey(lat: Double, lon: Double): Long = cellOf(lat, lon).let { key(it.first, it.second) }
    private fun key(x: Int, y: Int): Long = (x.toLong() shl 32) xor (y.toLong() and 0xffffffffL)

    companion object {
        private const val CELL_METERS = 100.0
        private val KX = 111320.0 * cos(36.72 * PI / 180)

        fun load(stream: InputStream): CityGraph {
            val bytes = GZIPInputStream(stream.buffered()).use { it.readBytes() }
            val b = ByteBuffer.wrap(bytes)
            val magic = ByteArray(4).also { b.get(it) }
            require(String(magic) == "BWG1") { "Formato de grafo no reconocido" }
            val south = b.int / 1e5
            val west = b.int / 1e5
            val north = b.int / 1e5
            val east = b.int / 1e5
            val nodes = b.int
            val nodeLat = b.ints(nodes)
            val nodeLon = b.ints(nodes)
            val edges = b.int
            val from = b.ints(edges)
            val to = b.ints(edges)
            val len = FloatArray(edges).also { b.asFloatBuffer().get(it); b.position(b.position() + edges * 4) }
            val name = b.ints(edges)
            val park = b.ints(edges)
            val db = ByteArray(edges).also { b.get(it) }
            val crowd = ByteArray(edges).also { b.get(it) }
            val flags = ByteArray(edges).also { b.get(it) }
            val offsets = b.ints(edges + 1)
            val geomCount = b.int
            val geom = b.ints(geomCount)
            val nameBytes = ByteArray(b.int).also { b.get(it) }
            val names = String(nameBytes, Charsets.UTF_8).split('\n')
            return CityGraph(south, west, north, east, nodeLat, nodeLon, from, to, len, name, park, db, crowd, flags, offsets, geom, names)
        }

        private fun ByteBuffer.ints(n: Int): IntArray {
            val out = IntArray(n)
            asIntBuffer().get(out)
            position(position() + n * 4)
            return out
        }
    }
}
