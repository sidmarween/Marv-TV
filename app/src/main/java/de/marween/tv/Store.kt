package de.marween.tv

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Einfache lokale Speicherung (Listen, Favoriten, Verlauf, Einstellungen). */
object Store {
    const val DEFAULT_UA = "Mozilla/5.0 (Linux; Android) MarvTV/1.0"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences("marvtv", Context.MODE_PRIVATE)
        }
    }

    fun playlists(): List<Playlist> = readArray("playlists").map { Playlist.fromJson(it) }
    fun savePlaylists(list: List<Playlist>) = writeArray("playlists", list.map { it.toJson() })

    fun favorites(): List<Channel> = readArray("favorites").map { Channel.fromJson(it) }
    fun saveFavorites(list: List<Channel>) = writeArray("favorites", list.map { it.toJson() })

    fun recents(): List<Channel> = readArray("recents").map { Channel.fromJson(it) }
    fun saveRecents(list: List<Channel>) = writeArray("recents", list.map { it.toJson() })

    fun addRecent(ch: Channel) {
        val list = recents().filterNot { it.key == ch.key }.toMutableList()
        list.add(0, ch)
        saveRecents(list.take(30))
    }

    var userAgent: String
        get() = prefs.getString("ua", null)?.takeIf { it.isNotBlank() } ?: DEFAULT_UA
        set(v) = prefs.edit().putString("ua", v).apply()

    /** "ts" oder "m3u8" für Xtream-Live-Sender */
    var liveFormat: String
        get() = prefs.getString("liveFormat", "ts") ?: "ts"
        set(v) = prefs.edit().putString("liveFormat", v).apply()

    var hideAdult: Boolean
        get() = prefs.getBoolean("hideAdult", true)
        set(v) = prefs.edit().putBoolean("hideAdult", v).apply()

    private fun readArray(key: String): List<JSONObject> {
        val s = prefs.getString(key, null) ?: return emptyList()
        return try {
            val a = JSONArray(s)
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeArray(key: String, list: List<JSONObject>) {
        val a = JSONArray()
        list.forEach { a.put(it) }
        prefs.edit().putString(key, a.toString()).apply()
    }
}
