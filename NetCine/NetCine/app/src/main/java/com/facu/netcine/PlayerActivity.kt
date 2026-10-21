package com.facu.netcine

import android.app.Activity
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlin.concurrent.thread

/**
 * Reproductor a pantalla completa pensado para el control del Fire Stick:
 *  - OK: pausa / reproduce
 *  - Izquierda / Derecha: -10 s / +30 s
 *  - Botones ⏪ ⏯ ⏩ del control también funcionan
 *  - En series pasa solo al siguiente episodio
 *  - Recuerda por dónde ibas y retoma desde ahí
 */
@OptIn(UnstableApi::class)
class PlayerActivity : Activity() {

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_UA = "ua"
        private const val MAX_RETRIES = 3
    }

    private lateinit var playerView: PlayerView
    private lateinit var titleView: TextView
    private lateinit var resolving: View
    private lateinit var message: TextView

    private var player: ExoPlayer? = null
    private var items: List<Playable> = emptyList()
    private var retries = 0
    private val ui = Handler(Looper.getMainLooper())
    private val prefs: SharedPreferences by lazy { getSharedPreferences("resume", MODE_PRIVATE) }

    private val saver = object : Runnable {
        override fun run() {
            saveProgress()
            ui.postDelayed(this, 5_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        playerView = findViewById(R.id.player_view)
        titleView = findViewById(R.id.title)
        resolving = findViewById(R.id.resolving)
        message = findViewById(R.id.message)

        playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v ->
            titleView.visibility = v
        })

        val url = intent.getStringExtra(EXTRA_URL)
        val ua = intent.getStringExtra(EXTRA_UA).orEmpty()
        if (url == null) { finish(); return }

        thread(name = "resolve") {
            val result = runCatching { WatchResolver.resolve(url, ua) }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                result.onSuccess { start(it) }.onFailure { fail(R.string.cant_open) }
            }
        }
    }

    private fun start(r: Resolved) {
        items = r.items
        resolving.visibility = View.GONE

        val p = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .build()
        player = p
        playerView.player = p

        val media = items.map {
            MediaItem.Builder()
                .setUri(it.url)
                .setMediaId(it.key)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(it.title).build())
                .build()
        }
        p.setMediaItems(media, r.startIndex, resumePositionMs(items[r.startIndex]))
        p.addListener(listener)
        p.playWhenReady = true
        p.prepare()

        updateTitle()
        playerView.requestFocus()
        playerView.showController()
        ui.postDelayed(saver, 5_000)
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_READY -> retries = 0
                Player.STATE_ENDED -> {
                    current()?.let { prefs.edit().remove(it.key).apply() }
                    finish()
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateTitle()
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                // Terminó el episodio anterior: lo damos por visto.
                val prevIndex = (player?.currentMediaItemIndex ?: 0) - 1
                items.getOrNull(prevIndex)?.let { prefs.edit().remove(it.key).apply() }
            }
            val now = current() ?: return
            val resume = resumePositionMs(now)
            if (resume > 0) player?.seekTo(resume)
        }

        override fun onPlayerError(error: PlaybackException) {
            if (retries < MAX_RETRIES) {
                retries++
                ui.postDelayed({ player?.let { it.prepare(); it.play() } }, 1_500)
            } else {
                fail(R.string.cant_play)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val p = player
        if (p != null && event.action == KeyEvent.ACTION_DOWN && !playerView.isControllerFullyVisible) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { p.seekBack(); playerView.showController(); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { p.seekForward(); playerView.showController(); return true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (p.isPlaying) p.pause() else p.play()
                    playerView.showController()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun current(): Playable? = player?.currentMediaItemIndex?.let { items.getOrNull(it) }

    private fun updateTitle() {
        titleView.text = current()?.title.orEmpty()
    }

    private fun resumePositionMs(item: Playable): Long {
        val local = prefs.getLong(item.key, 0)
        return maxOf(local, item.startSec * 1000)
    }

    private fun saveProgress() {
        val p = player ?: return
        val item = current() ?: return
        val pos = p.currentPosition
        val dur = p.duration
        val edit = prefs.edit()
        when {
            dur > 0 && dur - pos < 90_000 -> edit.remove(item.key)   // casi terminado: no retomar
            pos > 30_000 -> edit.putLong(item.key, pos)
        }
        edit.apply()
    }

    private fun fail(msgRes: Int) {
        Toast.makeText(this, msgRes, Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onStop() {
        super.onStop()
        // Si se apaga la tele o salís con Home, guardamos y cerramos; al volver retoma.
        saveProgress()
        ui.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        if (!isFinishing) finish()
    }
}
