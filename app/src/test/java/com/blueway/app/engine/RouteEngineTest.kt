package com.blueway.app.engine

import com.blueway.app.data.LatLon
import com.blueway.app.data.Report
import com.blueway.app.data.ReportRules
import com.blueway.app.data.ReportType
import com.blueway.app.data.RouteFilters
import com.blueway.app.data.RouteMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class RouteEngineTest {
    private val zambrano = LatLon(36.7123, -4.4311)
    private val merced = LatLon(36.7235, -4.4176)
    private val noon = 12

    private fun compute(from: LatLon, to: LatLon, filters: RouteFilters = RouteFilters(), reports: List<Report> = emptyList()) =
        engine.compute(from, to, filters, reports, ReportRules.SENSORS, noon)!!

    @Test
    fun theFastRouteIsNeverLongerThanTheOthers() {
        val routes = compute(zambrano, merced)
        val fast = routes.first { it.mode == RouteMode.FAST }
        routes.forEach { assertTrue("${it.mode} ${it.meters} < ${fast.meters}", it.meters >= fast.meters - 1) }
    }

    @Test
    fun theQuietRoutesCarryNoMoreNoiseThanTheFastOne() {
        val routes = compute(zambrano, merced)
        val fast = routes.first { it.mode == RouteMode.FAST }
        val quiet = routes.first { it.mode == RouteMode.QUIETEST }
        assertTrue(quiet.noiseLoad <= fast.noiseLoad)
        assertTrue((quiet.noiseReductionPct ?: 0) > 0)
    }

    @Test
    fun everyRouteHasGeometryStepsAndArrival() {
        compute(zambrano, merced).forEach { r ->
            assertTrue(r.points.size > 5)
            assertEquals(r.points.size, r.cumulative.size)
            assertEquals(com.blueway.app.data.StepKind.ARRIVE, r.steps.last().kind)
            assertTrue(r.meters in 1200.0..4500.0)
        }
    }

    @Test
    fun aLoudReportPushesTheCalmRouteAway() {
        val before = compute(zambrano, merced).first { it.mode == RouteMode.CALM }
        val middle = before.points[before.points.size / 2]
        val report = ReportRules.create(ReportType.NOISE_EXTREME, middle, "", false)
        val after = compute(zambrano, merced, reports = listOf(report)).first { it.mode == RouteMode.CALM }
        val distanceBefore = before.points.minOf { metersBetween(it, report.point) }
        val distanceAfter = after.points.minOf { metersBetween(it, report.point) }
        assertTrue("Sigue pasando a $distanceAfter m (antes $distanceBefore m)", distanceAfter > distanceBefore)
    }

    @Test
    fun accessibilityFilterNeverShortensTheRoute() {
        // Subida a Gibralfaro: una zona con escaleras en OpenStreetMap. Sin alternativa, no hay ruta (la app lo explica).
        val from = LatLon(36.7214, -4.4163)
        val to = LatLon(36.7239, -4.4110)
        val free = compute(from, to, RouteFilters(accessible = false)).first { it.mode == RouteMode.FAST }
        val accessible = engine.compute(from, to, RouteFilters(accessible = true), emptyList(), ReportRules.SENSORS, noon)
            ?.first { it.mode == RouteMode.FAST }
        if (accessible != null) assertTrue(accessible.meters >= free.meters - 1)
    }

    @Test
    fun sensitivityChangesHowMuchNoiseIsAvoided() {
        val low = compute(zambrano, merced, RouteFilters(sensitivity = 0)).first { it.mode == RouteMode.QUIETEST }
        val high = compute(zambrano, merced, RouteFilters(sensitivity = 2)).first { it.mode == RouteMode.QUIETEST }
        assertTrue(high.noiseLoad <= low.noiseLoad)
    }

    @Test
    fun pointsOutsideTheCityHaveNoRoute() {
        assertNull(engine.compute(LatLon(40.4168, -3.7038), merced, RouteFilters(), emptyList(), ReportRules.SENSORS, noon))
    }

    @Test
    fun nightIsQuieterThanMidday() {
        val edge = graph.nearestEdge(merced)!!
        assertTrue(noise.edgeDb(edge, 4) < noise.edgeDb(edge, 12))
    }

    @Test
    fun duplicatesAreDetectedAndPrecisionIsReduced() {
        val r = ReportRules.create(ReportType.WORKS, LatLon(36.7123456, -4.4311999), "", false)
        assertEquals(36.7123, r.point.lat, 1e-9)
        assertEquals(-4.4312, r.point.lon, 1e-9)
        assertNotNull(ReportRules.findDuplicate(listOf(r), ReportType.WORKS, LatLon(36.7128, -4.4312)))
        assertNull(ReportRules.findDuplicate(listOf(r), ReportType.WORKS, LatLon(36.7200, -4.4312)))
        assertNull(ReportRules.findDuplicate(listOf(r), ReportType.TRAFFIC, r.point))
    }

    @Test
    fun printsTheTradeOffOnTypicalTrips() {
        val trips = listOf(
            "Zambrano-Merced" to (zambrano to merced),
            "Zambrano-Malagueta" to (zambrano to LatLon(36.7190, -4.4085)),
            "Teatinos-Centro" to (LatLon(36.7165, -4.4954) to LatLon(36.7211, -4.4220)),
            "Huelin-Muelle Uno" to (LatLon(36.7058, -4.4327) to LatLon(36.7160, -4.4138)),
        )
        trips.forEach { (name, trip) ->
            val start = System.currentTimeMillis()
            val routes = compute(trip.first, trip.second)
            val ms = System.currentTimeMillis() - start
            val text = routes.joinToString(" | ") {
                "${it.mode}: ${it.meters.toInt()} m, ${it.averageDb} dB, -${it.noiseReductionPct ?: 0}%"
            }
            println("$name (${ms} ms): $text")
        }
    }

    companion object {
        private lateinit var graph: CityGraph
        private lateinit var noise: NoiseModel
        private lateinit var engine: RouteEngine

        @BeforeClass
        @JvmStatic
        fun load() {
            val dir = listOf("src/main/assets/city", "app/src/main/assets/city").map(::File).first { it.exists() }
            graph = File(dir, "graph.bwg").inputStream().use { CityGraph.load(it) }
            noise = NoiseModel(graph)
            engine = RouteEngine(graph, noise, PointIndex(IntArray(0)), PointIndex(IntArray(0)))
        }
    }
}
