package com.blueway.app.data

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.blueway.app.R

data class LatLon(val lat: Double, val lon: Double)

/** De dónde sale un dato. La app lo muestra siempre en texto, nunca solo con color. */
enum class DataSource(val label: String, val explanation: String) {
    MEASURED("Medido", "Lectura de un sensor real. Todavía no hay ninguno instalado."),
    SIMULATED("Sensor simulado", "Sensor de ejemplo para probar la app. No es una medición real."),
    REPORTED("Avisado", "Aviso de una persona usuaria. Puede no estar comprobado."),
    ESTIMATED("Estimado", "Cálculo orientativo según el tipo de calle, el tráfico habitual y la hora."),
    NONE("Sin datos", "BlueWay no tiene información de esta zona."),
}

enum class NoiseLevel(
    val label: String,
    val range: String,
    val tag: String,
    val description: String,
    val color: Color,
    val soft: Color,
    @param:DrawableRes val icon: Int,
) {
    LOW(
        "Bajo", "0 – 55 dB", "Zona tranquila",
        "Ambiente silencioso y adecuado para personas sensibles al ruido.",
        Color(0xFF12A36B), Color(0xFFE4F8EF), R.drawable.ic_report_noise_low,
    ),
    MODERATE(
        "Moderado", "56 – 65 dB", "Precaución",
        "Puede resultar molesto para algunas personas. Se recomienda valorar rutas alternativas.",
        Color(0xFFB87400), Color(0xFFFFF5DC), R.drawable.ic_report_noise_moderate,
    ),
    HIGH(
        "Alto", "66 – 75 dB", "Evita si es posible",
        "Nivel de ruido elevado que puede generar estrés y malestar.",
        Color(0xFFD9480F), Color(0xFFFFEDE4), R.drawable.ic_report_noise_high,
    ),
    VERY_HIGH(
        "Muy alto", "+ 75 dB", "Zona de alto ruido",
        "Ambiente muy ruidoso. Se recomienda evitar esta zona y buscar rutas más tranquilas.",
        Color(0xFFD01C3F), Color(0xFFFFE6EA), R.drawable.ic_report_noise_extreme,
    );

    companion object {
        fun of(db: Int) = when {
            db <= 55 -> LOW
            db <= 65 -> MODERATE
            db <= 75 -> HIGH
            else -> VERY_HIGH
        }
    }
}

enum class PlaceCategory(val label: String) {
    EDUCATION("Educación"),
    HEALTH("Salud"),
    PARKS("Parques"),
    TRANSPORT("Transporte"),
    CULTURE("Cultura"),
    CALM("Ocio tranquilo"),
    OTHER("Lugar"),
}

data class Place(
    val name: String,
    val detail: String,
    val point: LatLon,
    val category: PlaceCategory = PlaceCategory.OTHER,
)

enum class RouteMode(val label: String, val blurb: String) {
    CALM("Ruta tranquila", "Una ruta más tranquila y con menos estímulos sonoros."),
    FAST("Ruta rápida", "La ruta más corta, aunque pueda pasar por zonas con más ruido."),
    QUIETEST("Menos ruido", "Prioriza evitar el ruido, aunque haya que caminar más."),
}

data class RouteFilters(
    val avoidHighNoise: Boolean = true,
    val preferGreen: Boolean = true,
    val avoidCrowds: Boolean = true,
    val shortest: Boolean = false,
    val accessible: Boolean = true,
    /** 0 baja, 1 media, 2 alta */
    val sensitivity: Int = 2,
)

enum class StepKind { START, STRAIGHT, LEFT, RIGHT, SLIGHT_LEFT, SLIGHT_RIGHT, ARRIVE }

data class RouteStep(
    val kind: StepKind,
    val text: String,
    val meters: Double,
    val seconds: Double,
    /** Índice del punto de la geometría en que empieza este paso. */
    val startIndex: Int,
)

data class RouteResult(
    val mode: RouteMode,
    val points: List<LatLon>,
    val cumulative: DoubleArray,
    val meters: Double,
    val seconds: Double,
    val steps: List<RouteStep>,
    val averageDb: Int,
    val peakDb: Int,
    /** Metros por nivel de ruido, en el orden de [NoiseLevel]. */
    val metersByLevel: DoubleArray,
    val noiseLoad: Double,
    /** Reducción de carga sonora estimada frente a la ruta rápida; null si esta es la referencia. */
    val noiseReductionPct: Int?,
    val greenAreas: Int,
    val benches: Int,
    val shadeStretches: Int,
    val reportsOnRoute: Int,
    val nearSensorPct: Int,
    val sameAsFastest: Boolean,
)

enum class ReportType(
    val label: String,
    val shortDescription: String,
    val expiresHours: Int,
    /** Efecto estimado en dB sobre las calles cercanas. */
    val noiseEffect: Int,
    @param:DrawableRes val icon: Int,
) {
    WORKS("Obras en la zona", "Aumento temporal del ruido por obras.", 24 * 7, 10, R.drawable.ic_report_works),
    EVENT("Evento cercano", "Concierto, manifestación o aglomeración.", 6, 12, R.drawable.ic_report_event),
    TRAFFIC("Tráfico elevado", "Más tráfico de lo habitual.", 2, 8, R.drawable.ic_report_traffic),
    NOISE_EXTREME("Ruido excesivo", "Nivel de ruido muy molesto.", 2, 15, R.drawable.ic_report_noise_extreme),
    NOISE_HIGH("Ruido alto", "Se oye bastante ruido.", 2, 8, R.drawable.ic_report_noise_high),
    NOISE_MODERATE("Ruido moderado", "Ruido notable pero soportable.", 2, 3, R.drawable.ic_report_noise_moderate),
    NOISE_NORMAL("Ruido normal", "Ruido habitual de la calle.", 2, 0, R.drawable.ic_report_noise_normal),
    NOISE_LOW("Ruido bajo", "Zona tranquila ahora mismo.", 2, -3, R.drawable.ic_report_noise_low),
    GENERAL("Aviso general", "Otra incidencia que conviene conocer.", 3, 0, R.drawable.ic_report_general),
}

enum class ReportStatus(val label: String) {
    PENDING("Pendiente de confirmar"),
    CONFIRMED("Confirmado"),
    EXPIRED("Caducado"),
    DISMISSED("Descartado"),
}

data class Report(
    val id: String,
    val type: ReportType,
    val point: LatLon,
    val description: String,
    val createdAt: Long,
    val expiresAt: Long,
    val confirmations: Int,
    val rejections: Int,
    val isSample: Boolean,
    val mine: Boolean,
    /** Ya valorado desde este teléfono. */
    val voted: Boolean = false,
    /** False mientras un aviso propio espera a subirse al servidor. */
    val synced: Boolean = true,
) {
    fun status(now: Long = System.currentTimeMillis()): ReportStatus = when {
        rejections >= 2 && rejections > confirmations -> ReportStatus.DISMISSED
        now >= expiresAt -> ReportStatus.EXPIRED
        confirmations >= 2 -> ReportStatus.CONFIRMED
        else -> ReportStatus.PENDING
    }

    fun isActive(now: Long = System.currentTimeMillis()) =
        status(now).let { it == ReportStatus.PENDING || it == ReportStatus.CONFIRMED }
}

data class Sensor(val id: String, val name: String, val point: LatLon)

data class Zone(
    val title: String,
    val subtitle: String,
    val point: LatLon,
    val db: Int,
    val source: DataSource,
    val crowdPct: Int,
    val benches: Int,
    val cafes: Int,
    val trees: Int,
    val inGreen: Boolean,
    val curve: List<Int>,
    val quietHours: String,
    val sensorNote: String?,
    val reports: List<Report>,
)

data class SavedRoute(val id: String, val name: String, val origin: Place, val destination: Place, val mode: RouteMode)

data class AppSettings(
    val onboarded: Boolean = false,
    val name: String = "María",
    val highContrast: Boolean = false,
    val reduceMotion: Boolean = false,
    val voiceGuidance: Boolean = false,
    val notifyReports: Boolean = true,
    val notifyTypes: Set<ReportType> = ReportType.entries.toSet(),
    val filters: RouteFilters = RouteFilters(),
)
