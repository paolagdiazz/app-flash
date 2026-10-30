package com.blueway.app.data

import com.blueway.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Error del servidor con el código que devuelve backend/schema.sql. */
class SharedReportsException(val code: String) : IOException(code)

/** Cliente de los avisos compartidos. Solo envía el identificador aleatorio de la instalación y el aviso. */
object SharedReports {
    private val baseUrl = BuildConfig.REPORTS_API_URL.trimEnd('/')
    private val apiKey = BuildConfig.REPORTS_API_KEY

    val enabled: Boolean get() = baseUrl.isNotEmpty()

    fun list(device: String): List<Report> {
        val rows = JSONArray(call("list_reports", JSONObject().put("p_device", device)))
        return (0 until rows.length()).mapNotNull { i ->
            val o = rows.getJSONObject(i)
            // Un tipo desconocido puede venir de una versión más nueva de la app: se ignora.
            val type = ReportType.entries.firstOrNull { it.name == o.getString("type") } ?: return@mapNotNull null
            Report(
                id = o.getString("id"),
                type = type,
                point = LatLon(o.getDouble("lat"), o.getDouble("lon")),
                description = o.getString("description"),
                createdAt = o.getLong("created_ms"),
                expiresAt = o.getLong("expires_ms"),
                confirmations = o.getInt("confirmations"),
                rejections = o.getInt("rejections"),
                isSample = false,
                mine = o.getBoolean("mine"),
                voted = o.getBoolean("voted"),
            )
        }
    }

    /** Devuelve true si el servidor lo ha fusionado con un aviso igual que ya existía. */
    fun create(device: String, report: Report): Boolean {
        val body = JSONObject()
            .put("p_device", device)
            .put("p_type", report.type.name)
            .put("p_lat", report.point.lat)
            .put("p_lon", report.point.lon)
            .put("p_description", report.description)
        return JSONArray(call("create_report", body)).getJSONObject(0).getBoolean("merged")
    }

    fun vote(device: String, id: String, stillThere: Boolean) {
        call("vote_report", JSONObject().put("p_device", device).put("p_id", id).put("p_still_there", stillThere))
    }

    fun delete(device: String, id: String) {
        call("delete_report", JSONObject().put("p_device", device).put("p_id", id))
    }

    private fun call(function: String, body: JSONObject): String {
        val conn = URL("$baseUrl/rpc/$function").openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("User-Agent", "BlueWay-Android")
        if (apiKey.isNotEmpty()) {
            conn.setRequestProperty("apikey", apiKey)
            // Las claves antiguas de Supabase son JWT y también van aquí; las "sb_publishable_" no.
            if (!apiKey.startsWith("sb_")) conn.setRequestProperty("Authorization", "Bearer $apiKey")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val status = conn.responseCode
            if (status in 200..299) return conn.inputStream.bufferedReader().use { it.readText() }
            val error = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val code = runCatching { JSONObject(error).getString("message") }.getOrDefault("http_$status")
            throw SharedReportsException(code)
        } finally {
            conn.disconnect()
        }
    }
}
