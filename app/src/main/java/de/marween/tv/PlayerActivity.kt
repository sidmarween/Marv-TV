package de.marween.tv

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var overlay: TextView
    private lateinit var httpFactory: DefaultHttpDataSource.Factory
    private val handler = Handler(Looper.getMainLooper())
    private val hideOverlay = Runnable { overlay.visibility = View.GONE }
    private var items: List<Channel> = emptyList()
    private var epgJob: Job? = null
    private var xtream: XtreamClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        items = PlayQueue.items
        if (items.isEmpty()) {
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        playerView = PlayerView(this).apply {
            keepScreenOn = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            controllerShowTimeoutMs = 3500
        }
        overlay = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            setBackgroundColor(0xCC000000.toInt())
            setPadding(36, 22, 36, 22)
            visibility = View.GONE
        }
        root.addView(playerView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(
            overlay,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START)
                .apply { setMargins(48, 48, 48, 48) }
        )
        setContentView(root)
        hideSystemUi()

        httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(Store.userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, httpFactory)))
            .build()
        player = exo
        playerView.player = exo

        exo.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                onItemChanged()
            }

            override fun onPlayerError(error: PlaybackException) {
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    exo.seekToDefaultPosition()
                    exo.prepare()
                    return
                }
                val hint = if (isLive() && items.size > 1) "\n▲ / ▼ für einen anderen Sender" else ""
                showOverlay("Wiedergabe nicht möglich\n${errorText(error)}$hint", 10_000)
            }
        })

        val start = PlayQueue.index.coerceIn(0, items.lastIndex)
        xtream = PlayQueue.xtreamFor(items[start])
        applyHeaders(items[start])
        exo.setMediaItems(items.map { buildMediaItem(it) }, start, 0L)
        exo.prepare()
        exo.playWhenReady = true
        onItemChanged()
    }

    private fun buildMediaItem(ch: Channel): MediaItem {
        val b = MediaItem.Builder()
            .setUri(ch.url)
            .setMediaId(ch.key)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(ch.name).build())
        if (ch.url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) b.setMimeType(MimeTypes.APPLICATION_M3U8)
        if (ch.url.substringBefore('?').endsWith(".mpd", ignoreCase = true)) b.setMimeType(MimeTypes.APPLICATION_MPD)
        return b.build()
    }

    private fun applyHeaders(ch: Channel) {
        val headers = HashMap<String, String>()
        if (ch.referrer.isNotBlank()) headers["Referer"] = ch.referrer
        if (ch.userAgent.isNotBlank()) headers["User-Agent"] = ch.userAgent
        httpFactory.setDefaultRequestProperties(headers)
    }

    private fun currentIndex(): Int = player?.currentMediaItemIndex ?: 0

    private fun isLive(): Boolean = items.getOrNull(currentIndex())?.kind == Kind.LIVE

    private fun onItemChanged() {
        val i = currentIndex()
        val ch = items.getOrNull(i) ?: return
        applyHeaders(ch)
        Store.addRecent(ch)
        val title = if (ch.kind == Kind.LIVE && items.size > 1) "${i + 1}   ${ch.name}" else ch.name
        showOverlay(title, 4000)

        epgJob?.cancel()
        val client = xtream
        if (client != null && ch.kind == Kind.LIVE && ch.streamId > 0) {
            epgJob = lifecycleScope.launch {
                val epg = withContext(Dispatchers.IO) { runCatching { client.nowPlaying(ch.streamId) }.getOrNull() }
                if (!epg.isNullOrBlank() && currentIndex() == i) showOverlay("$title\n$epg", 7000)
            }
        }
    }

    private fun zap(delta: Int) {
        val p = player ?: return
        val n = items.size
        if (n <= 1) return
        val next = ((p.currentMediaItemIndex + delta) % n + n) % n
        applyHeaders(items[next])
        p.seekToDefaultPosition(next)
        p.prepare()
        p.play()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && isLive()) {
            val controlsHidden = !playerView.isControllerFullyVisible
            when (event.keyCode) {
                KeyEvent.KEYCODE_CHANNEL_UP -> { zap(1); return true }
                KeyEvent.KEYCODE_CHANNEL_DOWN -> { zap(-1); return true }
                KeyEvent.KEYCODE_DPAD_UP -> if (controlsHidden) { zap(-1); return true }
                KeyEvent.KEYCODE_DPAD_DOWN -> if (controlsHidden) { zap(1); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun showOverlay(text: String, durationMs: Long) {
        overlay.text = text
        overlay.visibility = View.VISIBLE
        handler.removeCallbacks(hideOverlay)
        handler.postDelayed(hideOverlay, durationMs)
    }

    private fun errorText(e: PlaybackException): String = when (e.errorCode) {
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Der Server lehnt die Anfrage ab (Sender offline, gesperrt oder Abo abgelaufen)."
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Keine Verbindung zum Server."
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Zeitüberschreitung – der Server antwortet nicht."
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "Stream nicht gefunden."
        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> "Unverschlüsselte Verbindung nicht erlaubt."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> "Das Stream-Format wird nicht unterstützt. Tipp: In den Einstellungen das Live-Format wechseln."
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> "Video- oder Tonformat wird von diesem Gerät nicht unterstützt."
        PlaybackException.ERROR_CODE_DRM_UNSPECIFIED,
        PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED,
        PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED -> "Der Sender ist verschlüsselt (DRM)."
        else -> "Fehler: ${e.errorCodeName}"
    }

    private fun hideSystemUi() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onStart() {
        super.onStart()
        player?.playWhenReady = true
    }

    override fun onStop() {
        super.onStop()
        player?.playWhenReady = false
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        super.onDestroy()
    }
}
