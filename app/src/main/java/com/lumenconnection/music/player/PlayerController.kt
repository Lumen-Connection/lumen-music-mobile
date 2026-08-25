package com.lumenconnection.music.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.lumenconnection.music.Graph
import com.lumenconnection.music.db.PlaybackStateEntity
import com.lumenconnection.music.db.RepeatMode
import com.lumenconnection.music.db.TrackEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ponte entre a [PlayerQueue] (semântica portada do desktop), o ExoPlayer e o
 * banco.
 *
 * Como no lumen-stream-mobile, não há ViewModel: a UI observa os StateFlows
 * daqui. O ExoPlayer recebe **um item por vez** — a fila de verdade é a
 * [PlayerQueue], o que reusa literalmente as regras do `PlaybackEngine`.
 */
object PlayerController {

    /** Voltar antes disso troca de faixa; depois, reinicia a atual (regra do desktop). */
    private const val PREV_RESTART_THRESHOLD_MS = 3_000L

    /** O desktop salva a cada 30 s para sobreviver a logoff/crash do Windows. */
    private const val AUTOSAVE_INTERVAL_MS = 30_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val queue = PlayerQueue()

    lateinit var exoPlayer: ExoPlayer
        private set

    @Volatile
    private var initialized = false
    private var appContext: Context? = null
    private var stateDirty = false
    private var pendingSeekMs = 0L

    private val _currentTrack = MutableStateFlow<TrackEntity?>(null)
    val currentTrack: StateFlow<TrackEntity?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _userQueue = MutableStateFlow<List<TrackEntity>>(emptyList())
    val userQueue: StateFlow<List<TrackEntity>> = _userQueue.asStateFlow()

    private val _upcoming = MutableStateFlow<List<TrackEntity>>(emptyList())
    val upcoming: StateFlow<List<TrackEntity>> = _upcoming.asStateFlow()

    private val _contextName = MutableStateFlow("")
    val contextName: StateFlow<String> = _contextName.asStateFlow()

    /** Faixa cujo arquivo sumiu — a UI mostra um aviso, como o toast do desktop. */
    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            appContext = context.applicationContext
            exoPlayer = ExoPlayer.Builder(context.applicationContext).build().apply {
                addListener(PlayerListener)
            }
            initialized = true
        }
        scope.launch { restoreSession() }
        startTicker()
        startAutosave()
    }

    // --- Transporte ---

    fun playTrack(track: TrackEntity, context: List<Long>, contextName: String) {
        if (queue.currentId == track.id) {
            togglePlay()
            return
        }
        queue.playTrack(track.id, context, contextName)
        _contextName.value = contextName
        markDirty()
        loadAndPlay(track)
    }

    /**
     * Retoma uma faixa numa posição específica — usado pelo "continuar de onde
     * parou no PC", que recebe do sync a posição em que o desktop estava.
     */
    fun playTrackAt(track: TrackEntity, context: List<Long>, contextName: String, positionMs: Long) {
        queue.playTrack(track.id, context, contextName)
        _contextName.value = contextName
        markDirty()
        loadAndPlay(track, seekToMs = positionMs)
    }

    fun togglePlay() {
        if (!initialized) return
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
        markDirty()
    }

    fun next() {
        val id = queue.next() ?: return
        loadById(id)
    }

    fun prev() {
        if (!initialized) return
        // Depois de alguns segundos, "anterior" reinicia a faixa atual.
        if (exoPlayer.currentPosition > PREV_RESTART_THRESHOLD_MS) {
            exoPlayer.seekTo(0)
            return
        }
        val id = queue.prev() ?: return
        loadById(id)
    }

    fun seek(ms: Long) {
        if (!initialized) return
        exoPlayer.seekTo(ms)
        markDirty()
    }

    // --- Modos ---

    fun setShuffle(on: Boolean) {
        queue.setShuffleMode(on)
        _shuffle.value = queue.shuffle
        markDirty()
    }

    fun cycleRepeatMode() {
        queue.cycleRepeatMode()
        _repeatMode.value = queue.repeat
        markDirty()
    }

    // --- Fila manual ---

    fun enqueue(track: TrackEntity) {
        val shouldPlayNow = queue.enqueue(track.id)
        if (shouldPlayNow) loadAndPlay(track) else refreshQueues()
        markDirty()
    }

    fun removeFromQueue(index: Int) {
        queue.removeFromQueue(index)
        refreshQueues()
        markDirty()
    }

    fun clearUserQueue() {
        queue.clearUserQueue()
        refreshQueues()
        markDirty()
    }

    fun reorderUserQueue(from: Int, to: Int) {
        queue.reorderUserQueue(from, to)
        refreshQueues()
        markDirty()
    }

    fun consumeError() {
        _playbackError.value = null
    }

    // --- Carga ---

    private fun loadById(id: Long) = scope.launch {
        val track = withContext(Dispatchers.IO) { Graph.db.trackDao().byId(id) } ?: return@launch
        loadAndPlay(track)
    }

    private fun loadAndPlay(track: TrackEntity, seekToMs: Long = 0, autoPlay: Boolean = true) {
        val path = track.filePath
        if (path.isNullOrBlank()) {
            // Faixa sincronizada cujo áudio ainda não desceu, ou arquivo perdido.
            _playbackError.value = track.title
            scope.launch(Dispatchers.IO) { Graph.db.trackDao().setMissing(track.id, true) }
            return
        }

        _currentTrack.value = track
        pendingSeekMs = seekToMs

        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .apply {
                GradientArtwork.forColors(track.coverColor1, track.coverColor2)?.let {
                    setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                }
            }
            .build()

        exoPlayer.setMediaItem(
            MediaItem.Builder()
                .setUri(Uri.parse(path))
                .setMediaId(track.id.toString())
                .setMediaMetadata(metadata)
                .build()
        )
        exoPlayer.prepare()
        if (autoPlay) exoPlayer.play()

        ensureServiceRunning()
        refreshQueues()
        markDirty()

        if (autoPlay) {
            scope.launch(Dispatchers.IO) {
                Graph.db.trackDao().registerPlay(track.id, System.currentTimeMillis())
            }
        }
    }

    /**
     * O serviço é quem mantém a reprodução viva em segundo plano; iniciá-lo aqui
     * garante que ele exista mesmo quando o player é acionado direto pela UI.
     */
    private fun ensureServiceRunning() {
        val ctx = appContext ?: return
        runCatching { ctx.startService(Intent(ctx, PlaybackService::class.java)) }
    }

    private fun refreshQueues() = scope.launch {
        val manualIds = queue.userQueue
        val upcomingIds = queue.upcomingContext().take(50)
        val (manual, upcoming) = withContext(Dispatchers.IO) {
            val dao = Graph.db.trackDao()
            // `byIds` não preserva a ordem do IN, então reordenamos pelo id.
            val manualMap = dao.byIds(manualIds).associateBy { it.id }
            val upcomingMap = dao.byIds(upcomingIds).associateBy { it.id }
            manualIds.mapNotNull { manualMap[it] } to upcomingIds.mapNotNull { upcomingMap[it] }
        }
        _userQueue.value = manual
        _upcoming.value = upcoming
    }

    // --- Persistência ---

    private fun markDirty() {
        stateDirty = true
    }

    private fun startTicker() = scope.launch {
        while (true) {
            if (initialized && exoPlayer.isPlaying) {
                _positionMs.value = exoPlayer.currentPosition
                _durationMs.value = exoPlayer.duration.coerceAtLeast(0)
            }
            delay(500)
        }
    }

    private fun startAutosave() = scope.launch {
        while (true) {
            delay(AUTOSAVE_INTERVAL_MS)
            if (stateDirty) persistState()
        }
    }

    /** Salva o estado; chamado no autosave, em eventos e ao destruir o serviço. */
    fun persistState() {
        if (!initialized) return
        val trackId = queue.currentId
        val position = runCatching { exoPlayer.currentPosition }.getOrDefault(0L)
        val volume = runCatching { exoPlayer.volume }.getOrDefault(1f)
        stateDirty = false
        scope.launch(Dispatchers.IO) {
            Graph.db.playbackStateDao().save(
                PlaybackStateEntity(
                    id = 1,
                    currentTrackId = trackId,
                    positionMs = position,
                    volume = volume,
                    muted = volume == 0f,
                    shuffle = queue.shuffle,
                    repeatMode = queue.repeat,
                    contextIds = queue.context,
                    userQueueIds = queue.userQueue,
                    contextIndex = queue.contextIndex,
                    contextName = queue.contextName,
                )
            )
        }
    }

    /**
     * Restaura a sessão anterior sem começar a tocar: o desktop reabre exatamente
     * onde parou, e o `seek` só é aplicado quando a mídia está pronta — daí o
     * [pendingSeekMs] em vez de um seek imediato, que seria ignorado.
     */
    private suspend fun restoreSession() {
        val saved = withContext(Dispatchers.IO) { Graph.db.playbackStateDao().get() } ?: return
        queue.restore(
            currentTrackId = saved.currentTrackId,
            contextIds = saved.contextIds,
            userQueueIds = saved.userQueueIds,
            savedContextIndex = saved.contextIndex,
            name = saved.contextName,
            shuffleOn = saved.shuffle,
            repeatMode = saved.repeatMode,
        )
        _shuffle.value = saved.shuffle
        _repeatMode.value = saved.repeatMode
        _contextName.value = saved.contextName
        exoPlayer.volume = if (saved.muted) 0f else saved.volume

        val trackId = saved.currentTrackId ?: return
        val track = withContext(Dispatchers.IO) { Graph.db.trackDao().byId(trackId) } ?: return
        // autoPlay = false: reabrir o app não deve começar a tocar sozinho.
        loadAndPlay(track, seekToMs = saved.positionMs, autoPlay = false)
        _positionMs.value = saved.positionMs
    }

    private object PlayerListener : Player.Listener {

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            markDirty()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                // O seek pendente da restauração só vale depois de a mídia
                // estar pronta; antes disso o ExoPlayer o descartaria.
                if (pendingSeekMs > 0) {
                    exoPlayer.seekTo(pendingSeekMs)
                    pendingSeekMs = 0
                }
                _durationMs.value = exoPlayer.duration.coerceAtLeast(0)
            }

            if (playbackState == Player.STATE_ENDED) {
                if (queue.repeat == RepeatMode.ONE) {
                    exoPlayer.seekTo(0)
                    exoPlayer.play()
                } else {
                    // Todas / Desligado: next() já respeita a volta e a parada.
                    next()
                }
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _playbackError.value = _currentTrack.value?.title
            val id = _currentTrack.value?.id ?: return
            scope.launch(Dispatchers.IO) { Graph.db.trackDao().setMissing(id, true) }
        }
    }
}
