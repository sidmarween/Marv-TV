package de.marween.tv

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale

sealed interface Loadable<out T> {
    data object Idle : Loadable<Nothing>
    data object Loading : Loadable<Nothing>
    data class Error(val message: String) : Loadable<Nothing>
    data class Ready<T>(val value: T) : Loadable<T>
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    init {
        Store.init(app)
    }

    val playlists = mutableStateListOf<Playlist>().apply { addAll(Store.playlists()) }
    val favorites = mutableStateListOf<Channel>().apply { addAll(Store.favorites()) }
    val recents = mutableStateListOf<Channel>().apply { addAll(Store.recents()) }

    // ---------- Einstellungen ----------
    var hideAdult by mutableStateOf(Store.hideAdult); private set
    var liveFormat by mutableStateOf(Store.liveFormat); private set
    var userAgent by mutableStateOf(Store.userAgent); private set

    fun updateHideAdult(v: Boolean) { Store.hideAdult = v; hideAdult = v; clearCache() }
    fun updateLiveFormat(v: String) { Store.liveFormat = v; liveFormat = v; clearCache() }
    fun updateUserAgent(v: String) { Store.userAgent = v; userAgent = Store.userAgent }

    // ---------- Listen ----------
    fun addPlaylist(p: Playlist) {
        playlists.add(p)
        Store.savePlaylists(playlists)
    }

    fun updatePlaylist(p: Playlist) {
        val i = playlists.indexOfFirst { it.id == p.id }
        if (i >= 0) playlists[i] = p else playlists.add(p)
        Store.savePlaylists(playlists)
        cache.remove(p.id)
        if (contentFor == p.id) contentFor = null
    }

    fun deletePlaylist(p: Playlist) {
        playlists.removeAll { it.id == p.id }
        Store.savePlaylists(playlists)
        cache.remove(p.id)
    }

    fun isSaved(url: String) = playlists.any { it.url == url }

    // ---------- Favoriten & Verlauf ----------
    fun refreshLists() {
        favorites.clear(); favorites.addAll(Store.favorites())
        recents.clear(); recents.addAll(Store.recents())
    }

    fun isFavorite(ch: Channel) = favorites.any { it.key == ch.key }

    fun toggleFavorite(ch: Channel) {
        val i = favorites.indexOfFirst { it.key == ch.key }
        if (i >= 0) favorites.removeAt(i) else favorites.add(ch)
        Store.saveFavorites(favorites)
    }

    fun clearRecents() {
        recents.clear()
        Store.saveRecents(emptyList())
    }

    // ---------- Inhalt einer Liste ----------
    var content by mutableStateOf<Loadable<PlaylistContent>>(Loadable.Idle); private set
    var contentFor by mutableStateOf<String?>(null); private set
    private val cache = HashMap<String, PlaylistContent>()

    fun open(p: Playlist, force: Boolean = false) {
        if (!force && contentFor == p.id && content is Loadable.Ready<*>) return
        contentFor = p.id
        if (!force) cache[p.id]?.let { content = Loadable.Ready(it); return }
        content = Loadable.Loading
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { loadContent(p) } }
            if (contentFor != p.id) return@launch
            result
                .onSuccess { cache[p.id] = it; content = Loadable.Ready(it) }
                .onFailure { content = Loadable.Error(friendly(it)) }
        }
    }

    private fun loadContent(p: Playlist): PlaylistContent = when (p.type) {
        PlaylistType.XTREAM -> XtreamClient(p.url, p.username, p.password).load(p.id, Store.hideAdult, Store.liveFormat)
        PlaylistType.M3U -> {
            val input = if (p.url.startsWith("content://")) {
                getApplication<Application>().contentResolver.openInputStream(Uri.parse(p.url))
                    ?: throw IOException("Die Datei kann nicht geöffnet werden.")
            } else {
                Http.openStream(p.url)
            }
            val all = input.bufferedReader().use { M3uParser.parse(it, p.id) }
            val items = if (Store.hideAdult) all.filterNot { isAdult(it.group) || isAdult(it.name) } else all
            if (items.isEmpty()) throw IOException("Die Liste enthält keine abspielbaren Einträge.")
            val sections = listOfNotNull(
                section(Kind.LIVE, "Live-TV", items.filter { it.kind == Kind.LIVE }),
                section(Kind.MOVIE, "Filme & Serien", items.filter { it.kind == Kind.MOVIE }),
            )
            PlaylistContent(sections, "${items.size} Einträge")
        }
    }

    private fun section(kind: Kind, title: String, items: List<Channel>): Section? =
        if (items.isEmpty()) null else Section(kind, title, items.map { it.group }.distinct(), items)

    // ---------- Serien ----------
    var episodes by mutableStateOf<Loadable<List<Pair<String, List<Channel>>>>>(Loadable.Idle); private set
    private var episodesFor: String? = null

    fun loadEpisodes(p: Playlist, series: Channel) {
        val key = series.key
        if (episodesFor == key && episodes is Loadable.Ready<*>) return
        episodesFor = key
        episodes = Loadable.Loading
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { XtreamClient(p.url, p.username, p.password).episodes(series.streamId, p.id) }
            }
            if (episodesFor != key) return@launch
            result
                .onSuccess { episodes = Loadable.Ready(it) }
                .onFailure { episodes = Loadable.Error(friendly(it)) }
        }
    }

    // ---------- Öffentliche Listen durchsuchen ----------
    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf<Loadable<List<Channel>>>(Loadable.Idle); private set
    private var publicIndex: List<Channel>? = null

    fun searchPublic() {
        val query = searchQuery.trim()
        if (query.length < 2) {
            searchResults = Loadable.Error("Bitte mindestens 2 Zeichen eingeben.")
            return
        }
        searchResults = Loadable.Loading
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val index = publicIndex ?: loadPublicIndex().also { publicIndex = it }
                    val n = normalize(query)
                    index.filter { normalize(it.name).contains(n) || normalize(it.group).contains(n) }
                        .distinctBy { it.url }
                        .take(500)
                }
            }
            result
                .onSuccess { searchResults = Loadable.Ready(it) }
                .onFailure { searchResults = Loadable.Error(friendly(it)) }
        }
    }

    fun resetSearch() {
        searchQuery = ""
        searchResults = Loadable.Idle
    }

    private fun loadPublicIndex(): List<Channel> {
        val dir = getApplication<Application>().cacheDir
        val file = File(dir, "public_index.m3u")
        val fresh = file.exists() && file.length() > 0 &&
            System.currentTimeMillis() - file.lastModified() < 24L * 60 * 60 * 1000
        if (!fresh) {
            val tmp = File(dir, "public_index.tmp")
            try {
                Http.openStream(PublicSources.SEARCH_INDEX).use { input -> tmp.outputStream().use { input.copyTo(it) } }
                file.delete()
                tmp.renameTo(file)
            } catch (e: Exception) {
                tmp.delete()
                if (!file.exists()) throw e // alte Kopie verwenden, falls vorhanden
            }
        }
        val all = file.bufferedReader().use { M3uParser.parse(it, "public") }
        return if (Store.hideAdult) all.filterNot { isAdult(it.group) || isAdult(it.name) } else all
    }

    private fun normalize(s: String) = s.lowercase(Locale.GERMAN).filter { it.isLetterOrDigit() }

    fun clearCache() {
        cache.clear()
        publicIndex = null
        File(getApplication<Application>().cacheDir, "public_index.m3u").delete()
        contentFor = null
        content = Loadable.Idle
        episodesFor = null
        episodes = Loadable.Idle
    }

    companion object {
        fun friendly(t: Throwable): String = when (t) {
            is UnknownHostException -> "Server nicht gefunden – Adresse und Internetverbindung prüfen."
            is SocketTimeoutException -> "Zeitüberschreitung – der Server antwortet nicht."
            is JSONException -> "Ungültige Antwort vom Server. Stimmt die Adresse?"
            is SecurityException -> "Kein Zugriff auf die Datei. Bitte die Liste neu hinzufügen."
            is OutOfMemoryError -> "Die Liste ist zu groß für dieses Gerät."
            is java.net.MalformedURLException -> "Ungültige Adresse."
            is java.net.ConnectException -> "Keine Verbindung zum Server möglich."
            else -> t.message ?: "Unbekannter Fehler"
        }
    }
}
