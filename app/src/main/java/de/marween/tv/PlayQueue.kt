package de.marween.tv

import android.content.Context
import android.content.Intent

/** Übergibt die Wiedergabeliste an den Player. */
object PlayQueue {
    var items: List<Channel> = emptyList()
    var index: Int = 0

    /**
     * Startet die Wiedergabe.
     * Live-Sender: alle Live-Sender der Liste werden übergeben (Umschalten mit ▲/▼).
     * Filme: nur der gewählte Film. Folgen (autoNext): die ganze Staffel.
     */
    fun start(context: Context, list: List<Channel>, position: Int, autoNext: Boolean = false) {
        val ch = list.getOrNull(position) ?: return
        when {
            ch.kind == Kind.LIVE -> {
                val live = list.filter { it.kind == Kind.LIVE }
                val i = live.indexOf(ch).coerceAtLeast(0)
                val from = (i - 500).coerceAtLeast(0)
                val to = (from + 1000).coerceAtMost(live.size)
                items = live.subList(from, to).toList()
                index = i - from
            }
            autoNext -> {
                val playable = list.filter { it.url.isNotEmpty() }
                items = playable
                index = playable.indexOf(ch).coerceAtLeast(0)
            }
            else -> {
                items = listOf(ch)
                index = 0
            }
        }
        context.startActivity(Intent(context, PlayerActivity::class.java))
    }

    /** Xtream-Zugang zur Liste eines Senders (für das TV-Programm). */
    fun xtreamFor(ch: Channel): XtreamClient? {
        val p = Store.playlists().find { it.id == ch.playlistId && it.type == PlaylistType.XTREAM } ?: return null
        return XtreamClient(p.url, p.username, p.password)
    }
}
