package de.marween.tv

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object Http {

    /** Öffnet eine URL und folgt Weiterleitungen (auch http ↔ https). */
    fun openStream(url: String, userAgent: String = Store.userAgent): InputStream =
        open(url, userAgent, emptyMap(), 15_000, 45_000).first

    /** Liefert den Datenstrom und die endgültige Adresse nach Weiterleitungen. */
    private fun open(
        url: String,
        userAgent: String,
        headers: Map<String, String>,
        connectTimeout: Int,
        readTimeout: Int,
    ): Pair<InputStream, String> {
        var current = url
        repeat(6) {
            val conn = URL(current).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = connectTimeout
            conn.readTimeout = readTimeout
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept", "*/*")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) throw IOException("Weiterleitung ohne Ziel")
                current = URL(URL(current), location).toString()
                return@repeat
            }
            if (code !in 200..299) {
                conn.disconnect()
                throw IOException(message(code))
            }
            return conn.inputStream to current
        }
        throw IOException("Zu viele Weiterleitungen")
    }

    fun getText(url: String): String = openStream(url).bufferedReader().use { it.readText() }

    /**
     * Prüft, ob ein Sender wirklich Daten liefert.
     * Bei HLS-Listen wird zusätzlich eine Unterliste bzw. ein Videosegment abgerufen,
     * damit auch tote oder gesperrte Streams erkannt werden.
     */
    fun isReachable(ch: Channel): Boolean = try {
        val headers = if (ch.referrer.isNotBlank()) mapOf("Referer" to ch.referrer) else emptyMap()
        probe(ch.url, ch.userAgent.ifBlank { Store.userAgent }, headers, 0)
    } catch (e: Exception) {
        false
    }

    private fun probe(url: String, ua: String, headers: Map<String, String>, depth: Int): Boolean {
        val (input, finalUrl) = open(url, ua, headers, 7_000, 8_000)
        val text = input.use { stream ->
            val buf = ByteArray(8192)
            var total = 0
            while (total < buf.size) {
                val n = stream.read(buf, total, buf.size - total)
                if (n <= 0) break
                total += n
            }
            if (total <= 0) return false
            String(buf, 0, total, Charsets.ISO_8859_1)
        }
        val head = text.trimStart().lowercase()
        if (head.startsWith("<!doctype html") || head.startsWith("<html")) return false
        if (head.startsWith("#extm3u") && depth < 2) {
            val uris = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            if (uris.isEmpty()) return false
            // Hauptliste: erste Qualitätsstufe. Segmentliste: neuestes Segment.
            val next = if (text.contains("#EXTINF")) uris.last() else uris.first()
            return probe(URL(URL(finalUrl), next).toString(), ua, headers, depth + 1)
        }
        return true
    }

    private fun message(code: Int): String = when (code) {
        401, 403 -> "Zugriff verweigert (HTTP $code) – Zugangsdaten oder Abo prüfen"
        404 -> "Nicht gefunden (HTTP 404) – Adresse prüfen"
        429 -> "Zu viele Anfragen (HTTP 429) – bitte kurz warten"
        in 500..599 -> "Der Server hat einen Fehler gemeldet (HTTP $code)"
        else -> "Unerwartete Antwort vom Server (HTTP $code)"
    }
}
