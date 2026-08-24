package com.lumenconnection.music.player

import com.lumenconnection.music.db.RepeatMode
import kotlin.random.Random

/**
 * Port da lógica de fila do `PlaybackEngine` do desktop (`src/player/playbackengine.cpp`).
 *
 * Fica deliberadamente **fora do ExoPlayer** e sem nenhuma dependência de
 * Android: opera só sobre ids de faixa, o que permite testar a semântica exata
 * do desktop e evita brigar com a playlist interna do Media3 — o controlador
 * alimenta o player um item por vez.
 *
 * Duas filas, como no desktop: a fila de contexto (playlist, curtidas ou
 * biblioteca) e a fila manual "a seguir", que tem precedência.
 */
class PlayerQueue(private val rng: Random = Random.Default) {

    var context: List<Long> = emptyList()
        private set
    var contextName: String = ""
        private set

    private val _userQueue = mutableListOf<Long>()
    val userQueue: List<Long> get() = _userQueue

    var currentId: Long? = null
        private set

    /**
     * Última posição conhecida no contexto. Preservada enquanto toca uma faixa
     * da fila manual que não pertence ao contexto — sem isso, `next()` acharia
     * que o contexto acabou em vez de apenas ter sido desviado.
     */
    var contextIndex: Int = -1
        private set

    var shuffle: Boolean = false
        private set
    var repeat: RepeatMode = RepeatMode.OFF

    private var shuffleBag: MutableList<Int> = mutableListOf()
    private var shufflePos: Int = 0

    // --- Transporte ---

    /** Começa um novo contexto de reprodução. */
    fun playTrack(trackId: Long, newContext: List<Long>, name: String) {
        context = newContext
        contextName = name
        contextIndex = -1
        if (shuffle) rebuildShuffleBag()
        setCurrent(trackId)
    }

    /** Toca uma faixa sem trocar o contexto (ex.: escolhida dentro da fila). */
    fun playKeepingContext(trackId: Long) = setCurrent(trackId)

    private fun setCurrent(trackId: Long) {
        currentId = trackId
        val idx = context.indexOf(trackId)
        if (idx >= 0) contextIndex = idx
    }

    /**
     * Próxima faixa. Devolve `null` quando não há para onde ir — fim do
     * contexto com repetição desligada, como o desktop, que simplesmente para
     * de avançar.
     */
    fun next(): Long? {
        if (currentId == null) return null

        // A fila manual tem precedência sobre o contexto.
        if (_userQueue.isNotEmpty()) {
            val t = _userQueue.removeAt(0)
            setCurrent(t)
            return t
        }

        if (context.isEmpty()) return null

        var idx = context.indexOf(currentId)
        // Tocando uma faixa da fila manual fora do contexto: retoma de onde parou.
        if (idx < 0) idx = contextIndex
        if (idx < 0) {
            val first = context.first()
            setCurrent(first)
            return first
        }

        val nextIdx = if (shuffle) {
            nextShuffleIndex()
        } else {
            val candidate = idx + 1
            when {
                candidate < context.size -> candidate
                repeat == RepeatMode.ALL -> 0
                else -> return null // fim do contexto
            }
        }

        if (nextIdx < 0 || nextIdx >= context.size) return null
        val id = context[nextIdx]
        setCurrent(id)
        return id
    }

    /**
     * Faixa anterior. O desktop dá a volta no contexto independentemente do modo
     * de repetição. A regra dos 3 s (voltar ao início em vez de trocar de faixa)
     * vive no controlador, que é quem conhece a posição do player.
     */
    fun prev(): Long? {
        if (currentId == null || context.isEmpty()) return null

        var idx = context.indexOf(currentId)
        if (idx < 0) idx = contextIndex
        if (idx < 0) return null

        val prevIdx = (idx - 1 + context.size) % context.size
        val id = context[prevIdx]
        setCurrent(id)
        return id
    }

    // --- Modos ---

    fun setShuffleMode(on: Boolean) {
        if (shuffle == on) return
        shuffle = on
        if (on) {
            rebuildShuffleBag()
        } else {
            shuffleBag = mutableListOf()
            shufflePos = 0
        }
    }

    /** Desligado → Todas → Uma → Desligado. */
    fun cycleRepeatMode() {
        repeat = when (repeat) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    /**
     * Baralho de aleatórios: uma permutação dos índices do contexto que avança
     * sem repetir até esgotar, e só então é reembaralhada — como um baralho de
     * cartas, não um sorteio a cada faixa.
     */
    private fun rebuildShuffleBag() {
        shuffleBag = MutableList(context.size) { it }

        // Fisher–Yates, igual ao desktop.
        for (i in shuffleBag.size - 1 downTo 1) {
            val j = rng.nextInt(i + 1)
            val tmp = shuffleBag[i]
            shuffleBag[i] = shuffleBag[j]
            shuffleBag[j] = tmp
        }
        shufflePos = 0

        // Se a faixa atual está no baralho, começa depois dela para o next()
        // não sortear ela mesma de imediato.
        val cur = context.indexOf(currentId)
        if (cur >= 0) {
            val at = shuffleBag.indexOf(cur)
            if (at >= 0) {
                val tmp = shuffleBag[0]
                shuffleBag[0] = shuffleBag[at]
                shuffleBag[at] = tmp
                shufflePos = 1 % maxOf(1, shuffleBag.size)
            }
        }
    }

    private fun nextShuffleIndex(): Int {
        if (context.isEmpty()) return -1
        if (shuffleBag.isEmpty() || shuffleBag.size != context.size || shufflePos >= shuffleBag.size) {
            rebuildShuffleBag()
        }
        if (shuffleBag.isEmpty()) return -1

        val idx = shuffleBag[shufflePos]
        shufflePos = (shufflePos + 1) % shuffleBag.size
        // Ao dar a volta, reembaralha para o próximo ciclo.
        if (shufflePos == 0) rebuildShuffleBag()
        return idx
    }

    // --- Fila manual ---

    /** Enfileira; se nada toca ainda, começa a tocar — comportamento do desktop. */
    fun enqueue(trackId: Long): Boolean {
        if (currentId == null) {
            setCurrent(trackId)
            return true // o chamador deve carregar e tocar
        }
        _userQueue.add(trackId)
        return false
    }

    fun removeFromQueue(index: Int) {
        if (index in _userQueue.indices) _userQueue.removeAt(index)
    }

    fun clearUserQueue() = _userQueue.clear()

    fun reorderUserQueue(from: Int, to: Int) {
        if (from !in _userQueue.indices || to !in _userQueue.indices || from == to) return
        val item = _userQueue.removeAt(from)
        _userQueue.add(to, item)
    }

    /** O que ainda vem do contexto depois da faixa atual (sem a fila manual). */
    fun upcomingContext(): List<Long> {
        if (context.isEmpty()) return emptyList()
        var idx = context.indexOf(currentId)
        if (idx < 0) idx = contextIndex
        if (idx < 0) return context
        return context.drop(idx + 1)
    }

    // --- Persistência ---

    /** Restaura o estado salvo em `playback_state`. */
    fun restore(
        currentTrackId: Long?,
        contextIds: List<Long>,
        userQueueIds: List<Long>,
        savedContextIndex: Int,
        name: String,
        shuffleOn: Boolean,
        repeatMode: RepeatMode,
    ) {
        context = contextIds
        contextName = name
        _userQueue.clear()
        _userQueue.addAll(userQueueIds)
        currentId = currentTrackId
        contextIndex = savedContextIndex
        repeat = repeatMode
        shuffle = shuffleOn
        if (shuffleOn) rebuildShuffleBag()
    }
}
