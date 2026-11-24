package de.marween.tv

import org.json.JSONObject
import java.util.UUID

enum class PlaylistType { M3U, XTREAM }

/** Eine gespeicherte Liste: M3U (Link oder Datei) oder Xtream-Codes-Zugang. */
data class Playlist(
    val id: String,
    val name: String,
    val type: PlaylistType,
    val url: String = "",        // M3U-Link, content://-Datei oder Xtream-Server
    val username: String = "",
    val password: String = "",
    val isPublic: Boolean = false,
    val epgUrl: String = "",     // eigene EPG-Adresse (optional)
) {
    val subtitle: String
        get() = when {
            isPublic -> "Öffentliche Liste"
            type == PlaylistType.XTREAM -> "Xtream Codes · ${url.removePrefix("http://").removePrefix("https://")}"
            url.startsWith("content://") -> "M3U-Datei vom Gerät"
            else -> "M3U-Liste"
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("type", type.name); put("url", url)
        put("username", username); put("password", password); put("isPublic", isPublic)
        put("epgUrl", epgUrl)
    }

    companion object {
        fun fromJson(o: JSONObject) = Playlist(
            id = o.optString("id"),
            name = o.optString("name"),
            type = runCatching { PlaylistType.valueOf(o.optString("type")) }.getOrDefault(PlaylistType.M3U),
            url = o.optString("url"),
            username = o.optString("username"),
            password = o.optString("password"),
            isPublic = o.optBoolean("isPublic"),
            epgUrl = o.optString("epgUrl"),
        )
    }
}

enum class Kind { LIVE, MOVIE, SERIES }

/** Ein Sender, Film, eine Serie oder eine Folge. */
data class Channel(
    val name: String,
    val url: String,
    val logo: String = "",
    val group: String = "",
    val kind: Kind = Kind.LIVE,
    val streamId: Int = 0,        // Xtream: stream_id bzw. series_id
    val userAgent: String = "",
    val referrer: String = "",
    val playlistId: String = "",
    val plot: String = "",
    val epgId: String = "",       // tvg-id bzw. epg_channel_id
) {
    val key: String get() = if (url.isNotEmpty()) url else "$playlistId/series/$streamId"

    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name); put("url", url); put("logo", logo); put("group", group)
        put("kind", kind.name); put("streamId", streamId); put("userAgent", userAgent)
        put("referrer", referrer); put("playlistId", playlistId); put("plot", plot)
        put("epgId", epgId)
    }

    companion object {
        fun fromJson(o: JSONObject) = Channel(
            name = o.optString("name"),
            url = o.optString("url"),
            logo = o.optString("logo"),
            group = o.optString("group"),
            kind = runCatching { Kind.valueOf(o.optString("kind")) }.getOrDefault(Kind.LIVE),
            streamId = o.optInt("streamId"),
            userAgent = o.optString("userAgent"),
            referrer = o.optString("referrer"),
            playlistId = o.optString("playlistId"),
            plot = o.optString("plot"),
            epgId = o.optString("epgId"),
        )
    }
}

/** Ein Bereich einer Liste, z. B. Live-TV, Filme oder Serien. */
data class Section(
    val kind: Kind,
    val title: String,
    val categories: List<String>,
    val items: List<Channel>,
)

data class PlaylistContent(val sections: List<Section>, val info: String = "", val epgUrls: List<String> = emptyList())

fun newId(): String = UUID.randomUUID().toString()

private val adultRegex = Regex("(?i)(xxx|adult|porn|erotik|18\\+|\\+18|for adults)")
fun isAdult(text: String): Boolean = adultRegex.containsMatchIn(text)
