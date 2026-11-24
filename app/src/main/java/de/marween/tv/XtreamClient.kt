package de.marween.tv

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Zugriff auf Xtream-Codes-Server (player_api.php). */
class XtreamClient(server: String, private val username: String, private val password: String) {

    val base: String = normalize(server)

    companion object {
        fun normalize(server: String): String {
            var u = server.trim()
            if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) u = "http://$u"
            u = u.substringBefore("/player_api.php").substringBefore("/get.php")
            return u.trimEnd('/')
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun api(action: String?, extra: String = ""): String {
        val sb = StringBuilder("$base/player_api.php?username=${enc(username)}&password=${enc(password)}")
        if (action != null) sb.append("&action=").append(action)
        sb.append(extra)
        return Http.getText(sb.toString())
    }

    private fun array(text: String): JSONArray {
        val t = text.trim()
        return if (t.startsWith("[")) JSONArray(t) else JSONArray()
    }

    /** Prüft die Anmeldung und liefert eine deutsche Kontoübersicht. */
    fun accountInfo(): String {
        val o = JSONObject(api(null))
        val ui = o.optJSONObject("user_info") ?: throw IOException("Unerwartete Antwort – ist das ein Xtream-Server?")
        if (ui.optInt("auth", 1) == 0) throw IOException("Anmeldung fehlgeschlagen – Benutzername und Passwort prüfen")
        val status = when (ui.optString("status").lowercase()) {
            "active" -> "Aktiv"
            "expired" -> "Abgelaufen"
            "banned" -> "Gesperrt"
            "disabled" -> "Deaktiviert"
            "" -> "Unbekannt"
            else -> ui.optString("status")
        }
        val exp = ui.optString("exp_date").toLongOrNull()
        val expText = exp?.let { SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date(it * 1000)) } ?: "unbegrenzt"
        val max = ui.optString("max_connections").ifBlank { "?" }
        return "Konto: $status · gültig bis $expText · max. Verbindungen: $max"
    }

    private fun categories(action: String): Map<String, String> {
        val a = array(api(action))
        val map = LinkedHashMap<String, String>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            map[o.optString("category_id")] = o.optString("category_name").ifBlank { "Ohne Kategorie" }
        }
        return map
    }

    fun load(playlistId: String, hideAdult: Boolean, liveExt: String): PlaylistContent {
        val info = accountInfo()
        val sections = ArrayList<Section>()

        runCatching {
            val cats = categories("get_live_categories")
            val a = array(api("get_live_streams"))
            val items = ArrayList<Channel>(a.length())
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val id = o.optInt("stream_id")
                items.add(
                    Channel(
                        name = o.optString("name").trim(),
                        url = "$base/live/$username/$password/$id.$liveExt",
                        logo = o.optString("stream_icon"),
                        group = cats[o.optString("category_id")] ?: "Sonstige",
                        kind = Kind.LIVE,
                        streamId = id,
                        playlistId = playlistId,
                    )
                )
            }
            section(Kind.LIVE, "Live-TV", cats.values, items, hideAdult)?.let { sections.add(it) }
        }

        runCatching {
            val cats = categories("get_vod_categories")
            val a = array(api("get_vod_streams"))
            val items = ArrayList<Channel>(a.length())
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val id = o.optInt("stream_id")
                val ext = o.optString("container_extension").ifBlank { "mp4" }
                items.add(
                    Channel(
                        name = o.optString("name").trim(),
                        url = "$base/movie/$username/$password/$id.$ext",
                        logo = o.optString("stream_icon"),
                        group = cats[o.optString("category_id")] ?: "Sonstige",
                        kind = Kind.MOVIE,
                        streamId = id,
                        playlistId = playlistId,
                    )
                )
            }
            section(Kind.MOVIE, "Filme", cats.values, items, hideAdult)?.let { sections.add(it) }
        }

        runCatching {
            val cats = categories("get_series_categories")
            val a = array(api("get_series"))
            val items = ArrayList<Channel>(a.length())
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                items.add(
                    Channel(
                        name = o.optString("name").trim(),
                        url = "",
                        logo = o.optString("cover"),
                        group = cats[o.optString("category_id")] ?: "Sonstige",
                        kind = Kind.SERIES,
                        streamId = o.optInt("series_id"),
                        playlistId = playlistId,
                        plot = o.optString("plot"),
                    )
                )
            }
            section(Kind.SERIES, "Serien", cats.values, items, hideAdult)?.let { sections.add(it) }
        }

        if (sections.isEmpty()) throw IOException("Keine Inhalte gefunden – der Server liefert keine Sender, Filme oder Serien.")
        return PlaylistContent(sections, info)
    }

    private fun section(kind: Kind, title: String, catOrder: Collection<String>, all: List<Channel>, hideAdult: Boolean): Section? {
        val items = if (hideAdult) all.filterNot { isAdult(it.group) || isAdult(it.name) } else all
        if (items.isEmpty()) return null
        val used = items.mapTo(HashSet()) { it.group }
        val cats = (catOrder.filter { it in used } + used.filterNot { it in catOrder }).distinct()
        return Section(kind, title, cats, items)
    }

    /** Lädt Staffeln und Folgen einer Serie. */
    fun episodes(seriesId: Int, playlistId: String): List<Pair<String, List<Channel>>> {
        val o = JSONObject(api("get_series_info", "&series_id=$seriesId"))
        val seasons = LinkedHashMap<String, MutableList<Channel>>()

        fun addEpisode(seasonKey: String, e: JSONObject) {
            val info = e.optJSONObject("info")
            val ext = e.optString("container_extension").ifBlank { "mp4" }
            val num = e.optString("episode_num")
            val title = e.optString("title").ifBlank { "Folge $num" }
            seasons.getOrPut(seasonKey) { ArrayList() }.add(
                Channel(
                    name = if (num.isNotBlank() && !title.contains(num)) "$num. $title" else title,
                    url = "$base/series/$username/$password/${e.optString("id")}.$ext",
                    logo = info?.optString("movie_image") ?: "",
                    group = "Staffel $seasonKey",
                    kind = Kind.MOVIE,
                    playlistId = playlistId,
                    plot = info?.optString("plot") ?: "",
                )
            )
        }

        when (val eps = o.opt("episodes")) {
            is JSONObject -> eps.keys().forEach { key ->
                val arr = eps.optJSONArray(key) ?: return@forEach
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { addEpisode(key, it) }
            }
            is JSONArray -> for (i in 0 until eps.length()) {
                when (val item = eps.opt(i)) {
                    is JSONArray -> for (j in 0 until item.length()) item.optJSONObject(j)?.let {
                        addEpisode(it.optString("season").ifBlank { "${i + 1}" }, it)
                    }
                    is JSONObject -> addEpisode(item.optString("season").ifBlank { "1" }, item)
                }
            }
        }

        return seasons.entries
            .sortedBy { it.key.toIntOrNull() ?: Int.MAX_VALUE }
            .map { "Staffel ${it.key}" to it.value.toList() }
    }

    /** Kurzes TV-Programm: was läuft jetzt und danach. */
    fun nowPlaying(streamId: Int): String? {
        val o = JSONObject(api("get_short_epg", "&stream_id=$streamId&limit=2"))
        val arr = o.optJSONArray("epg_listings") ?: return null
        val lines = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            val title = b64(e.optString("title"))
            val start = e.optString("start")
            val time = if (start.length >= 16) start.substring(11, 16) else ""
            lines.add((if (i == 0) "Jetzt: " else "Danach: ") + listOf(time, title).filter { it.isNotBlank() }.joinToString(" "))
        }
        return lines.joinToString("\n").ifBlank { null }
    }

    private fun b64(s: String): String = try {
        String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8)
    } catch (e: Exception) {
        s
    }
}
