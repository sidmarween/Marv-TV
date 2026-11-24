package de.marween.tv

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object Http {

    /** Öffnet eine URL und folgt Weiterleitungen (auch http ↔ https). */
    fun openStream(url: String, userAgent: String = Store.userAgent): InputStream {
        var current = url
        repeat(6) {
            val conn = URL(current).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15_000
            conn.readTimeout = 45_000
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept", "*/*")
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
            return conn.inputStream
        }
        throw IOException("Zu viele Weiterleitungen")
    }

    fun getText(url: String): String = openStream(url).bufferedReader().use { it.readText() }

    private fun message(code: Int): String = when (code) {
        401, 403 -> "Zugriff verweigert (HTTP $code) – Zugangsdaten oder Abo prüfen"
        404 -> "Nicht gefunden (HTTP 404) – Adresse prüfen"
        429 -> "Zu viele Anfragen (HTTP 429) – bitte kurz warten"
        in 500..599 -> "Der Server hat einen Fehler gemeldet (HTTP $code)"
        else -> "Unerwartete Antwort vom Server (HTTP $code)"
    }
}
