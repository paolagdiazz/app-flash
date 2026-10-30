package com.blueway.app.engine

import com.blueway.app.data.LatLon
import com.blueway.app.data.Report
import java.util.Calendar
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Modelo orientativo de ruido. El valor base de cada calle se estima por tipo de vía y cercanía al
 * tráfico (tools/build_city_data.py); aquí se ajusta por hora y por los avisos activos.
 * Nada de esto es una medición: la interfaz lo presenta como "Estimado".
 */
class NoiseModel(private val graph: CityGraph) {

    /** Ajuste en dB por hora del día respecto al nivel diurno. */
    fun hourOffset(hour: Int): Int = HOUR_OFFSET[hour.coerceIn(0, 23)]

    fun currentHour(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

    /** Efecto en dB de los avisos activos por arista. */
    fun reportEffects(reports: List<Report>): Map<Int, Int> {
        val out = HashMap<Int, Int>()
        val now = System.currentTimeMillis()
        reports.filter { it.isActive(now) && it.type.noiseEffect != 0 }.forEach { report ->
            graph.forEachEdgeNear(report.point, REPORT_RADIUS) { edge, dist ->
                val factor = 1.0 - dist / REPORT_RADIUS * 0.6
                val effect = (report.type.noiseEffect * factor).roundToInt()
                val current = out[edge]
                if (current == null || kotlin.math.abs(effect) > kotlin.math.abs(current)) out[edge] = effect
            }
        }
        return out
    }

    fun edgeDb(edge: Int, hour: Int, effects: Map<Int, Int> = emptyMap()): Int {
        val base = graph.edgeDb[edge].toInt() and 0xff
        val crowd = (graph.edgeCrowd[edge].toInt() and 0xff) / 100.0
        // Por la noche, las zonas de ocio bajan menos que el resto.
        val offset = hourOffset(hour) + if (hour >= 21 || hour <= 1) (crowd * 5).roundToInt() else 0
        return (base + offset + (effects[edge] ?: 0)).coerceIn(35, 95)
    }

    fun crowdPct(edge: Int, hour: Int): Int {
        val base = graph.edgeCrowd[edge].toInt() and 0xff
        return (base * CROWD_BY_HOUR[hour.coerceIn(0, 23)]).roundToInt().coerceIn(0, 100)
    }

    /** Curva de 24 valores para la ficha de zona. */
    fun dayCurve(edge: Int, effects: Map<Int, Int> = emptyMap()): List<Int> = (0..23).map { edgeDb(edge, it, effects) }

    /** Lectura de un sensor de ejemplo: el modelo con una pequeña variación que cambia con el tiempo. */
    fun simulatedSensorDb(point: LatLon, hour: Int, now: Long, effects: Map<Int, Int>): Int? {
        val edge = graph.nearestEdge(point) ?: return null
        val phase = (now / 60000L % 600L).toDouble() / 600.0 * 6.283 + point.lat * 1000
        return edgeDb(edge, hour, effects) + (sin(phase) * 2).roundToInt()
    }

    companion object {
        const val REPORT_RADIUS = 130.0

        private val HOUR_OFFSET = intArrayOf(
            -9, -10, -11, -11, -11, -10, -7, -3, 1, 1, 0, 0,
            0, 1, 0, -1, 0, 1, 2, 2, 1, -2, -4, -7,
        )
        private val CROWD_BY_HOUR = doubleArrayOf(
            .25, .15, .1, .05, .05, .05, .1, .25, .45, .6, .75, .9,
            1.0, 1.0, .85, .7, .75, .9, 1.0, 1.0, .95, .85, .65, .4,
        )
    }
}
