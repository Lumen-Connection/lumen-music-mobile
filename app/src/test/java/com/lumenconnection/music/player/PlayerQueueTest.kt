package com.lumenconnection.music.player

import com.lumenconnection.music.db.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Trava a semântica de fila do `PlaybackEngine` do desktop: precedência da fila
 * manual, baralho de aleatórios sem repetição, modos de repetição e a
 * preservação da posição no contexto durante um desvio pela fila manual.
 */
class PlayerQueueTest {

    private fun queueOf(vararg ids: Long) = ids.toList()

    @Test
    fun `next avanca no contexto em ordem`() {
        val q = PlayerQueue()
        q.playTrack(1, queueOf(1, 2, 3), "Rock")

        assertEquals(2L, q.next())
        assertEquals(3L, q.next())
    }

    @Test
    fun `next para no fim quando repeticao esta desligada`() {
        val q = PlayerQueue()
        q.playTrack(3, queueOf(1, 2, 3), "Rock")

        assertNull(q.next())
        // A faixa atual não muda quando não há para onde ir.
        assertEquals(3L, q.currentId)
    }

    @Test
    fun `repetir todas da a volta no fim do contexto`() {
        val q = PlayerQueue()
        q.playTrack(3, queueOf(1, 2, 3), "Rock")
        q.repeat = RepeatMode.ALL

        assertEquals(1L, q.next())
    }

    @Test
    fun `fila manual tem precedencia sobre o contexto`() {
        val q = PlayerQueue()
        q.playTrack(1, queueOf(1, 2, 3), "Rock")
        q.enqueue(99)

        assertEquals(99L, q.next())
        assertTrue(q.userQueue.isEmpty())
    }

    @Test
    fun `desvio pela fila manual preserva o lugar no contexto`() {
        val q = PlayerQueue()
        q.playTrack(2, queueOf(1, 2, 3), "Rock")
        q.enqueue(99) // faixa que não pertence ao contexto

        assertEquals(99L, q.next())
        // contextIndex é o índice no contexto, não o id: a faixa 2 está na posição 1.
        assertEquals(1, q.contextIndex)
        // Depois do desvio, o contexto retoma da faixa seguinte à 2.
        assertEquals(3L, q.next())
    }

    @Test
    fun `enfileirar sem nada tocando comeca a tocar`() {
        val q = PlayerQueue()
        val shouldPlayNow = q.enqueue(7)

        assertTrue(shouldPlayNow)
        assertEquals(7L, q.currentId)
        assertTrue(q.userQueue.isEmpty())
    }

    @Test
    fun `prev volta e da a volta no inicio`() {
        val q = PlayerQueue()
        q.playTrack(2, queueOf(1, 2, 3), "Rock")

        assertEquals(1L, q.prev())
        assertEquals(3L, q.prev()) // dá a volta
    }

    @Test
    fun `ciclo de repeticao segue desligado - todas - uma`() {
        val q = PlayerQueue()
        assertEquals(RepeatMode.OFF, q.repeat)
        q.cycleRepeatMode(); assertEquals(RepeatMode.ALL, q.repeat)
        q.cycleRepeatMode(); assertEquals(RepeatMode.ONE, q.repeat)
        q.cycleRepeatMode(); assertEquals(RepeatMode.OFF, q.repeat)
    }

    @Test
    fun `baralho aleatorio percorre todo o contexto sem repetir`() {
        val q = PlayerQueue(Random(42))
        val ids = (1L..8L).toList()
        q.playTrack(1, ids, "Biblioteca")
        q.setShuffleMode(true)

        // As 7 faixas restantes devem sair todas, sem repetição, antes de
        // qualquer reembaralhamento.
        val seen = mutableListOf<Long>()
        repeat(7) { seen.add(q.next()!!) }

        assertEquals(7, seen.size)
        assertEquals(7, seen.toSet().size)
        assertTrue(seen.none { it == 1L }) // a atual não volta no mesmo ciclo
        assertEquals(ids.toSet(), (seen + 1L).toSet())
    }

    @Test
    fun `aleatorio continua entregando faixas depois de esgotar o baralho`() {
        val q = PlayerQueue(Random(7))
        q.playTrack(1, queueOf(1, 2, 3), "Rock")
        q.setShuffleMode(true)

        // Muito além do tamanho do baralho: reembaralha e nunca devolve nulo.
        repeat(20) { assertTrue(q.next() != null) }
    }

    @Test
    fun `desligar aleatorio volta a ordem sequencial`() {
        val q = PlayerQueue(Random(1))
        q.playTrack(1, queueOf(1, 2, 3), "Rock")
        q.setShuffleMode(true)
        q.setShuffleMode(false)
        q.playKeepingContext(1)

        assertEquals(2L, q.next())
    }

    @Test
    fun `reordenar e limpar a fila manual`() {
        val q = PlayerQueue()
        q.playTrack(1, queueOf(1), "Rock")
        q.enqueue(10); q.enqueue(20); q.enqueue(30)

        q.reorderUserQueue(0, 2)
        assertEquals(listOf(20L, 30L, 10L), q.userQueue)

        q.removeFromQueue(1)
        assertEquals(listOf(20L, 10L), q.userQueue)

        q.clearUserQueue()
        assertTrue(q.userQueue.isEmpty())
    }

    @Test
    fun `upcomingContext lista o que ainda vem`() {
        val q = PlayerQueue()
        q.playTrack(2, queueOf(1, 2, 3, 4), "Rock")

        assertEquals(listOf(3L, 4L), q.upcomingContext())
    }

    @Test
    fun `restaurar sessao recompoe as duas filas`() {
        val q = PlayerQueue()
        q.restore(
            currentTrackId = 7,
            contextIds = listOf(5, 6, 7, 8),
            userQueueIds = listOf(42),
            savedContextIndex = 2,
            name = "Curtidas",
            shuffleOn = false,
            repeatMode = RepeatMode.ALL,
        )

        assertEquals(7L, q.currentId)
        assertEquals("Curtidas", q.contextName)
        assertEquals(listOf(42L), q.userQueue)
        assertEquals(RepeatMode.ALL, q.repeat)
        // A fila manual continua tendo precedência depois de restaurar.
        assertEquals(42L, q.next())
        assertEquals(8L, q.next())
    }

    @Test
    fun `contexto vazio nao quebra o avanco`() {
        val q = PlayerQueue()
        q.playTrack(1, emptyList(), "")
        assertNull(q.next())
        assertNull(q.prev())
    }
}
